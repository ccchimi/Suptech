FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S postventa && adduser -S postventa -G postventa

WORKDIR /app
COPY --from=build --chown=postventa:postventa /build/target/postventa-service-*.jar app.jar

USER postventa
EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
