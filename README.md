# Suptech

[![CI](https://github.com/ccchimi/Suptech/actions/workflows/ci.yml/badge.svg)](https://github.com/ccchimi/Suptech/actions/workflows/ci.yml)

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

Incluye seguridad con Spring Security, bloqueo distribuido del planificador con ShedLock,
imagen Docker multi-etapa y 34 tests ejecutados en CI.

La documentación completa —arquitectura, estrategia de fallos, seguridad, decisiones de
diseño y cómo ejecutarlo— está en
[postventa-service/README.md](postventa-service/README.md).

```bash
cd postventa-service && docker compose up -d && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

En PowerShell el wrapper es `.\mvnw.cmd` y los argumentos `-D` **deben ir entrecomillados**,
o PowerShell parte el argumento en el primer punto y Maven falla con
`Unknown lifecycle phase ".run.profiles=local"`:

```bash
cd postventa-service
```
```bash
docker compose up -d
```
```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

O todo en contenedores, sin necesidad de JDK local:

```bash
cd postventa-service && docker compose --profile app up -d --build
```

Requisitos: Docker y, para el primer modo, JDK 21 o superior. Maven no hace falta
instalarlo, el proyecto incluye el wrapper.

## Integración continua

`.github/workflows/ci.yml` compila, ejecuta la suite completa (incluidos los tests con
Testcontainers) y construye la imagen Docker en cada push y pull request a `main`.
