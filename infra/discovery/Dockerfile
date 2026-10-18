FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw && ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
RUN addgroup -S urleads && adduser -S -G urleads urleads
COPY --from=builder --chown=urleads:urleads /app/target/discovery-service-*.jar /app/app.jar

EXPOSE 8761
USER urleads
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
