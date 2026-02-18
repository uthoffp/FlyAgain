FROM gradle:8.13-jdk21 AS build
WORKDIR /app
COPY server/ server/
COPY shared/ shared/
WORKDIR /app/server
RUN gradle :account-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/account-service/build/install/account-service/ .
EXPOSE 7779
CMD ["bin/account-service"]
