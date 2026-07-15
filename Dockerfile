FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ADD --chmod=644 https://github.com/grafana/pyroscope-java/releases/download/v2.8.0/pyroscope.jar /app/pyroscope.jar

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/pyroscope.jar", "-jar", "app.jar"]
