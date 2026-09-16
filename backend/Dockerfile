# Build stage. The dependency layer is separated from the source layer so that editing a Java file
# does not re-download the world; only a change to the build files invalidates it.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY backend/build.gradle.kts backend/
RUN chmod +x gradlew && ./gradlew --no-daemon :backend:dependencies --configuration runtimeClasspath > /dev/null

COPY backend/src backend/src
# Tests run in CI, on their own, where a failure is readable. Repeating them here would only make
# the image build slower and the failure harder to find.
RUN ./gradlew --no-daemon :backend:bootJar -x test

# Spring Boot's layered jar: dependencies change rarely, application classes change every commit,
# so unpacking them into separate image layers keeps rebuilds small.
RUN java -Djarmode=layertools -jar backend/build/libs/*.jar extract --destination /layers

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is here only so the healthcheck below is a real request rather than a liveness theatre.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home dragon
USER dragon

COPY --from=build --chown=dragon:dragon /layers/dependencies/ ./
COPY --from=build --chown=dragon:dragon /layers/spring-boot-loader/ ./
COPY --from=build --chown=dragon:dragon /layers/snapshot-dependencies/ ./
COPY --from=build --chown=dragon:dragon /layers/application/ ./

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx, so the container respects whatever memory it is given.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
