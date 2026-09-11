FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY shared/ shared/
# Every module pom must be present for the Maven reactor, but only this
# service's sources are copied — edits to sibling services don't bust the cache
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/identity-service/pom.xml services/identity-service/
COPY services/species-service/pom.xml services/species-service/
COPY services/observation-service/pom.xml services/observation-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/species-service/src services/species-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn clean package -DskipTests -pl services/species-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/services/species-service/target/*.jar app.jar
# Runs as root by default otherwise — a container breakout or dependency RCE would have full
# root inside the container for no benefit, since the JVM doesn't need it to bind :8080
# (BACKLOG.md item G, WEAKNESS_AUDIT.md §1.4).
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
