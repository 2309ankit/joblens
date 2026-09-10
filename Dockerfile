FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY src src
COPY frontend frontend
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S joblens && adduser -S joblens -G joblens
COPY --from=build --chown=joblens:joblens /workspace/target/joblens.jar app.jar
USER joblens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
