# API de Soporte y Postventa Suptech (`postventa-service`)

Microservicio de atención al cliente: **reembolsos, reclamos, devoluciones de productos
defectuosos y cancelaciones de pedidos**.

| | |
|---|---|
| Java | 21 (LTS) — records, sealed types, pattern matching, virtual threads |
| Spring Boot | 3.5.16 |
| Persistencia | Spring Data JPA (Hibernate 6) + PostgreSQL + Flyway |
| Integración | `RestClient` (síncrono, sobre el `HttpClient` del JDK) |
| Resiliencia | Resilience4j (retry + circuit breaker) + saga persistida |
| Observabilidad | Actuator + Spring Boot Admin Client + Micrometer Tracing (OTLP) + Prometheus |

---

## 1. Arquitectura

Hexagonal (puertos y adaptadores). La regla de dependencia apunta siempre hacia adentro:
**el dominio no importa nada de Spring, de JPA ni de HTTP**.

```
com.suptech.postventa
├── domain/                          ← núcleo. Java puro, testeable sin contexto Spring
│   ├── model/                       Caso, EstadoCaso, LineaAfectada, ResultadoIntegracion (sealed)
│   │   └── saga/                    SagaCancelacion, EstadoSaga, PasoSaga
│   ├── exception/                   errores de negocio (DominioException)
│   └── port/
│       ├── in/                      casos de uso: RegistrarCaso, SolicitarCancelacion, ConsultarCasos
│       └── out/                     SPI: CasoRepositoryPort, SagaRepositoryPort, PedidosPort, InventarioPort
│
├── application/service/             ← orquestación de casos de uso
│   ├── CasoService                  reembolsos / reclamos / devoluciones
│   ├── CancelacionSagaService       orquestador de la saga de cancelación
│   ├── SagaEstadoWriter             escrituras transaccionales cortas
│   └── SagaReconciliadorService     reanuda sagas a medias (@Scheduled, hilos virtuales)
│
└── infrastructure/
    ├── adapter/in/rest/             controladores + DTOs + ProblemDetail (RFC 9457)
    ├── adapter/out/persistence/     entidades JPA, repositorios Spring Data, mappers
    ├── adapter/out/client/          RestClient hacia Pedidos e Inventario + Resilience4j
    └── config/                      RestClientConfig, IntegracionProperties, AplicacionConfig
```

Consecuencia práctica: `CancelacionSagaServiceTest` prueba **toda** la lógica de la
cancelación —incluido el fallo de Inventario— sin Spring, sin base de datos y sin red.

---

## 2. El flujo crítico: cancelación de un pedido

```
Cliente ──POST /api/v1/cancelaciones──▶ postventa-service
                                            │
                              (0) ¿ya hay una saga viva para el pedido? → idempotencia
                              (1) GET  pedido            ──▶ Pedidos     (pre-condición: ¿es cancelable?)
                              (2) persiste Caso + Saga           [tx corta, sin red dentro]
                              (3) POST cancelación       ──▶ Pedidos     PASO 1
                              (4) POST liberación stock  ──▶ Inventario  PASO 2
                                            │
                                     Saga COMPLETADA → 200 OK
```

### Estrategia de fallos

Cada llamada devuelve un `ResultadoIntegracion` (tipo *sealed*, resuelto con
`switch` con patrones), que distingue lo único que importa aquí: **fallo transitorio**
(5xx, timeout, circuito abierto → reintentable) frente a **fallo permanente** (4xx de
negocio → reintentar es inútil).

| Momento del fallo | Efectos aplicados | Qué hace el sistema | Respuesta HTTP |
|---|---|---|---|
| Consultando el pedido | ninguno | Falla rápido, no se crea nada | `503` |
| Pedido no cancelable | ninguno | Rechaza la solicitud | `409` |
| **Paso 1** (Pedidos) transitorio | ninguno | Reintentos con backoff; al agotarse, saga `FALLIDA` | `202` → `409` |
| **Paso 1** (Pedidos) permanente | ninguno | Saga `FALLIDA`, caso `RECHAZADO` | `409` |
| **Paso 2** (Inventario) caído | **el pedido ya está cancelado** | Ver abajo | `202` |

### ¿Y si Pedidos responde bien pero Inventario está caído?

**No se revierte la cancelación del pedido.** Es la decisión de diseño central:

