# One container for Render's free tier: the Ktor API on localhost:8080 and the Next.js web app on $PORT,
# which forwards /api/* to the API. Build context: the repository root.

FROM gradle:8.14.3-jdk21 AS api
WORKDIR /src
COPY backend/ .
# One JVM with a fixed heap (Kotlin compiles inside Gradle rather than in a second daemon), so the build fits
# Render's build machine; plain output so a failure shows its reason in the deploy log.
RUN gradle buildFatJar --no-daemon --console=plain --warning-mode=none \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Pkotlin.compiler.execution.strategy=in-process

# Node 22 (20 is end-of-life and unpatched). The check fails the build if a cached image is older than the
# October 2026 security release (TLS hostname checks, Brotli, eMudhra certificates).
FROM node:22-slim AS web
RUN node -e 'const [a,b,c]=process.versions.node.split(".").map(Number); if (a!==22||b<23||(b===23&&c<4)) { console.error("Node "+process.version+" is older than 22.23.4"); process.exit(1) }'
WORKDIR /web
COPY web/package.json ./
RUN npm install --no-audit --no-fund
COPY web/ .
# The /api rewrite is fixed at build time; the API runs next to it.
ENV API_URL=http://127.0.0.1:8080 NEXT_TELEMETRY_DISABLED=1
RUN npm run build && npm prune --omit=dev

FROM node:22-slim
RUN node -e 'const [a,b,c]=process.versions.node.split(".").map(Number); if (a!==22||b<23||(b===23&&c<4)) { console.error("Node "+process.version+" is older than 22.23.4"); process.exit(1) }'
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk PATH="/opt/java/openjdk/bin:${PATH}"
WORKDIR /app
COPY --from=api /src/build/libs/*-all.jar api.jar
COPY --from=web /web /app/web
COPY deploy/start.sh /app/start.sh
RUN chmod +x /app/start.sh && mkdir -p /tmp/uploads
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 API_PORT=8080 STORAGE_DIR=/tmp/uploads \
    JAVA_OPTS="-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1" \
    NODE_OPTIONS="--max-old-space-size=160"
CMD ["/app/start.sh"]
