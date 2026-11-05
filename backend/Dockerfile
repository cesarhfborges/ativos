# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
# Os testes rodam fora da imagem: os de integração precisam de acesso ao Docker.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# Mesma linha do Tomcat usada pelo Spring Boot 3.5 (10.1, Jakarta EE 10).
FROM tomcat:10.1-jre17-temurin-noble AS runtime
# curl é usado pelo healthcheck do Compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home app
# ROOT.war publica a aplicação na raiz, sem prefixo de contexto na URL.
COPY --from=build /workspace/core-api/target/ativos-backend.war /usr/local/tomcat/webapps/ROOT.war
RUN chown -R app:app /usr/local/tomcat
USER app
EXPOSE 8080
