FROM gradle:8.12-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre
ARG NODE_VERSION=24.11.1
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl xz-utils ca-certificates git \
    python3 python3-pip python3-venv build-essential \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
    && tar -xJf "node-v${NODE_VERSION}-linux-x64.tar.xz" -C /usr/local --strip-components=1 \
    && rm "node-v${NODE_VERSION}-linux-x64.tar.xz" \
    && node --version \
    && npm --version \
    && git --version \
    && python3 --version \
    && pip3 --version

RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --no-cache-dir "graphifyy[all]" \
    && graphify install

WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-Dspring.threads.virtual.enabled=true", "-jar", "app.jar"]