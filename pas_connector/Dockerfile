# ── Stage 1: Build + keystore materialisation ────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache Maven deps
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
COPY certs ./certs

# ── Convert PEMs into a PKCS12 keystore + JKS truststore with a
#    known password (changeit).  This avoids shipping binary
#    keystores whose passwords have to be guessed. ────────────────
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

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

EXPOSE 8443 8444 8883 1883

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fs http://localhost:8444/api/pas/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
