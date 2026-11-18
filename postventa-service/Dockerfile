FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

# La imagen base se publica cada tanto y entre medio Alpine saca parches (p. ej. brotli-libs,
# CVE-2026-111497). Se aplican al construir para que el escaneo de Trivy no dependa de cuando
# se actualiza la base.
RUN apk upgrade --no-cache \
    && addgroup -S postventa && adduser -S postventa -G postventa

WORKDIR /app
COPY --from=build --chown=postventa:postventa /build/target/postventa-service-*.jar app.jar

USER postventa
EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
