#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
