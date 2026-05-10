# Memind Java Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the official memind Java Client SDK with sync/async APIs and Spring Boot Starter auto-configuration.

**Architecture:** Two Maven modules under `memind-clients/java/` — a core `memind-client` module (JDK HttpClient + Jackson 2.x) and a `memind-client-spring-boot-starter` module. The core module is framework-agnostic; the starter provides Spring Boot auto-configuration.

**Tech Stack:** Java 17, JDK HttpClient, Jackson 2.18.x, SLF4J 2.0.x, Spring Boot 3.2+ (starter only), JUnit 5, WireMock

---

## File Map

### memind-clients/java/ (parent POM)
| File | Responsibility |
|------|---------------|
| `pom.xml` | Parent POM, version management, module aggregation |

### memind-clients/java/memind-client/ (core module)
| File | Responsibility |
|------|---------------|
| `pom.xml` | Core module build config |
| `src/main/java/com/openmemind/ai/client/MemindClient.java` | Public API: builder, sync/async methods, lifecycle |
| `src/main/java/com/openmemind/ai/client/model/common/Role.java` | Enum: USER, ASSISTANT |
| `src/main/java/com/openmemind/ai/client/model/common/Strategy.java` | Enum: SIMPLE, DEEP |
| `src/main/java/com/openmemind/ai/client/model/common/ContentBlock.java` | Sealed interface + TextBlock, ImageBlock, AudioBlock, VideoBlock records |
| `src/main/java/com/openmemind/ai/client/model/common/Source.java` | Sealed interface + UrlSource, Base64Source records |
| `src/main/java/com/openmemind/ai/client/model/common/Message.java` | Message record with factory methods |
| `src/main/java/com/openmemind/ai/client/model/common/RawContent.java` | Abstract base class for raw content |
| `src/main/java/com/openmemind/ai/client/model/common/ConversationContent.java` | Conversation RawContent implementation |
| `src/main/java/com/openmemind/ai/client/model/common/MapRawContent.java` | Generic Map-based RawContent |
| `src/main/java/com/openmemind/ai/client/model/request/AddMessageRequest.java` | Request record + builder |
| `src/main/java/com/openmemind/ai/client/model/request/ExtractMemoryRequest.java` | Request record + builder |
| `src/main/java/com/openmemind/ai/client/model/request/CommitMemoryRequest.java` | Request record + builder |
| `src/main/java/com/openmemind/ai/client/model/request/RetrieveMemoryRequest.java` | Request record + builder |
| `src/main/java/com/openmemind/ai/client/model/response/HealthResponse.java` | Response record |
| `src/main/java/com/openmemind/ai/client/model/response/RetrieveMemoryResponse.java` | Response record with nested types |
| `src/main/java/com/openmemind/ai/client/model/response/RetrievalTraceView.java` | Trace response record |
| `src/main/java/com/openmemind/ai/client/exception/MemindClientException.java` | Base exception |
| `src/main/java/com/openmemind/ai/client/exception/MemindApiException.java` | Server error exception |
| `src/main/java/com/openmemind/ai/client/exception/MemindConnectionException.java` | Network exception |
| `src/main/java/com/openmemind/ai/client/exception/MemindTimeoutException.java` | Timeout exception |
| `src/main/java/com/openmemind/ai/client/internal/ApiResult.java` | Internal response wrapper |
| `src/main/java/com/openmemind/ai/client/internal/MemindHttpClient.java` | HTTP layer implementation |
| `src/main/java/com/openmemind/ai/client/internal/RawContentSerializer.java` | Custom Jackson serializer |
| `src/test/java/com/openmemind/ai/client/model/common/MessageTest.java` | Message serialization tests |
| `src/test/java/com/openmemind/ai/client/model/common/RawContentSerializerTest.java` | RawContent serialization tests |
| `src/test/java/com/openmemind/ai/client/internal/MemindHttpClientTest.java` | HTTP client unit tests |
| `src/test/java/com/openmemind/ai/client/MemindClientTest.java` | Integration tests with WireMock |

### memind-clients/java/memind-client-spring-boot-starter/
| File | Responsibility |
|------|---------------|
| `pom.xml` | Starter module build config |
| `src/main/java/com/openmemind/ai/client/spring/MemindClientProperties.java` | Configuration properties binding |
| `src/main/java/com/openmemind/ai/client/spring/MemindClientAutoConfiguration.java` | Auto-configuration class |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Registration file |
| `src/test/java/com/openmemind/ai/client/spring/MemindClientAutoConfigurationTest.java` | Auto-config tests |

---

## Task 1: Project Scaffolding — Maven POMs

**Files:**
- Create: `memind-clients/java/pom.xml`
- Create: `memind-clients/java/memind-client/pom.xml`
- Create: `memind-clients/java/memind-client-spring-boot-starter/pom.xml`

