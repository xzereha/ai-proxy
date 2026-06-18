# Build the application
FROM gradle:9.5.1-jdk26-noble AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Start the runtime
FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
