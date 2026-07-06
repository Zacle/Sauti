FROM gradle:8.10.2-jdk17 AS backend-build
WORKDIR /workspace
COPY settings.gradle gradlew.bat ./
COPY gradle ./gradle
COPY backend ./backend
COPY docs/agent-templates.md ./docs/agent-templates.md
RUN gradle :backend:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_OPTS=""
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=backend-build /workspace/backend/build/libs/*.jar /app/sauti-backend.jar
EXPOSE 8080 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/sauti-backend.jar"]