- [ ] **Step 1: Create parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-client-parent</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Memind Client - Parent</name>
    <description>Official Java Client SDK for Memind</description>
    <url>https://github.com/openmemind/memind</url>

    <licenses>
        <license>
            <name>Apache License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
    </licenses>

    <modules>
        <module>memind-client</module>
        <module>memind-client-spring-boot-starter</module>
    </modules>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>17</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>

        <jackson.version>2.18.3</jackson.version>
        <slf4j.version>2.0.17</slf4j.version>
        <spring-boot.version>3.4.5</spring-boot.version>

        <!-- Test -->
        <junit.version>5.11.4</junit.version>
        <wiremock.version>3.12.1</wiremock.version>
        <assertj.version>3.27.3</assertj.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.fasterxml.jackson</groupId>
                <artifactId>jackson-bom</artifactId>
                <version>${jackson.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.junit</groupId>
                <artifactId>junit-bom</artifactId>
                <version>${junit.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.wiremock</groupId>
                <artifactId>wiremock-standalone</artifactId>
                <version>${wiremock.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.15.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create memind-client module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
    Licensed under the Apache License, Version 2.0 (the "License");
    ...
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.openmemind.ai</groupId>
        <artifactId>memind-client-parent</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </parent>

    <artifactId>memind-client</artifactId>
    <name>Memind Client</name>
    <description>Official Java Client for Memind API</description>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>${slf4j.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create memind-client-spring-boot-starter module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.openmemind.ai</groupId>
        <artifactId>memind-client-parent</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </parent>

    <artifactId>memind-client-spring-boot-starter</artifactId>
    <name>Memind Client Spring Boot Starter</name>
    <description>Spring Boot auto-configuration for Memind Client</description>

    <dependencies>
        <dependency>
            <groupId>com.openmemind.ai</groupId>
            <artifactId>memind-client</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <version>${spring-boot.version}</version>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <version>${spring-boot.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Verify build compiles**

Run: `cd memind-clients/java && mvn compile -q`
Expected: BUILD SUCCESS (no source files yet, just validates POM structure)

- [ ] **Step 5: Commit**

```bash
git add memind-clients/
git commit -m "feat(client): scaffold Maven project structure for Java client"
```

---

## Task 2: Exception Hierarchy + Enums + Source/ContentBlock Models

**Files:**
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/exception/MemindClientException.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/exception/MemindApiException.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/exception/MemindConnectionException.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/exception/MemindTimeoutException.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/Role.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/Strategy.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/Source.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/ContentBlock.java`

- [ ] **Step 1: Create exception classes**

`MemindClientException.java`:
```java
package com.openmemind.ai.client.exception;

public class MemindClientException extends RuntimeException {
    public MemindClientException(String message) {
        super(message);
    }

    public MemindClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`MemindApiException.java`:
```java
package com.openmemind.ai.client.exception;

public class MemindApiException extends MemindClientException {
    private final int httpStatus;
    private final String errorCode;
    private final String errorMessage;
    private final String traceId;

    public MemindApiException(int httpStatus, String errorCode, String errorMessage, String traceId) {
        super("Memind API error [" + httpStatus + "]: " + errorCode + " - " + errorMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.traceId = traceId;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getTraceId() { return traceId; }
}
```

`MemindConnectionException.java`:
```java
package com.openmemind.ai.client.exception;

public class MemindConnectionException extends MemindClientException {
    public MemindConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`MemindTimeoutException.java`:
```java
package com.openmemind.ai.client.exception;

public class MemindTimeoutException extends MemindClientException {
    public MemindTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Create enums**

`Role.java`:
```java
package com.openmemind.ai.client.model.common;

public enum Role {
    USER,
    ASSISTANT
}
```

`Strategy.java`:
```java
package com.openmemind.ai.client.model.common;

public enum Strategy {
    SIMPLE,
    DEEP
}
```

- [ ] **Step 3: Create Source sealed interface**

`Source.java`:
```java
package com.openmemind.ai.client.model.common;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Source.UrlSource.class, name = "url"),
    @JsonSubTypes.Type(value = Source.Base64Source.class, name = "base64")
})
public sealed interface Source permits Source.UrlSource, Source.Base64Source {

    record UrlSource(String url) implements Source {}

    record Base64Source(String mediaType, String data) implements Source {}
}
```

- [ ] **Step 4: Create ContentBlock sealed interface**

`ContentBlock.java`:
```java
package com.openmemind.ai.client.model.common;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
    @JsonSubTypes.Type(value = ContentBlock.ImageBlock.class, name = "image"),
    @JsonSubTypes.Type(value = ContentBlock.AudioBlock.class, name = "audio"),
    @JsonSubTypes.Type(value = ContentBlock.VideoBlock.class, name = "video")
})
public sealed interface ContentBlock
        permits ContentBlock.TextBlock, ContentBlock.ImageBlock,
                ContentBlock.AudioBlock, ContentBlock.VideoBlock {

    record TextBlock(String text) implements ContentBlock {}

    record ImageBlock(Source source) implements ContentBlock {}

    record AudioBlock(Source source) implements ContentBlock {}

    record VideoBlock(Source source) implements ContentBlock {}
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd memind-clients/java && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add memind-clients/java/memind-client/src/
git commit -m "feat(client): add exception hierarchy, enums, ContentBlock and Source models"
```

---

## Task 3: Message Model + RawContent + Serializer

**Files:**
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/Message.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/RawContent.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/ConversationContent.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/common/MapRawContent.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/internal/RawContentSerializer.java`
- Create: `memind-clients/java/memind-client/src/test/java/com/openmemind/ai/client/model/common/MessageTest.java`
- Create: `memind-clients/java/memind-client/src/test/java/com/openmemind/ai/client/model/common/RawContentSerializerTest.java`

- [ ] **Step 1: Create Message record**

```java
package com.openmemind.ai.client.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
        Role role,
        List<ContentBlock> content,
        Instant timestamp,
        String userName,
        String sourceClient) {

    public static Message user(String text) {
        return new Message(Role.USER, List.of(new ContentBlock.TextBlock(text)), null, null, null);
    }

    public static Message user(String text, Instant timestamp) {
        return new Message(Role.USER, List.of(new ContentBlock.TextBlock(text)), timestamp, null, null);
    }

    public static Message assistant(String text) {
        return new Message(Role.ASSISTANT, List.of(new ContentBlock.TextBlock(text)), null, null, null);
    }

    public static Message assistant(String text, Instant timestamp) {
        return new Message(Role.ASSISTANT, List.of(new ContentBlock.TextBlock(text)), timestamp, null, null);
    }
}
```

- [ ] **Step 2: Create RawContent base class**

```java
package com.openmemind.ai.client.model.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.openmemind.ai.client.internal.RawContentSerializer;

@JsonSerialize(using = RawContentSerializer.class)
public abstract class RawContent {
    public abstract String type();
}
```

- [ ] **Step 3: Create ConversationContent**

```java
package com.openmemind.ai.client.model.common;

import java.util.List;
import java.util.Objects;

public final class ConversationContent extends RawContent {

    private final List<Message> messages;

    private ConversationContent(List<Message> messages) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    public static ConversationContent of(List<Message> messages) {
        return new ConversationContent(messages);
    }

    @Override
    public String type() {
        return "conversation";
    }

    public List<Message> getMessages() {
        return messages;
    }
}
```

- [ ] **Step 4: Create MapRawContent**

```java
package com.openmemind.ai.client.model.common;

import java.util.Map;
import java.util.Objects;

public final class MapRawContent extends RawContent {

    private final String type;
    private final Map<String, Object> properties;

    private MapRawContent(String type, Map<String, Object> properties) {
        this.type = Objects.requireNonNull(type, "type");
        this.properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    }

    public static MapRawContent of(String type, Map<String, Object> properties) {
        return new MapRawContent(type, properties);
    }

    @Override
    public String type() {
        return type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
```

- [ ] **Step 5: Create RawContentSerializer**

```java
package com.openmemind.ai.client.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.openmemind.ai.client.model.common.ConversationContent;
import com.openmemind.ai.client.model.common.MapRawContent;
import com.openmemind.ai.client.model.common.RawContent;
import java.io.IOException;

public class RawContentSerializer extends JsonSerializer<RawContent> {

    @Override
    public void serialize(RawContent value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", value.type());

        if (value instanceof ConversationContent conv) {
            gen.writeObjectField("messages", conv.getMessages());
        } else if (value instanceof MapRawContent map) {
            for (var entry : map.getProperties().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
        }

        gen.writeEndObject();
    }
}
```

- [ ] **Step 6: Write serialization tests**

`MessageTest.java`:
```java
package com.openmemind.ai.client.model.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class MessageTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void userMessage_serializesCorrectly() throws Exception {
        Message msg = Message.user("hello");
        String json = mapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"USER\"");
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"hello\"");
    }

    @Test
    void assistantMessage_serializesCorrectly() throws Exception {
        Message msg = Message.assistant("hi there");
        String json = mapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"ASSISTANT\"");
        assertThat(json).contains("\"text\":\"hi there\"");
    }
}
```

`RawContentSerializerTest.java`:
```java
package com.openmemind.ai.client.model.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawContentSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void conversationContent_serializesWithTypeField() throws Exception {
        ConversationContent content = ConversationContent.of(List.of(
                Message.user("hello"),
                Message.assistant("hi")));

        String json = mapper.writeValueAsString(content);

        assertThat(json).contains("\"type\":\"conversation\"");
        assertThat(json).contains("\"messages\":[");
    }

    @Test
    void mapRawContent_serializesPropertiesFlat() throws Exception {
        MapRawContent content = MapRawContent.of("document", Map.of(
                "fileName", "test.pdf",
                "mimeType", "application/pdf"));

        String json = mapper.writeValueAsString(content);

        assertThat(json).contains("\"type\":\"document\"");
        assertThat(json).contains("\"fileName\":\"test.pdf\"");
        assertThat(json).contains("\"mimeType\":\"application/pdf\"");
    }
}
```

- [ ] **Step 7: Run tests**

Run: `cd memind-clients/java && mvn test -pl memind-client -q`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add memind-clients/java/memind-client/src/
git commit -m "feat(client): add Message, RawContent models with custom serializer"
```

---

## Task 4: Request Models

**Files:**
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/request/AddMessageRequest.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/request/ExtractMemoryRequest.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/request/CommitMemoryRequest.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/request/RetrieveMemoryRequest.java`

- [ ] **Step 1: Create AddMessageRequest**

```java
package com.openmemind.ai.client.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openmemind.ai.client.model.common.Message;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddMessageRequest(String userId, String agentId, Message message, String sourceClient) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userId;
        private String agentId;
        private Message message;
        private String sourceClient;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder message(Message message) { this.message = message; return this; }
        public Builder sourceClient(String sourceClient) { this.sourceClient = sourceClient; return this; }

        public AddMessageRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(message, "message");
            return new AddMessageRequest(userId, agentId, message, sourceClient);
        }
    }
}
```

- [ ] **Step 2: Create ExtractMemoryRequest**

```java
package com.openmemind.ai.client.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openmemind.ai.client.model.common.RawContent;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractMemoryRequest(String userId, String agentId, RawContent rawContent, String sourceClient) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userId;
        private String agentId;
        private RawContent rawContent;
        private String sourceClient;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder rawContent(RawContent rawContent) { this.rawContent = rawContent; return this; }
        public Builder sourceClient(String sourceClient) { this.sourceClient = sourceClient; return this; }

        public ExtractMemoryRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(rawContent, "rawContent");
            return new ExtractMemoryRequest(userId, agentId, rawContent, sourceClient);
        }
    }
}
```

- [ ] **Step 3: Create CommitMemoryRequest**

```java
package com.openmemind.ai.client.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitMemoryRequest(String userId, String agentId, String sourceClient) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userId;
        private String agentId;
        private String sourceClient;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder sourceClient(String sourceClient) { this.sourceClient = sourceClient; return this; }

        public CommitMemoryRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            return new CommitMemoryRequest(userId, agentId, sourceClient);
        }
    }
}
```

- [ ] **Step 4: Create RetrieveMemoryRequest**

```java
package com.openmemind.ai.client.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openmemind.ai.client.model.common.Strategy;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrieveMemoryRequest(
        String userId, String agentId, String query, Strategy strategy, Boolean trace) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userId;
        private String agentId;
        private String query;
        private Strategy strategy;
        private Boolean trace;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder query(String query) { this.query = query; return this; }
        public Builder strategy(Strategy strategy) { this.strategy = strategy; return this; }
        public Builder trace(Boolean trace) { this.trace = trace; return this; }

        public RetrieveMemoryRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(strategy, "strategy");
            return new RetrieveMemoryRequest(userId, agentId, query, strategy, trace);
        }
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd memind-clients/java && mvn compile -pl memind-client -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/request/
git commit -m "feat(client): add request model records with builders"
```

---

## Task 5: Response Models

**Files:**
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/response/HealthResponse.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/response/RetrieveMemoryResponse.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/response/RetrievalTraceView.java`
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/internal/ApiResult.java`

- [ ] **Step 1: Create HealthResponse**

```java
package com.openmemind.ai.client.model.response;

