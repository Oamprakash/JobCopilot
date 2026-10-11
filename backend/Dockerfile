FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*-all.jar app.jar
ENV PORT=8080 STORAGE_DIR=/app/data/uploads
RUN mkdir -p /app/data/uploads && chown -R 1000 /app/data
EXPOSE 8080
USER 1000
CMD ["java", "-jar", "app.jar"]
