FROM gradle:8.12-jdk21-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/kkalscan-api-0.1.0-SNAPSHOT-all.jar app.jar
EXPOSE 8080
VOLUME /data
ENV DATABASE_URL=jdbc:sqlite:/data/kkalscan.db
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
