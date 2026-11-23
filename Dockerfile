# API de Agencia360. La misma imagen sirve en local (docker-compose, perfil dev)
# y en Azure Container Apps, donde la publica el workflow despliegue.yml y la
# Container App fija SPRING_PROFILES_ACTIVE=prod con sus variables y secretos.
FROM eclipse-temurin:25-jdk AS construccion
WORKDIR /fuente
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true \
    && cp target/backend-agencia360-*-exec.jar /api.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home api \
    && mkdir -p /home/api/manuales && chown -R api:api /home/api
USER api
WORKDIR /home/api
COPY --from=construccion /api.jar api.jar
ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "api.jar"]
