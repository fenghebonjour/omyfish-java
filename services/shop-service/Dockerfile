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
COPY services/shop-service/pom.xml services/shop-service/
COPY services/shop-service/src services/shop-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn clean package -DskipTests -pl services/shop-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/services/shop-service/target/*.jar app.jar
# Runs as root by default otherwise — a container breakout or dependency RCE would have full
# root inside the container for no benefit, since the JVM doesn't need it to bind :8080
# (BACKLOG.md item G, WEAKNESS_AUDIT.md §1.4).
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
USER app
EXPOSE 8080
# -Djboss.threads.eqe.statistics.active-count=true: Undertow's XNIO worker pool (the
# EnhancedQueueExecutor backing it) doesn't track its active/busy thread count by default — it's
# an opt-in because the atomic increment/decrement on every task has a real cost at high QPS.
# Worth paying here since config/UndertowMetricsBinder.java exports exactly that gauge, and
# without this flag it reads a permanent -1. JFR_ENABLED=true (set via docker-compose, off by
# default) turns on a continuous JDK Flight Recorder session for load-test runs — free, bundled
# in the JDK, sufficient for finding CPU hot paths/leaks under load without a commercial profiler.
ENTRYPOINT ["sh", "-c", "if [ \"$JFR_ENABLED\" = \"true\" ]; then exec java -Djboss.threads.eqe.statistics.active-count=true -XX:StartFlightRecording=filename=/tmp/shop-service.jfr,dumponexit=true,maxage=1h -jar app.jar; else exec java -Djboss.threads.eqe.statistics.active-count=true -jar app.jar; fi"]
