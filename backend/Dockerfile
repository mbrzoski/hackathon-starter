# Backend image for the demo (deploy/docker-compose.yml). Build the jar first: ./mvnw -DskipTests package
# (deploy/run-demo.sh does it). Secrets come from the environment at run time, never from the image (rule 8).
FROM eclipse-temurin:21-jre

RUN useradd --system --home /app aniol && mkdir -p /app /data && chown aniol /data
WORKDIR /app
COPY target/backend-*.jar /app/app.jar

USER aniol
# SQLite file and evaluation labels live in /data (a volume), not in the image.
ENV APP_DB_FILE=/data/aniol.db \
    APP_LABELS_FILE=/data/labels.jsonl
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
