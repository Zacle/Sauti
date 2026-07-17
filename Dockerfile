# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk17 AS backend-build
WORKDIR /workspace
COPY --chown=gradle:gradle settings.gradle gradlew.bat ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle backend ./backend
COPY --chown=gradle:gradle docs/agent-templates.md ./docs/agent-templates.md
RUN --mount=type=cache,id=sauti-gradle-home,target=/home/gradle/.gradle,sharing=locked,uid=1000,gid=1000 \
    gradle :backend:bootJar \
      --no-daemon \
      --project-cache-dir /tmp/sauti-gradle-project-cache \
    && rm -rf /tmp/sauti-gradle-project-cache

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_OPTS=""
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=backend-build /workspace/backend/build/libs/*.jar /app/sauti-backend.jar
EXPOSE 8080 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/sauti-backend.jar"]
