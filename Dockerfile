# Stage 1 - Build
FROM gradle:9.5.1-jdk26-noble AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle gradle
RUN gradle dependencies --no-daemon || true
COPY src src
RUN gradle bootJar --no-daemon

# Stage 2 - Runtime
FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
EXPOSE 8080
USER app
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