1. Al cliente ya se le confirmó la cancelación; "des-cancelar" es un efecto visible e
   inaceptable — la compensación sería peor que el fallo.
2. Compensar exige que Pedidos responda… justo cuando estamos en un escenario de
   indisponibilidad. Una compensación que también puede fallar no es una garantía.

En su lugar se aplica **compensación hacia adelante** (*forward recovery*), que es válida
porque liberar stock es **idempotente y reintentable**:

- La saga se persiste en `PENDIENTE_REINTENTO` con `paso_pendiente = LIBERAR_STOCK`,
  `intentos` y `proximo_intento_en` (backoff exponencial: 15s → 30s → 1m → … → 30m tope).
- `SagaReconciliadorService` la reanuda **desde el paso exacto** en que murió, un hilo
  virtual por saga. Funciona igual si el pod se cayó entre el paso 1 y el 2: el estado
  vive en la tabla `saga_cancelacion`, no en memoria.
- Agotados los 6 intentos → `REQUIERE_INTERVENCION`: el caso se marca para backoffice y
  se emite log `ERROR` + métrica `postventa.saga.finalizadas{estado=REQUIERE_INTERVENCION}`
  sobre la que se alerta. **El stock nunca queda retenido en silencio.**
- La reversión real (`PedidosPort#revertirCancelacion`) existe en el puerto, pero solo se
  invoca por decisión explícita de negocio, no de forma automática.

La compensación clásica solo aplica a fallos **anteriores** al paso 1: ahí no hay ningún
efecto externo que deshacer y la saga muere limpia (`FALLIDA`).

### Idempotencia (en las tres capas)

| Capa | Mecanismo |
|---|---|
| Entrada | Una saga activa por pedido; el índice único parcial `uq_saga_activa_por_pedido` lo garantiza en la BD, no solo en código |
| Salida | Cabecera `Idempotency-Key: {sagaId}:{paso}` — determinista, estable entre reintentos, instancias y días |
| Remoto | Un `409` de Pedidos/Inventario se interpreta como **éxito**: el efecto deseado ya está aplicado |

### Transacciones

**Nunca se mantiene una transacción de BD abierta durante una llamada de red**
(`SagaEstadoWriter`, `REQUIRES_NEW`). Con hilos virtuales los hilos dejan de ser el
recurso escaso, pero el pool de Hikari sigue siéndolo: una conexión bloqueada esperando a
un servicio caído convierte un fallo ajeno en una caída propia.

---

## 3. Virtual Threads (Project Loom)

Se activan por configuración, no por código:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Con esa única propiedad, Tomcat, el `applicationTaskExecutor` y el `taskScheduler` dejan
de usar el pool de plataforma. Este servicio pasa la vida bloqueado esperando a Pedidos, a
Inventario y a la base de datos: concurrencia limitada por I/O, exactamente el perfil para
el que Loom existe. Se obtiene el rendimiento del modelo reactivo **sin** el coste
cognitivo de `WebClient`/`Mono`/`Flux` — de ahí que la integración use `RestClient`
bloqueante y no reactivo.

Dos advertencias que el código ya contempla:

- El cuello de botella se desplaza al **pool JDBC** (`maximum-pool-size: 20`, explícito).
- No se usan bloques `synchronized` alrededor de I/O (causaban *pinning* del carrier
  thread antes de JDK 24).

---

## 4. Endpoints

```bash
# Abrir un reembolso
curl -X POST http://localhost:8081/api/v1/casos \
  -H 'Content-Type: application/json' \
  -d '{"pedidoId":"PED-1001","clienteId":"CLI-77","tipo":"REEMBOLSO",
       "motivo":"Producto defectuoso","montoSolicitado":149.90,
       "lineas":[{"sku":"SKU-1","cantidad":1,"detalle":"llegó roto"}]}'
```

```bash
# Cancelar un pedido (dispara la saga)
curl -X POST http://localhost:8081/api/v1/cancelaciones \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f1c9a20-8f2a-4a1e-9a55-9f0c6f2b1d31' \
  -d '{"pedidoId":"PED-1001","clienteId":"CLI-77","motivo":"El cliente se arrepintió"}'
```

