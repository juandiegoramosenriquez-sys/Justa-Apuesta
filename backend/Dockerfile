# Imagen para Render (plan gratis, 512 MB). Compila con Maven y corre con Java 21.
FROM maven:3.9-eclipse-temurin-21 AS compilar
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilar /app/target/*.jar app.jar
# Render pone el puerto en PORT; el perfil prod usa MySQL de Aiven
ENV SPRING_PROFILES_ACTIVE=prod
# Que Java no pase los 512 MB del plan gratis
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]
