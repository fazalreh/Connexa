# Two stages so the runtime image carries a JRE and one jar, not a JDK and a Gradle cache.
# The build stage is discarded, which takes the deployed image from roughly 800 MB to 250 MB
# and removes the compiler and build tooling from anything an attacker could reach.

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build

# Wrapper and build scripts first, on their own layer. They change far less often than source,
# so an edit to a Java file reuses the cached dependency download instead of repeating it.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
# Tests are deliberately not run here. They are the CI job's responsibility, and running them
# in the image build would need database credentials present at build time.
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# An unprivileged user: nothing in this service needs root, and a container process that keeps
# root gives a code-execution bug a much larger blast radius.
RUN groupadd --system connexa && useradd --system --gid connexa --home /app connexa

COPY --from=build /build/build/libs/*.jar /app/connexa-api.jar
RUN chown -R connexa:connexa /app
USER connexa

# The platform supplies PORT and routes to it; application.yml reads the same variable.
ENV PORT=8080
EXPOSE 8080

# Container memory is not the host's memory. Without this the JVM sizes its heap against the
# machine and gets killed by the container limit under load.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# exec form so the JVM is PID 1 and receives SIGTERM directly, which lets the configured
# graceful shutdown drain in-flight requests instead of being killed mid-response.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/connexa-api.jar"]