```bash
# Consultar el desenlace de un caso
curl http://localhost:8081/api/v1/casos/{casoId}
```

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/casos` | Abre un reembolso, reclamo o devolución → `201` |
| `GET` | `/api/v1/casos/{casoId}` | Consulta un caso |
| `GET` | `/api/v1/casos?clienteId=` | Casos de un cliente |
| `POST` | `/api/v1/cancelaciones` | Cancela un pedido → `200` / `202` / `409` |
| `GET` | `/` | Redirige a Swagger UI |

`200` = saga completa · `202` = pedido cancelado, algún paso en reintento (seguir en
`seguimiento`) · `409` = rechazada, **sin ningún cambio aplicado**.

Errores en formato **ProblemDetail (RFC 9457)**. Documentación viva en
`http://localhost:8081/swagger-ui.html` (redirige a `/swagger-ui/index.html`).

---

## 5. Ejecución local

Requisitos: **JDK 21+** y Docker. Maven **no** hace falta instalarlo: el proyecto incluye
el *Maven Wrapper* (`mvnw` / `mvnw.cmd`), que descarga la versión correcta la primera vez.

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

En PowerShell (Windows), el wrapper es `.\mvnw.cmd` y los argumentos `-D` deben ir
entrecomillados o PowerShell se come el punto:

