FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S joblens && adduser -S joblens -G joblens
COPY target/joblens.jar app.jar
USER joblens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
