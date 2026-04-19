# Build stage
FROM gradle:9.3.0-jdk17 AS build
WORKDIR /workspace

# Leverage Docker layer caching
COPY build.gradle settings.gradle gradlew gradlew.bat /workspace/
COPY gradle /workspace/gradle
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew
RUN ./gradlew --no-daemon -q dependencies

COPY src /workspace/src
RUN ./gradlew --no-daemon -q bootJar

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS=""
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
