# syntax=docker/dockerfile:1

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
COPY tunnelvision-core/ tunnelvision-core/
COPY tunnelvision-cache-caffeine/ tunnelvision-cache-caffeine/
COPY tunnelvision-cache-redis/ tunnelvision-cache-redis/
COPY tunnelvision-cache-hazelcast/ tunnelvision-cache-hazelcast/
COPY tunnelvision-server/ tunnelvision-server/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar tunnelvision-server/target/tunnelvision-server.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 tunnelvision \
    && useradd --system --uid 10001 --gid tunnelvision --home-dir /app tunnelvision
WORKDIR /app

COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

RUN mkdir -p data config && chown -R tunnelvision:tunnelvision data config
USER 10001

ENV TUNNELVISION_SNAPSHOT_DIRECTORY=/app/data/sources
VOLUME ["/app/data"]
EXPOSE 8080 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
