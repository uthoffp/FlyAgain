FROM gradle:8.13-jdk21 AS build
WORKDIR /app
COPY server/ server/
COPY shared/ shared/
WORKDIR /app/server
RUN gradle :database-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/database-service/build/install/database-service/ .
EXPOSE 9090
CMD ["bin/database-service"]
