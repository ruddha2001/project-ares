FROM gradle:8.14.0-jdk21-alpine AS builder
WORKDIR /build
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S aresgroup && adduser -S aresuser -G aresgroup

COPY --from=builder /build/build/libs/ares-backend-*.jar ./ares-backend.jar
RUN chown -R aresuser:aresgroup /app
USER aresuser

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError", "-jar", "ares-backend.jar"]