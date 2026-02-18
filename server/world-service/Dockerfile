FROM gradle:8.13-jdk21 AS build
WORKDIR /app
COPY server/ server/
COPY shared/ shared/
WORKDIR /app/server
RUN gradle :world-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/world-service/build/install/world-service/ .
EXPOSE 7780 7781/udp
CMD ["bin/world-service"]
