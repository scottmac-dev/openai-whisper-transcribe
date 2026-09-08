FROM eclipse-temurin:25-jre

WORKDIR /app

COPY target/assignment1-0.0.1-SNAPSHOT.jar launch.jar

ENTRYPOINT ["java", "-jar", "launch.jar"]
