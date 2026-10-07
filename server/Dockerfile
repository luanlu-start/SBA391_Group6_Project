# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package

FROM eclipse-temurin:25-jre-noble AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/sba391-server-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
