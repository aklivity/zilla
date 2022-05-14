FROM eclipse-temurin:17-alpine

COPY target/cqrs-processor.jar /

RUN apk upgrade --no-cache && \
    apk add --no-cache libgcc libstdc++ ncurses-libs

ENTRYPOINT ["java", "-jar", "/sqrs-processor.jar"]
