# Suptech

Plataforma de comercio electrónico construida como microservicios independientes.

## Servicios

| Servicio | Descripción | Estado |
|---|---|---|
| [postventa-service](postventa-service/) | Soporte y postventa: reembolsos, reclamos, devoluciones y cancelación de pedidos | Funcional |
| pedidos-service | Gestión del ciclo de vida del pedido | Pendiente |
| inventario-service | Stock y reservas | Pendiente |

## postventa-service

Microservicio de atención al cliente en **Java 21 + Spring Boot 3.5**, con arquitectura
hexagonal y virtual threads. Su pieza central es una **saga orquestada** que cancela un
pedido coordinando los servicios de Pedidos e Inventario, con recuperación automática
cuando alguno de ellos no responde.

La documentación completa —arquitectura, estrategia de fallos, decisiones de diseño y cómo
ejecutarlo— está en [postventa-service/README.md](postventa-service/README.md).

```bash
cd postventa-service && docker compose up -d && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Requisitos: JDK 21 o superior y Docker. Maven no hace falta instalarlo (el proyecto incluye
el wrapper).
