FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN ./mvnw -B clean verify

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
RUN addgroup -S urleads && adduser -S -G urleads urleads
COPY --from=builder --chown=urleads:urleads /app/target/urleads-backend-*.jar /app/app.jar

EXPOSE 8080
USER urleads
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
