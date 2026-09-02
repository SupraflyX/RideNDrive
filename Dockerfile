# stage 1: build. needs maven and a full jdk, neither of which ships in the
# final image
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# pom first, on its own layer: dependency resolution only depends on the pom,
# so editing source code doesn't invalidate the downloaded dependency cache
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# stage 2: run. only a jre and the finished jar
FROM eclipse-temurin:17-jre
WORKDIR /app

# don't run as root. data/ is created up front and handed to the same user,
# because the local profile writes an H2 file database into it and a root-owned
# working directory would make that fail with AccessDeniedException
RUN useradd --uid 1001 --no-create-home spring \
    && mkdir -p /app/data \
    && chown -R spring:spring /app

# chown on the copy, so the jar belongs to the user that runs it
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
