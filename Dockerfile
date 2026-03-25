# Dockerfile para aplicação Spring Boot
# Build com Gradle pre-instalado (sem download do wrapper)
FROM gradle:8.14.2-jdk17-alpine AS build
WORKDIR /app

# Copia arquivos de configuracao antes do src para aproveitar cache do Docker
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Pre-baixa dependencias (cacheia essa layer enquanto o build.gradle nao mudar)
RUN gradle dependencies --no-daemon --configuration compileClasspath --quiet 2>/dev/null || true

# Copia o codigo fonte e compila
COPY src src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 9292
ENTRYPOINT ["java", "-jar", "app.jar"]
