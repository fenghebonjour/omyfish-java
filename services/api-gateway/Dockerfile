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
COPY services/api-gateway/src services/api-gateway/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn clean package -DskipTests -pl services/api-gateway -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/services/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
