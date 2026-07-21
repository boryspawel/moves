FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system motion \
    && useradd --system --gid motion --home-dir /app --no-create-home motion \
    && install -d -o motion -g motion /var/lib/moves/exercise-import
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/application.jar
USER motion
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
