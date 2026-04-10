# ── Stage 1: Build + keystore materialisation ────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache Maven deps
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
COPY certs ./certs

# Convert PEMs into:
#   server.p12     — server identity (used by both Tomcat HTTPS and Moquette)
#   truststore.jks — root CA in a JKS (used by both Tomcat HTTPS and Moquette)
# Both files have password "changeit".  We use a hand-built SslContext in
# MqttBrokerBean so the same two files work for both Tomcat and Moquette.
RUN apt-get update && apt-get install -y --no-install-recommends openssl \
 && rm -rf /var/lib/apt/lists/* \
 && openssl pkcs12 -export \
        -in  certs/server.crt \
        -inkey certs/server.key \
        -out certs/server.p12 \
        -name medserver \
        -CAfile certs/ca.crt \
        -passout pass:changeit \
 && rm -f certs/truststore.jks \
 && keytool -import -trustcacerts \
        -file certs/ca.crt \
        -alias rootcamed \
        -keystore certs/truststore.jks \
        -storepass changeit \
        -noprompt \
 && echo "─── server.p12 ───" \
 && keytool -list -keystore certs/server.p12 -storetype PKCS12 -storepass changeit \
 && echo "─── truststore.jks ───" \
 && keytool -list -keystore certs/truststore.jks -storepass changeit \
 && ls -la certs/

RUN mvn -B -q -DskipTests package

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/pas-connector.jar app.jar
COPY --from=build /build/certs /app/certs

RUN mkdir -p /app/uploads

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Djavax.net.debug=ssl:handshake:verbose"

EXPOSE 8443 8444 8883 1883

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fs http://localhost:8444/api/pas/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
