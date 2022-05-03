FROM eclipse-temurin:17-alpine

COPY target/http-kafka-cqrs.jar /

RUN apk upgrade --no-cache && \
    apk add --no-cache libgcc libstdc++ ncurses-libs

ENTRYPOINT ["java", "-jar", "/http-kafka-cqrs.jar"]