public record HealthResponse(String status, String service) {}
```

- [ ] **Step 2: Create RetrieveMemoryResponse**

```java
package com.openmemind.ai.client.model.response;

import java.time.Instant;
import java.util.List;

public record RetrieveMemoryResponse(
        String status,
        List<RetrievedItem> items,
        List<RetrievedInsight> insights,
        List<RetrievedRawData> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalTraceView trace) {

    public record RetrievedItem(
            String id, String text, float vectorScore, double finalScore, Instant occurredAt) {}

    public record RetrievedInsight(String id, String text, String tier) {}

    public record RetrievedRawData(
            String rawDataId, String caption, double maxScore, List<String> itemIds) {}
}
```

- [ ] **Step 3: Create RetrievalTraceView**

```java
package com.openmemind.ai.client.model.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RetrievalTraceView(
        String traceId,
        Instant startedAt,
        Instant completedAt,
        boolean truncated,
        List<StageView> stages,
        MergeView merge,
        FinalView finalResults) {

    public record StageView(
            String stage,
            String tier,
            String method,
            String status,
            Integer inputCount,
            Integer candidateCount,
            Integer resultCount,
            boolean degraded,
            boolean skipped,
            Instant startedAt,
            Long durationMillis,
            Map<String, Object> attributes) {}

    public record MergeView(
            int inputCount, int outputCount, int deduplicatedCount, int sourceCount, String status) {}

    public record FinalView(
            String strategy, String status, int itemCount,
            int insightCount, int rawDataCount, int evidenceCount) {}
}
```

- [ ] **Step 4: Create internal ApiResult**

```java
package com.openmemind.ai.client.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResult<T>(String code, String message, T data, Instant timestamp, String traceId) {

    public boolean isSuccess() {
        return "200".equals(code) || "success".equals(code);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd memind-clients/java && mvn compile -pl memind-client -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/model/response/ \
        memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/internal/ApiResult.java
git commit -m "feat(client): add response models and internal ApiResult wrapper"
```

---

## Task 6: MemindHttpClient — Internal HTTP Layer

**Files:**
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/internal/MemindHttpClient.java`
- Create: `memind-clients/java/memind-client/src/test/java/com/openmemind/ai/client/internal/MemindHttpClientTest.java`

- [ ] **Step 1: Write failing test for MemindHttpClient**

```java
package com.openmemind.ai.client.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.openmemind.ai.client.exception.MemindApiException;
import com.openmemind.ai.client.exception.MemindTimeoutException;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

@WireMockTest
class MemindHttpClientTest {

    @Test
    void get_successfulResponse_returnsData(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/open/v1/health").willReturn(okJson("""
                {"code":"success","data":{"status":"UP","service":"memind-server"},"timestamp":"2026-01-01T00:00:00Z"}
                """)));

        MemindHttpClient httpClient = new MemindHttpClient(
                wmInfo.getHttpBaseUrl(), null, Duration.ofSeconds(5), Duration.ofSeconds(5));

        HealthResponse result = httpClient.get(
                "/open/v1/health", new TypeReference<ApiResult<HealthResponse>>() {}).join();

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.service()).isEqualTo("memind-server");
    }

    @Test
    void post_apiError_throwsMemindApiException(WireMockRuntimeInfo wmInfo) {
        stubFor(post("/open/v1/memory/retrieve").willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"bad_request","message":"query is required","timestamp":"2026-01-01T00:00:00Z","traceId":"abc123"}
                        """)));

        MemindHttpClient httpClient = new MemindHttpClient(
                wmInfo.getHttpBaseUrl(), null, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> httpClient.post(
                "/open/v1/memory/retrieve", Map.of(), new TypeReference<ApiResult<Void>>() {}).join())
                .hasCauseInstanceOf(MemindApiException.class)
                .extracting(e -> (MemindApiException) e.getCause())
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(400);
                    assertThat(ex.getErrorCode()).isEqualTo("bad_request");
                    assertThat(ex.getTraceId()).isEqualTo("abc123");
                });
    }

    @Test
    void post_withApiToken_sendsAuthorizationHeader(WireMockRuntimeInfo wmInfo) {
        stubFor(post("/open/v1/memory/commit").willReturn(okJson("""
                {"code":"200","timestamp":"2026-01-01T00:00:00Z"}
                """)));

        MemindHttpClient httpClient = new MemindHttpClient(
                wmInfo.getHttpBaseUrl(), "mk-test-token", Duration.ofSeconds(5), Duration.ofSeconds(5));

        httpClient.post("/open/v1/memory/commit", Map.of("userId", "u1", "agentId", "a1"),
                new TypeReference<ApiResult<Void>>() {}).join();

        verify(postRequestedFor(urlEqualTo("/open/v1/memory/commit"))
                .withHeader("Authorization", equalTo("Bearer mk-test-token"))
                .withHeader("User-Agent", matching("memind-java-client/.*")));
    }

    @Test
    void get_timeout_throwsMemindTimeoutException(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/open/v1/health").willReturn(ok().withFixedDelay(3000)));

        MemindHttpClient httpClient = new MemindHttpClient(
                wmInfo.getHttpBaseUrl(), null, Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(() -> httpClient.get(
                "/open/v1/health", new TypeReference<ApiResult<HealthResponse>>() {}).join())
                .hasCauseInstanceOf(MemindTimeoutException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd memind-clients/java && mvn test -pl memind-client -Dtest=MemindHttpClientTest -q`
Expected: COMPILATION ERROR (MemindHttpClient doesn't exist yet)

- [ ] **Step 3: Implement MemindHttpClient**

```java
package com.openmemind.ai.client.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.client.exception.MemindApiException;
import com.openmemind.ai.client.exception.MemindConnectionException;
import com.openmemind.ai.client.exception.MemindTimeoutException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemindHttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemindHttpClient.class);
    private static final String USER_AGENT = "memind-java-client/0.2.0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiToken;
    private final Duration readTimeout;

    public MemindHttpClient(String baseUrl, String apiToken, Duration connectTimeout, Duration readTimeout) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.apiToken = apiToken;
        this.readTimeout = readTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    }

    public <T> CompletableFuture<T> get(String path, TypeReference<ApiResult<T>> responseType) {
        HttpRequest request = newRequestBuilder(path).GET().build();
        return sendAsync(request, responseType);
    }

    public <T> CompletableFuture<T> post(String path, Object body, TypeReference<ApiResult<T>> responseType) {
        try {
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            HttpRequest request = newRequestBuilder(path)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
            log.debug("POST {}", path);
            return sendAsync(request, responseType);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(
                    new MemindConnectionException("Failed to serialize request body", e));
        }
    }

    private HttpRequest.Builder newRequestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .timeout(readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        if (apiToken != null) {
            builder.header("Authorization", "Bearer " + apiToken);
        }
        return builder;
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, TypeReference<ApiResult<T>> responseType) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> handleResponse(response, responseType));
    }

    private <T> T handleResponse(HttpResponse<byte[]> response, TypeReference<ApiResult<T>> responseType) {
        int status = response.statusCode();
        byte[] body = response.body();
        log.debug("Response status: {}", status);

        try {
            ApiResult<T> result = objectMapper.readValue(body, responseType);

            if (status >= 200 && status < 300 && result.isSuccess()) {
                return result.data();
            }

            throw new MemindApiException(
                    status,
                    result.code(),
                    result.message(),
                    result.traceId());
        } catch (MemindApiException e) {
            throw e;
        } catch (IOException e) {
            throw new MemindApiException(status, "parse_error",
                    "Failed to parse response: " + new String(body), null);
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public void close() {
        // JDK HttpClient doesn't require explicit close in Java 17
        // In Java 21+ we could call httpClient.close()
    }
}
```

Note: The timeout exception mapping needs to be handled in the `sendAsync` chain. Update the `sendAsync` method:

```java
private <T> CompletableFuture<T> sendAsync(HttpRequest request, TypeReference<ApiResult<T>> responseType) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(response -> handleResponse(response, responseType))
            .exceptionallyCompose(ex -> {
                Throwable cause = ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
                if (cause instanceof java.net.http.HttpTimeoutException) {
                    return CompletableFuture.failedFuture(
                            new MemindTimeoutException("Request timed out: " + request.uri(), cause));
                }
                if (cause instanceof java.net.ConnectException || cause instanceof java.io.IOException) {
                    return CompletableFuture.failedFuture(
                            new MemindConnectionException("Connection failed: " + request.uri(), cause));
                }
                if (cause instanceof MemindApiException) {
                    return CompletableFuture.failedFuture(cause);
                }
                return CompletableFuture.failedFuture(
                        new MemindConnectionException("Unexpected error: " + cause.getMessage(), cause));
            });
}
```

- [ ] **Step 4: Run tests**

Run: `cd memind-clients/java && mvn test -pl memind-client -Dtest=MemindHttpClientTest -q`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add memind-clients/java/memind-client/src/
git commit -m "feat(client): implement MemindHttpClient with error handling and auth"
```

---

## Task 7: MemindClient — Public API

**Files:**
- Create: `memind-clients/java/memind-client/src/main/java/com/openmemind/ai/client/MemindClient.java`
- Create: `memind-clients/java/memind-client/src/test/java/com/openmemind/ai/client/MemindClientTest.java`

- [ ] **Step 1: Write integration tests**

```java
package com.openmemind.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.openmemind.ai.client.model.common.ConversationContent;
import com.openmemind.ai.client.model.common.Message;
import com.openmemind.ai.client.model.common.Strategy;
import com.openmemind.ai.client.model.request.*;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.openmemind.ai.client.model.response.RetrieveMemoryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

@WireMockTest
class MemindClientTest {

    @Test
    void health_returnsResponse(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/open/v1/health").willReturn(okJson("""
                {"code":"success","data":{"status":"UP","service":"memind-server"}}
                """)));

        try (MemindClient client = MemindClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .build()) {
            HealthResponse health = client.health();
            assertThat(health.status()).isEqualTo("UP");
        }
    }

    @Test
    void addMessage_sendsCorrectPayload(WireMockRuntimeInfo wmInfo) {
        stubFor(post("/open/v1/memory/add-message").willReturn(okJson("""
                {"code":"200"}
                """)));

        try (MemindClient client = MemindClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .build()) {
            client.addMessage(AddMessageRequest.builder()
                    .userId("user-1")
                    .agentId("agent-1")
                    .message(Message.user("hello"))
                    .build());
        }

        verify(postRequestedFor(urlEqualTo("/open/v1/memory/add-message"))
                .withRequestBody(matchingJsonPath("$.userId", equalTo("user-1")))
                .withRequestBody(matchingJsonPath("$.message.role", equalTo("USER"))));
    }

    @Test
    void extract_sendsRawContent(WireMockRuntimeInfo wmInfo) {
        stubFor(post("/open/v1/memory/extract").willReturn(okJson("""
                {"code":"200"}
                """)));

        try (MemindClient client = MemindClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .build()) {
            client.extract(ExtractMemoryRequest.builder()
                    .userId("user-1")
                    .agentId("agent-1")
                    .rawContent(ConversationContent.of(List.of(Message.user("test"))))
                    .build());
        }

        verify(postRequestedFor(urlEqualTo("/open/v1/memory/extract"))
                .withRequestBody(matchingJsonPath("$.rawContent.type", equalTo("conversation"))));
    }

    @Test
    void retrieve_returnsMemories(WireMockRuntimeInfo wmInfo) {
        stubFor(post("/open/v1/memory/retrieve").willReturn(okJson("""
                {"code":"success","data":{
                    "status":"found","items":[{"id":"1","text":"memory text","vectorScore":0.9,"finalScore":0.85}],
                    "insights":[],"rawData":[],"evidences":[],"strategy":"SIMPLE","query":"test"
                }}
                """)));

        try (MemindClient client = MemindClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .build()) {
            RetrieveMemoryResponse response = client.retrieve(RetrieveMemoryRequest.builder()
                    .userId("user-1")
                    .agentId("agent-1")
                    .query("test")
                    .strategy(Strategy.SIMPLE)
                    .build());

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).text()).isEqualTo("memory text");
        }
    }

    @Test
    void builder_missingBaseUrl_throws() {
        assertThatThrownBy(() -> MemindClient.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void close_thenCall_throwsIllegalState(WireMockRuntimeInfo wmInfo) {
        MemindClient client = MemindClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .build();
        client.close();

        assertThatThrownBy(client::health)
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd memind-clients/java && mvn test -pl memind-client -Dtest=MemindClientTest -q`
Expected: COMPILATION ERROR (MemindClient doesn't exist yet)

- [ ] **Step 3: Implement MemindClient**

```java
package com.openmemind.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openmemind.ai.client.internal.ApiResult;
import com.openmemind.ai.client.internal.MemindHttpClient;
import com.openmemind.ai.client.model.request.*;
import com.openmemind.ai.client.model.response.HealthResponse;
import com.openmemind.ai.client.model.response.RetrieveMemoryResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MemindClient implements AutoCloseable {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    private final MemindHttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private MemindClient(Builder builder) {
        this.httpClient = new MemindHttpClient(
                builder.baseUrl, builder.apiToken, builder.connectTimeout, builder.readTimeout);
    }

    // === Sync API ===

    public void addMessage(AddMessageRequest request) {
        addMessageAsync(request).join();
    }

    public void extract(ExtractMemoryRequest request) {
        extractAsync(request).join();
    }

    public void commit(CommitMemoryRequest request) {
        commitAsync(request).join();
    }

    public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request) {
        return retrieveAsync(request).join();
    }

    public HealthResponse health() {
        return healthAsync().join();
    }

    // === Async API ===

    public CompletableFuture<Void> addMessageAsync(AddMessageRequest request) {
        ensureOpen();
        return httpClient.post("/open/v1/memory/add-message", request,
                new TypeReference<ApiResult<Void>>() {});
    }

    public CompletableFuture<Void> extractAsync(ExtractMemoryRequest request) {
        ensureOpen();
        return httpClient.post("/open/v1/memory/extract", request,
                new TypeReference<ApiResult<Void>>() {});
    }

    public CompletableFuture<Void> commitAsync(CommitMemoryRequest request) {
        ensureOpen();
        return httpClient.post("/open/v1/memory/commit", request,
                new TypeReference<ApiResult<Void>>() {});
    }

    public CompletableFuture<RetrieveMemoryResponse> retrieveAsync(RetrieveMemoryRequest request) {
        ensureOpen();
        return httpClient.post("/open/v1/memory/retrieve", request,
                new TypeReference<ApiResult<RetrieveMemoryResponse>>() {});
    }

    public CompletableFuture<HealthResponse> healthAsync() {
        ensureOpen();
        return httpClient.get("/open/v1/health",
                new TypeReference<ApiResult<HealthResponse>>() {});
    }

    // === Lifecycle ===

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            httpClient.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("MemindClient is closed");
        }
    }

    // === Builder ===

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String baseUrl;
        private String apiToken;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder apiToken(String apiToken) { this.apiToken = apiToken; return this; }
        public Builder connectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; return this; }
        public Builder readTimeout(Duration readTimeout) { this.readTimeout = readTimeout; return this; }

        public MemindClient build() {
            Objects.requireNonNull(baseUrl, "baseUrl");
            return new MemindClient(this);
        }
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `cd memind-clients/java && mvn test -pl memind-client -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add memind-clients/java/memind-client/src/
git commit -m "feat(client): implement MemindClient with sync/async API and builder"
```

---

## Task 8: Spring Boot Starter

**Files:**
- Create: `memind-clients/java/memind-client-spring-boot-starter/src/main/java/com/openmemind/ai/client/spring/MemindClientProperties.java`
- Create: `memind-clients/java/memind-client-spring-boot-starter/src/main/java/com/openmemind/ai/client/spring/MemindClientAutoConfiguration.java`
- Create: `memind-clients/java/memind-client-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `memind-clients/java/memind-client-spring-boot-starter/src/test/java/com/openmemind/ai/client/spring/MemindClientAutoConfigurationTest.java`

- [ ] **Step 1: Create MemindClientProperties**

```java
package com.openmemind.ai.client.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.client")
public class MemindClientProperties {

    private String baseUrl;
    private String apiToken;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
```

- [ ] **Step 2: Create MemindClientAutoConfiguration**

```java
package com.openmemind.ai.client.spring;

import com.openmemind.ai.client.MemindClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(MemindClientProperties.class)
@ConditionalOnClass(MemindClient.class)
public class MemindClientAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "memind.client", name = "base-url")
    public MemindClient memindClient(MemindClientProperties properties) {
        MemindClient.Builder builder = MemindClient.builder()
                .baseUrl(properties.getBaseUrl());

        if (properties.getApiToken() != null) {
            builder.apiToken(properties.getApiToken());
        }
        if (properties.getConnectTimeout() != null) {
            builder.connectTimeout(properties.getConnectTimeout());
        }
        if (properties.getReadTimeout() != null) {
            builder.readTimeout(properties.getReadTimeout());
        }

        return builder.build();
    }
}
```

- [ ] **Step 3: Create AutoConfiguration.imports registration**

File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
com.openmemind.ai.client.spring.MemindClientAutoConfiguration
```

- [ ] **Step 4: Write auto-configuration tests**

```java
package com.openmemind.ai.client.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.client.MemindClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemindClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MemindClientAutoConfiguration.class));

    @Test
    void autoConfiguration_withBaseUrl_createsBean() {
        contextRunner
                .withPropertyValues("memind.client.base-url=http://localhost:8366")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemindClient.class);
                });
    }

    @Test
    void autoConfiguration_withoutBaseUrl_doesNotCreateBean() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MemindClient.class);
        });
    }

    @Test
    void autoConfiguration_withCustomBean_doesNotOverride() {
        contextRunner
                .withPropertyValues("memind.client.base-url=http://localhost:8366")
                .withBean(MemindClient.class, () -> MemindClient.builder()
                        .baseUrl("http://custom:9999")
                        .build())
                .run(context -> {
                    assertThat(context).hasSingleBean(MemindClient.class);
                });
    }

    @Test
    void autoConfiguration_bindsAllProperties() {
        contextRunner
                .withPropertyValues(
                        "memind.client.base-url=http://localhost:8366",
                        "memind.client.api-token=mk-test",
                        "memind.client.connect-timeout=10s",
                        "memind.client.read-timeout=60s")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemindClient.class);
                    MemindClientProperties props = context.getBean(MemindClientProperties.class);
                    assertThat(props.getApiToken()).isEqualTo("mk-test");
                    assertThat(props.getConnectTimeout().getSeconds()).isEqualTo(10);
                    assertThat(props.getReadTimeout().getSeconds()).isEqualTo(60);
                });
    }
}
```

- [ ] **Step 5: Run starter tests**

Run: `cd memind-clients/java && mvn test -pl memind-client-spring-boot-starter -q`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add memind-clients/java/memind-client-spring-boot-starter/
git commit -m "feat(client): add Spring Boot Starter with auto-configuration"
```

---

## Task 9: Full Build Verification + Final Commit

**Files:**
- No new files

- [ ] **Step 1: Run full build from parent**

Run: `cd memind-clients/java && mvn clean verify -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Verify test count**

Run: `cd memind-clients/java && mvn test -q 2>&1 | grep "Tests run"`
Expected: Shows test counts for both modules, 0 failures, 0 errors

- [ ] **Step 3: Final commit if any formatting fixes needed**

If spotless or any formatting was applied:
```bash
git add -A memind-clients/java/
git commit -m "style(client): apply code formatting"
```




