FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY build/libs/kkalscan-api-*-all.jar app.jar
EXPOSE 8080
VOLUME /data
ENV DATABASE_URL=jdbc:sqlite:/data/kkalscan.db
ENV LOG_DIR=/data/logs
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
