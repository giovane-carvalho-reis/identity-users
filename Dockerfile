# Dockerfile para aplicação Spring Boot
FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /app
COPY . .
RUN ./gradlew build -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 9292
ENTRYPOINT ["java","-jar","app.jar"]
