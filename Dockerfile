# Multi-stage build: frontend + Spring Boot jar
FROM node:24-alpine AS webbuild
WORKDIR /app/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS jvbuilder
WORKDIR /app
COPY spring/pom.xml .
COPY spring/src ./src
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=webbuild /app/web/dist ./web/dist
COPY --from=jvbuilder /app/target/*.jar app.jar
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]
