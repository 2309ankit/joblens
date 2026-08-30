FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S joblens && adduser -S joblens -G joblens
COPY target/joblens-0.0.1-SNAPSHOT.jar app.jar
USER joblens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
