FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY --chmod=755 docker-entrypoint.sh /app/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
