FROM gradle:8.13-jdk21 AS build
WORKDIR /app
COPY server/ server/
COPY shared/ shared/
WORKDIR /app/server
RUN gradle :login-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/login-service/build/install/login-service/ .
EXPOSE 7777
CMD ["bin/login-service"]
