# ShopSync Mini container (Render builds this automatically).
# Stage 1: Maven compiles the code and builds target/shopsync-mini.jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package

# Stage 2: run the jar on a small Java runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/shopsync-mini.jar app.jar
ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 10000
CMD ["java", "-jar", "app.jar"]
