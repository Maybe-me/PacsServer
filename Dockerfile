FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
COPY pacs-common ./pacs-common
COPY pacs-domain ./pacs-domain
COPY pacs-application ./pacs-application
COPY pacs-infrastructure ./pacs-infrastructure
COPY pacs-boot ./pacs-boot
COPY pacs-test ./pacs-test
COPY pacs-viewer ./pacs-viewer
RUN mvn -q -DskipTests package

FROM node:20-alpine AS viewer-build
WORKDIR /viewer
COPY pacs-viewer/package*.json ./
RUN npm install
COPY pacs-viewer ./
RUN npm run build

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=backend-build /workspace/pacs-boot/target/pacs-boot-0.1.0-SNAPSHOT-exec.jar /app/pacs-server.jar
COPY --from=viewer-build /viewer/dist /app/public
EXPOSE 8080 11112
ENTRYPOINT ["java", "-jar", "/app/pacs-server.jar"]
