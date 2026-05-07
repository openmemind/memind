FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY . .
RUN mvn -pl memind-server -am -DskipTests -Dmaven.javadoc.skip=true \
        -Dlicense.skip=true -Dcheckstyle.skip=true package \
    && JAR="$(find memind-server/target -maxdepth 1 -type f -name 'memind-server-*.jar' \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' | head -n 1)" \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/memind-server.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /app/data
COPY --from=build /workspace/memind-server.jar /app/memind-server.jar

EXPOSE 8366
ENTRYPOINT ["java", "-jar", "/app/memind-server.jar"]
