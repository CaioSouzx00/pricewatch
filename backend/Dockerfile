FROM gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/src
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /home/gradle/src/build/install/backend/ /app/
EXPOSE 8080
CMD ["/app/bin/backend"]
