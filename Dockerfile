FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw -DskipTests package


FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home appuser

COPY --from=build /app/target/*.jar app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]