# Etapa 1: compila a API e gera o jar com todas as dependências.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: imagem final, só com o Java de execução e o jar.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S forca && adduser -S forca -G forca
COPY --from=build /app/target/forca-backend.jar app.jar
USER forca
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
