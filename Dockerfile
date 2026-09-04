# syntax=docker/dockerfile:1

# Build the Spring Boot application with the project's Maven Wrapper.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
	&& ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

# Run the packaged application in a smaller JRE-only image.
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN mkdir -p /app/output /app/logs \
	&& chown -R 10001:10001 /app

COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