```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

> **Puerto de PostgreSQL:** el contenedor se publica en el **5433** del host, no en el
> 5432. Es habitual tener una instalación nativa de PostgreSQL ocupando el 5432; en ese
> caso Docker publica el mapeo igualmente, pero las conexiones acaban en el servidor
> equivocado y el síntoma es un `FATAL: password authentication failed for user
> "postventa"` que despista bastante. Para usar otro puerto, exporta `DB_PORT` (lo leen
> tanto `docker-compose.yml` como `application.yml`).

| Qué quiero ver | Dónde |
|---|---|
| Probar la API a mano | http://localhost:8081/swagger-ui.html |
| Estado del servicio y de los circuitos | http://localhost:8081/actuator/health |
| Sagas pendientes de reintento | `docker exec -it postventa-postgres psql -U postventa -d postventa` |
| Traza completa de una cancelación | http://localhost:16686 |
| Métricas | http://localhost:8081/actuator/prometheus |
| Tareas programadas (reconciliador) | http://localhost:8081/actuator/scheduledtasks |

El perfil `local` desactiva el registro en Spring Boot Admin. Para probarlo, levanta un
servidor SBA — son 20 líneas en un módulo aparte:

```java
@EnableAdminServer
@SpringBootApplication
public class AdminServerApplication {
    public static void main(String[] args) { SpringApplication.run(AdminServerApplication.class, args); }
}
```

…con `de.codecentric:spring-boot-admin-starter-server`, `server.port=8090`, y arrancar
este servicio sin el perfil `local`.

> **Al cambiar un `.sql` ya aplicado**, Flyway falla el arranque con
> `Migrations have failed validation`: el checksum ya no coincide. Una migración aplicada no
> se edita — se añade otra. En desarrollo, si hace falta partir de cero:
> `docker compose down -v && docker compose up -d`.

### Tests

```bash
mvn test
```

`CancelacionSagaServiceTest` cubre camino feliz, Inventario caído, recuperación posterior,
agotamiento de reintentos, rechazo permanente e idempotencia — todo con dobles de los
puertos. `ContextoAplicacionTest` levanta el contexto completo contra un PostgreSQL real
con Testcontainers y **se salta automáticamente si no hay Docker**.

---

## 6. Decisión: `RestClient` síncrono vs. arquitectura orientada a eventos

Recomendación: **híbrido, y este código ya está preparado para adoptarlo sin tocar el
dominio.**

| Paso | Naturaleza | Transporte adecuado |
|---|---|---|
| Consultar pedido | Necesito la respuesta **para decidir** | Síncrono (`RestClient`) — obligatorio |
| Paso 1 · cancelar pedido | El cliente espera una confirmación real | Síncrono (`RestClient`) — recomendado |
| Paso 2 · liberar stock | Nadie espera; es idempotente y compensable | **Evento asíncrono** — mejor opción |

**Por qué el paso 2 encaja mejor como evento:** la disponibilidad de Inventario deja de
condicionar la respuesta al cliente, el broker asume la persistencia y el reintento (que
hoy resolvemos con la tabla de saga + reconciliador) y añadir consumidores nuevos
—analítica, notificaciones— no toca este servicio.

**Por qué no se implementó así de entrada:** introduce un broker como dependencia
operativa (Kafka + esquemas + DLQ + monitorización), y el paso 2 debe publicarse con
**outbox transaccional** — publicar dentro de la transacción de BD reintroduce el problema
de la doble escritura que la saga viene a resolver. No es complejidad gratuita salvo que
ya exista Kafka en la plataforma.

**Migración cuando llegue el broker** (el hexágono la hace barata):

1. Nueva implementación de `InventarioPort` que escriba un evento
   `StockLiberacionSolicitada` en una tabla `outbox`, **en la misma transacción** que la saga.
2. Un publicador (`@Scheduled` o Debezium/CDC) mueve la outbox al tópico de Kafka.
3. `CancelacionSagaService`, `SagaCancelacion` y los controladores **no cambian ni una
   línea**: el orquestador sigue viendo `ResultadoIntegracion.Exitoso` cuando el evento
   queda encolado de forma duradera.
4. El paso 2 pasa a confirmarse por un evento de vuelta (`StockLiberado`), que cierra la
   saga — mismo estado, misma tabla.

Ese es el objetivo de la arquitectura hexagonal: cambiar de HTTP a Kafka es cambiar un
adaptador, no rediseñar el servicio.

---

## 7. Decisiones no obvias del código

El código no lleva comentarios: estas son las trampas que justifican por qué está escrito así.

**El `fallbackMethod` cuelga de `@Retry`, no de `@CircuitBreaker`.** Resilience4j aplica
`@Retry` como la anotación más externa. Si el fallback estuviera en el circuit breaker,
devolvería un valor "exitoso" hacia afuera y `@Retry` nunca llegaría a reintentar nada.

**`ManejadorGlobalErrores` extiende `ResponseEntityExceptionHandler`** en vez de activar
`spring.mvc.problemdetails.enabled`. Esa propiedad registra el advice de Spring con
`@Order(0)`, por delante del nuestro, y se queda con los errores de validación antes de que
lleguen: la API respondía un genérico `"Invalid request content."` sin detalle por campo.

**`SagaEstadoWriter` es un bean aparte, no métodos del orquestador.** Si vivieran en
`CancelacionSagaService`, la auto-invocación saltaría el proxy de Spring y `@Transactional`
sencillamente no se aplicaría. Sus transacciones son cortas y `REQUIRES_NEW` a propósito:
nunca hay una transacción abierta mientras se hace una llamada de red.

**La clave de idempotencia hacia abajo es `{sagaId}:{paso}`, no la del cliente.** Es
determinista: el reconciliador reproduce exactamente la misma clave días después y desde
otra instancia, así que un reintento nunca duplica el efecto remoto.

**El monto se normaliza a escala 4 en el agregado.** Sin eso, un caso recién creado se
serializa `149.90` y el mismo caso releído de la BD `149.9000`: la API sería inconsistente
consigo misma.

**El índice único de sagas activas es parcial.** La unicidad "una saga viva por pedido" la
garantiza PostgreSQL, no una comprobación previa en Java que dos peticiones simultáneas
podrían saltarse entre el `SELECT` y el `INSERT`.

**`Clock` se inyecta como bean.** Permite probar el backoff exponencial con un reloj fijo en
vez de con `Thread.sleep`, que es lo que hace que la suite corra en milisegundos.

**Los estados cancelables se validan localmente** aunque la autoridad sea el microservicio de
Pedidos. Es para fallar rápido con un mensaje útil; si Pedidos discrepa, responde `409` y el
adaptador lo trata como éxito idempotente.

---

## 8. Pendientes conocidos antes de producción

- **ShedLock** en `SagaReconciliadorService`: con varias réplicas, todas procesarían el
  mismo lote (el bloqueo optimista evita la corrupción, no el trabajo duplicado).
- **Seguridad**: los endpoints están abiertos; falta Spring Security (OAuth2 Resource
  Server) y proteger `/actuator`.
- **Alertas**: la métrica `postventa.saga.finalizadas{estado=REQUIERE_INTERVENCION}` debe
  tener alerta; es la señal de que hay stock retenido esperando a un humano.
- **Contratos**: tests de contrato (Spring Cloud Contract / Pact) contra Pedidos e
  Inventario. Hoy los DTOs de sus APIs son una suposición documentada.