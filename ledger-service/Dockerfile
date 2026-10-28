FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ledger-service-0.0.1-SNAPSHOT.jar ledger.jar

ENTRYPOINT ["java", "-jar", "ledger.jar"]