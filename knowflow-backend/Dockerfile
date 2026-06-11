FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
RUN groupadd --system knowflow && useradd --system --gid knowflow --home-dir /app --shell /usr/sbin/nologin knowflow \
    && chown -R knowflow:knowflow /app
USER knowflow
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
