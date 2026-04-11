# RawData Plugin Extraction Phase 2A Document Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `RawContentJackson` the authoritative runtime registration path and extract the Document rawdata implementation from `memind-core` into a dedicated `memind-plugin-rawdata-document` module without breaking builder-based or Spring Boot runtimes.

**Architecture:** This phase is the first executable slice of Phase 2, not the entire Phase 2 program. The slice first introduces the plugin-owned document implementation while core still owns runtime compatibility, then makes `RawContentJackson` authoritative, and finally performs one atomic runtime cutover that removes core document ownership and wires Spring/runtime JSON handling to the plugin path. To keep the cutover safe, `DocumentExtractionOptions` stays in core for Phase 2A as a transitional contract because source-size enforcement still runs in core `MemoryExtractor`; plugin-owned option extraction is deferred until a later phase adds a proper source-limit SPI.

**Tech Stack:** Java 21, Maven multi-module build, Jackson, Reactor, JUnit 5, Spring Boot auto-configuration, Apache Tika

---

## Scope

This plan intentionally covers only **Phase 2A**:

- runtime-scoped `RawContentJackson` cutover
- new `memind-plugin-rawdata-document` module
- new `memind-plugin-rawdata-jackson-starter`
- new `memind-plugin-rawdata-document-starter`
- builder/server/store wiring needed so document extraction still works once document leaves core
- shared Spring application `ObjectMapper` wiring owned by the rawdata Jackson starter so `RawContent` request/response binding still works after `@JsonSubTypes` removal
- compatibility bridges for the legacy Tika artifact and Tika starter

This plan intentionally keeps the following in core for this phase:

- `DocumentExtractionOptions`
- `RawDataExtractionOptions.document()`

Reason: document source-size enforcement still happens before parser output exists, so removing the core-owned option contract in the same slice would require a new plugin-owned source-limit SPI and would expand the phase beyond a safe document-runtime cutover.

This plan does **not** cover:

- image plugin extraction
- audio plugin extraction
- toolcall plugin extraction
- removal of the legacy compatibility bridge artifacts

Those belong to follow-up Phase 2B/2C plans.

---

## File Structure

### New files

- `memind-plugins/memind-plugin-rawdatas/pom.xml`
  Responsibility: parent POM for rawdata plugin modules.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/pom.xml`
  Responsibility: document rawdata plugin module definition.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/content/DocumentContent.java`
  Responsibility: plugin-owned document raw content model.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/content/document/DocumentSection.java`
  Responsibility: plugin-owned document section model.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/chunk/ProfileAwareDocumentChunker.java`
  Responsibility: relocated document chunker.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/processor/DocumentContentProcessor.java`
  Responsibility: relocated document processor.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/NativeTextDocumentContentParser.java`
  Responsibility: lightweight text-like document parser owned by the document plugin.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/HtmlTextExtractor.java`
  Responsibility: relocated HTML text extraction helper.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentContentParser.java`
  Responsibility: merged Tika parser implementation owned by the document plugin.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentMetadataMapper.java`
  Responsibility: merged Tika metadata mapper.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentParserSupport.java`
  Responsibility: merged Tika parser helper.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawDataPlugin.java`
  Responsibility: non-Spring `RawDataPlugin` entrypoint for the document module.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawContentTypeRegistrar.java`
  Responsibility: `RawContentTypeRegistrar` for `"document"`.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/support/DocumentExtractionRequests.java`
  Responsibility: convenience request factory replacing `ExtractionRequest.document(...)`.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawDataPluginTest.java`
  Responsibility: verifies plugin processors, parsers, and subtype registrar behavior.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/support/DocumentExtractionRequestsTest.java`
  Responsibility: verifies the plugin request-factory migration path.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/NativeTextDocumentContentParserTest.java`
  Responsibility: relocated native text parser tests.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentContentParserTest.java`
  Responsibility: relocated Tika parser tests.
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/processor/DocumentContentProcessorTest.java`
  Responsibility: relocated document processor tests.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/pom.xml`
  Responsibility: shared Spring Boot starter for rawdata Jackson registration.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/jackson/autoconfigure/RawDataJacksonAutoConfiguration.java`
  Responsibility: publishes application `ObjectMapper` customization for builtin and plugin-contributed rawdata subtypes.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  Responsibility: Spring Boot auto-configuration registration for shared rawdata Jackson wiring.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/src/test/java/com/openmemind/ai/memory/plugin/rawdata/jackson/autoconfigure/RawDataJacksonAutoConfigurationTest.java`
  Responsibility: verifies builtin rawdata registration and plugin subtype pickup through Spring `RawDataPlugin` beans.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/pom.xml`
  Responsibility: Spring Boot starter module definition and dependencies for the document plugin.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentRawDataAutoConfiguration.java`
  Responsibility: publishes the document `RawDataPlugin` bean only; shared rawdata Jackson registration lives in the common starter.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentNativeTextParserAutoConfiguration.java`
  Responsibility: publishes the native-text document parser bean behind document starter properties.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentTikaParserAutoConfiguration.java`
  Responsibility: publishes the Tika document parser bean behind document starter properties.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentRawDataProperties.java`
  Responsibility: starter properties for parser/bean enablement only.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  Responsibility: Spring Boot auto-configuration registration.
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentRawDataAutoConfigurationTest.java`
  Responsibility: verifies document starter bean composition, parser property toggles, and cooperation with the shared rawdata Jackson starter.

### Modified files

- `memind-plugins/pom.xml`
- `memind-plugins/memind-plugin-spring-boot-starters/pom.xml`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- `memind-server/pom.xml`
- `memind-server/src/main/java/com/openmemind/ai/memory/server/configuration/MemindServerRuntimeConfiguration.java`
- `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerApplicationTest.java`
- `memind-server/src/test/java/com/openmemind/ai/memory/server/configuration/MemindServerRuntimeConfigurationTest.java`
- `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodec.java`
- `memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodecTest.java`
- `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java`
- `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java`
- `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/main/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfiguration.java`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/test/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfigurationSqliteTest.java`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryMybatisPlusAutoConfiguration.java`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryStoreAutoConfigurationTest.java`
- `memind-plugins/memind-plugin-content-parser-document-tika/pom.xml`
- `memind-plugins/memind-plugin-content-parser-document-tika/src/main/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/TikaDocumentContentParser.java`
- `memind-plugins/memind-plugin-content-parser-document-tika-starter/pom.xml`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/main/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/autoconfigure/TikaDocumentParserAutoConfiguration.java`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/main/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/autoconfigure/TikaDocumentParserProperties.java`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/test/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/autoconfigure/TikaDocumentParserAutoConfigurationTest.java`
- document-related core tests that currently reference `DocumentContent` or `ExtractionRequest.document(...)`

### Deleted files

- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/document/DocumentSection.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ProfileAwareDocumentChunker.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParser.java`
- `memind-core/src/main/java/com/openmemind/ai/memory/core/resource/HtmlTextExtractor.java`

---

### Task 1: Scaffold the rawdata parent and the new document plugin module

**Files:**
- Create: `memind-plugins/memind-plugin-rawdatas/pom.xml`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/pom.xml`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/content/DocumentContent.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/content/document/DocumentSection.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawDataPlugin.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawContentTypeRegistrar.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/support/DocumentExtractionRequests.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawDataPluginTest.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/support/DocumentExtractionRequestsTest.java`
- Modify: `memind-plugins/pom.xml`

- [ ] **Step 1: Write the failing module-shape and plugin-shape tests**

```java
package com.openmemind.ai.memory.plugin.rawdata.document.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.document.support.DocumentExtractionRequests;
import org.junit.jupiter.api.Test;

class DocumentRawDataPluginTest {

    @Test
    void pluginExposesStableIdAndDocumentSubtypeRegistrar() {
        RawDataPlugin plugin = new DocumentRawDataPlugin();

        assertThat(plugin.pluginId()).isEqualTo("rawdata-document");
        assertThat(plugin.typeRegistrars())
                .singleElement()
                .extracting(RawContentTypeRegistrar::subtypes)
                .satisfies(
                        mappings -> {
                            assertThat(mappings).containsKey("document");
                        });
    }
}
```

```java
package com.openmemind.ai.memory.plugin.rawdata.document.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import org.junit.jupiter.api.Test;

class DocumentExtractionRequestsTest {

    @Test
    void documentFactoryBuildsGenericExtractionRequest() {
        var content = DocumentContent.of("Guide", "text/plain", "hello");
        var request =
                DocumentExtractionRequests.document(
                        DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.content()).isSameAs(content);
        assertThat(request.contentType()).isEqualTo(content.contentType());
    }
}
```

- [ ] **Step 2: Run the plugin tests and verify they fail**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document -Dtest=DocumentRawDataPluginTest,DocumentExtractionRequestsTest test
```

Expected: FAIL because the parent module, the document plugin module, and the new classes do not exist yet.

- [ ] **Step 3: Create the rawdata parent, module skeleton, and minimal public API surface**

Create the new parent POM:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.openmemind.ai</groupId>
        <artifactId>memind-plugins</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>memind-plugin-rawdatas</artifactId>
    <packaging>pom</packaging>
    <name>Memind - RawData Plugins</name>

    <modules>
        <module>memind-plugin-rawdata-document</module>
    </modules>
</project>
```

Update `memind-plugins/pom.xml`:

```xml
<modules>
    <module>memind-plugin-ai-spring-ai</module>
    <module>memind-plugin-content-parser-document-tika</module>
    <module>memind-plugin-jdbc</module>
    <module>memind-plugin-rawdatas</module>
    <module>memind-plugin-spring-boot-starters</module>
    <module>memind-plugin-tracing-opentelemetry</module>
</modules>
```

Create the new document module POM with `memind-core` and test dependencies only. Keep Tika-specific dependencies for Task 2 when the parser implementation is relocated:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.openmemind.ai</groupId>
        <artifactId>memind-plugin-rawdatas</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>memind-plugin-rawdata-document</artifactId>
    <name>Memind - Document RawData Plugin</name>

    <dependencies>
        <dependency>
            <groupId>com.openmemind.ai</groupId>
            <artifactId>memind-core</artifactId>
            <version>${revision}</version>
        </dependency>
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
    </dependencies>
</project>
```

- [ ] **Step 4: Create the plugin-owned public document API skeleton**

Create `DocumentContent`, `DocumentSection`, `DocumentRawContentTypeRegistrar`, `DocumentRawDataPlugin`, and `DocumentExtractionRequests` in the new plugin package. In this task, keep `DocumentRawDataPlugin.processors(...)` and `.parsers(...)` empty and use the plugin only for the stable plugin ID, document subtype registration, and request-factory migration path:

```java
package com.openmemind.ai.memory.plugin.rawdata.document.plugin;

public final class DocumentRawDataPlugin implements RawDataPlugin {
    @Override
    public String pluginId() {
        return "rawdata-document";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of();
    }

    @Override
    public List<ContentParser> parsers(RawDataPluginContext context) {
        return List.of();
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new DocumentRawContentTypeRegistrar());
    }
}
```

Do **not** move `DocumentExtractionOptions` into the plugin module in this task. Leave the current core record unchanged and only adjust relocated document classes to import the core type from `memind-core`.

Keep `DocumentContent`, `DocumentSection`, and `DocumentExtractionRequests` behavior identical to the current core public API. Do not relocate parser, chunker, processor, or Tika support classes in this task.

- [ ] **Step 5: Re-run the new module tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document -Dtest=DocumentRawDataPluginTest,DocumentExtractionRequestsTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add memind-plugins/pom.xml \
        memind-plugins/memind-plugin-rawdatas/pom.xml \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document
git commit -m "feat(plugin): scaffold rawdata document plugin module"
```

---

### Task 2: Relocate the document implementation into the plugin module while core still owns runtime compatibility

**Files:**
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/pom.xml`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/chunk/ProfileAwareDocumentChunker.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/processor/DocumentContentProcessor.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/NativeTextDocumentContentParser.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/HtmlTextExtractor.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentContentParser.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentMetadataMapper.java`
- Create: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser/tika/TikaDocumentParserSupport.java`
- Modify: `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin/DocumentRawDataPlugin.java`
- Create: relocated document parser/processor tests in the plugin module

- [ ] **Step 1: Write the failing plugin-behavior tests**

Extend the document plugin tests so the new module is validated as a self-contained builder-path unit:

```java
@Test
void pluginExposesDocumentProcessorAndParsers() {
    RawDataPlugin plugin = new DocumentRawDataPlugin();
    RawDataPluginContext context =
            new RawDataPluginContext(
                    Mockito.mock(ChatClientRegistry.class),
                    PromptRegistry.EMPTY,
                    MemoryBuildOptions.defaults());

    assertThat(plugin.processors(context))
            .singleElement()
            .isInstanceOf(DocumentContentProcessor.class);
    assertThat(plugin.parsers(context))
            .extracting(ContentParser::parserId)
            .containsExactly("document-native-text", "document-tika");
}
```

- [ ] **Step 2: Run the plugin tests and verify they fail**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document -Dtest=DocumentRawDataPluginTest,DocumentExtractionRequestsTest,NativeTextDocumentContentParserTest,TikaDocumentContentParserTest,DocumentContentProcessorTest test
```

Expected: FAIL because the parser and processor classes do not exist yet and `DocumentRawDataPlugin` still returns empty processor/parser lists from Task 1.

- [ ] **Step 3: Implement the plugin-owned document stack**

First update the plugin module POM to add the relocated parser dependencies:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <scope>test</scope>
</dependency>
```

Then relocate the document processor, chunker, native parser, and Tika support classes into the new plugin package. Keep `DocumentExtractionOptions` in core and read it through `RawDataPluginContext` by extending the Task 1 plugin skeleton:

```java
public final class DocumentRawDataPlugin implements RawDataPlugin {
    @Override
    public String pluginId() {
        return "rawdata-document";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of(
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(),
                        context.buildOptions().extraction().rawdata().document()));
    }

    @Override
    public List<ContentParser> parsers(RawDataPluginContext context) {
        return List.of(new NativeTextDocumentContentParser(), new TikaDocumentContentParser());
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new DocumentRawContentTypeRegistrar());
    }
}
```

Keep `DocumentExtractionRequests.document(...)` as the explicit plugin-owned convenience factory that delegates to `ExtractionRequest.of(...)`.

- [ ] **Step 4: Re-run the plugin tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/content \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/chunk \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/processor \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/parser \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/support \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/plugin \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/support \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/parser \
        memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/processor
git commit -m "feat(plugin): relocate document rawdata implementation"
```

---

### Task 3: Make `RawContentJackson` authoritative and remove `@JsonSubTypes` from `RawContent`

**Files:**
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java`
- Modify: document/image/audio/toolcall content round-trip tests
- Create: `memind-core/src/test/java/com/openmemind/ai/memory/core/utils/JsonUtilsRawContentTest.java`

- [ ] **Step 1: Write the failing mapper-authority tests**

```java
class JsonUtilsRawContentTest {

    @Test
    void jsonUtilsCanRoundTripBuiltinRawContentWithoutJsonSubTypes() {
        RawContent content = ConversationContent.builder().addUserMessage("hello").build();

        String json = JsonUtils.toJson(content);
        RawContent decoded = JsonUtils.fromJson(json, RawContent.class);

        assertThat(decoded).isInstanceOf(ConversationContent.class);
    }
}
```

In the document plugin add a second failing test:

```java
@Test
void rawContentJacksonRegistersDocumentSubtypeWithoutJsonSubTypes() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    RawContentJackson.registerAll(mapper, List.of(new DocumentRawContentTypeRegistrar()));

    RawContent decoded =
            mapper.readValue(
                    "{\"type\":\"document\",\"title\":\"Guide\",\"mimeType\":\"text/plain\",\"parsedText\":\"hello\",\"sections\":[],\"metadata\":{}}",
                    RawContent.class);

    assertThat(decoded).isInstanceOf(DocumentContent.class);
}
```

- [ ] **Step 2: Run the targeted mapper tests and verify they fail**

Run:

```bash
mvn -pl memind-core -Dtest=JsonUtilsRawContentTest,RawContentJacksonTest test
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document -Dtest=DocumentRawDataPluginTest test
```

Expected: FAIL once `@JsonSubTypes` is removed or while `JsonUtils` still only knows about conversation.

- [ ] **Step 3: Remove annotation-driven subtype registration and route everything through registrars**

Update `RawContent.java`:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public abstract class RawContent {}
```

Keep `CoreBuiltinRawDataPlugin.typeRegistrars()` registering the current in-core document implementation until the later cutover task:

```java
@Override
public List<RawContentTypeRegistrar> typeRegistrars() {
    return List.of(
            () -> Map.of("tool_call", ToolCallContent.class),
            () -> Map.of("document", DocumentContent.class),
            () -> Map.of("image", ImageContent.class),
            () -> Map.of("audio", AudioContent.class));
}
```

Update `JsonUtils` so its mapper is built with all current builtin registrars rather than annotation side effects:

```java
private static ObjectMapper createMapper() {
    ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    RawContentJackson.registerAll(mapper, new CoreBuiltinRawDataPlugin().typeRegistrars());
    return mapper;
}
```

- [ ] **Step 4: Re-run the mapper tests**

Run:

```bash
mvn -pl memind-core -Dtest=JsonUtilsRawContentTest,RawContentJacksonTest test
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document -Dtest=DocumentRawDataPluginTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/RawContent.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJackson.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/utils/JsonUtils.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/utils/JsonUtilsRawContentTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/rawdata/RawContentJacksonTest.java
git commit -m "refactor(core): make raw content jackson authoritative"
```

---

### Task 4: Perform the atomic document runtime cutover, Spring JSON wiring, and legacy Tika bridge cutover

**Files:**
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/pom.xml`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/jackson/autoconfigure/RawDataJacksonAutoConfiguration.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter/src/test/java/com/openmemind/ai/memory/plugin/rawdata/jackson/autoconfigure/RawDataJacksonAutoConfigurationTest.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/pom.xml`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentRawDataAutoConfiguration.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentNativeTextParserAutoConfiguration.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentTikaParserAutoConfiguration.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentRawDataProperties.java`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter/src/test/java/com/openmemind/ai/memory/plugin/rawdata/document/autoconfigure/DocumentRawDataAutoConfigurationTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/pom.xml`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java`
- Modify: `memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java`
- Modify: `memind-server/pom.xml`
- Modify: `memind-server/src/main/java/com/openmemind/ai/memory/server/configuration/MemindServerRuntimeConfiguration.java`
- Modify: `memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerApplicationTest.java`
- Modify: `memind-server/src/test/java/com/openmemind/ai/memory/server/configuration/MemindServerRuntimeConfigurationTest.java`
- Modify: `memind-plugins/memind-plugin-content-parser-document-tika/pom.xml`
- Modify: `memind-plugins/memind-plugin-content-parser-document-tika/src/main/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/TikaDocumentContentParser.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/pom.xml`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/main/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/autoconfigure/TikaDocumentParserAutoConfiguration.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/main/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/autoconfigure/TikaDocumentParserProperties.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter/src/test/java/com/openmemind/ai/memory/plugin/content/parser/document/tika/autoconfigure/TikaDocumentParserAutoConfigurationTest.java`
- Modify: document-related core tests
- Delete: core document content/processor/parser files listed in the plan header

- [ ] **Step 1: Write the failing cutover tests**

Add one shared-starter test and one document-starter composition test. The shared starter must prove builtin rawdata registration works without any document starter on the classpath and that Spring `RawDataPlugin` beans contribute additional subtypes automatically:

```java
class RawDataJacksonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(
                    AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            RawDataJacksonAutoConfiguration.class));

    @Test
    void registersBuiltinRawContentTypesWithoutPluginStarters() throws Exception {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            "{\"type\":\"image\",\"mimeType\":\"image/png\",\"description\":\"cover\",\"metadata\":{}}",
                                            RawContent.class))
                            .isInstanceOf(ImageContent.class);

                    assertThat(
                                    mapper.readValue(
                                            "{\"type\":\"audio\",\"mimeType\":\"audio/mpeg\",\"transcript\":\"hello\",\"segments\":[],\"metadata\":{}}",
                                            RawContent.class))
                            .isInstanceOf(AudioContent.class);

                    assertThat(
                                    mapper.readValue(
                                            "{\"type\":\"conversation\",\"messages\":[{\"role\":\"USER\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"timestamp\":null,\"userName\":null}]}",
                                            RawContent.class))
                            .isInstanceOf(ConversationContent.class);

                    assertThat(
                                    mapper.readValue(
                                            "{\"type\":\"tool_call\",\"calls\":[{\"toolName\":\"search\",\"input\":\"{}\",\"output\":\"ok\",\"status\":\"SUCCESS\",\"durationMs\":1,\"inputTokens\":1,\"outputTokens\":1,\"contentHash\":\"abc\",\"calledAt\":\"2026-04-11T00:00:00Z\"}]}",
                                            RawContent.class))
                            .isInstanceOf(ToolCallContent.class);
                });
    }

    @Test
    void picksUpPluginSubtypeRegistrarsFromSpringContext() throws Exception {
        contextRunner
                .withBean(RawDataPlugin.class, TestRawDataPlugin::new)
                .run(
                        context -> {
                            ObjectMapper mapper = context.getBean(ObjectMapper.class);

                            assertThat(
                                            mapper.readValue(
                                                    "{\"type\":\"test_raw\",\"text\":\"hello\",\"metadata\":{}}",
                                                    RawContent.class))
                                    .isInstanceOf(TestRawContent.class);
                        });
    }
}
```

Implement `TestRawDataPlugin` and `TestRawContent` as nested private fixtures in the new test file using the same single-type registrar pattern already used by `RawDataPluginAssemblyTest`.

The document starter test should now verify composition with the shared rawdata Jackson starter rather than owning application-level Jackson behavior itself:

```java
class DocumentRawDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(
                    AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            RawDataJacksonAutoConfiguration.class,
                            DocumentRawDataAutoConfiguration.class,
                            DocumentNativeTextParserAutoConfiguration.class,
                            DocumentTikaParserAutoConfiguration.class));

    @Test
    void registersDocumentRawDataPluginAndContentParsers() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
                    assertThat(context.getBeansOfType(ContentParser.class))
                            .containsKeys("documentNativeTextContentParser", "tikaDocumentContentParser");
                });
    }

    @Test
    void applicationObjectMapperCanDeserializeDocumentRawContentWhenSharedJacksonStarterPresent()
            throws Exception {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            "{\"type\":\"document\",\"title\":\"Guide\",\"mimeType\":\"text/plain\",\"parsedText\":\"hello\",\"sections\":[],\"metadata\":{}}",
                                            RawContent.class))
                            .isInstanceOf(DocumentContent.class);
                });
    }

    @Test
    void canDisableNativeTextParserOnly() {
        contextRunner
                .withPropertyValues("memind.rawdata.document.native-text-enabled=false")
                .run(
                        context ->
                                assertThat(context.getBeansOfType(ContentParser.class))
                                        .containsKey("tikaDocumentContentParser")
                                        .doesNotContainKey("documentNativeTextContentParser"));
    }

    @Test
    void canDisableTikaParserOnly() {
        contextRunner
                .withPropertyValues("memind.rawdata.document.tika-enabled=false")
                .run(
                        context ->
                                assertThat(context.getBeansOfType(ContentParser.class))
                                        .containsKey("documentNativeTextContentParser")
                                        .doesNotContainKey("tikaDocumentContentParser"));
    }

    @Test
    void backsOffCompletelyWhenDocumentStarterDisabled() {
        contextRunner
                .withPropertyValues("memind.rawdata.document.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RawDataPlugin.class);
                            assertThat(context.getBeansOfType(ContentParser.class)).isEmpty();
                        });
    }
}
```

Update `TikaDocumentParserAutoConfigurationTest` so it composes the shared rawdata Jackson starter plus the document plugin auto-configuration and then captures the exact compatibility contract for the legacy bridge:

```java
private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        RawDataJacksonAutoConfiguration.class,
                        DocumentRawDataAutoConfiguration.class,
                        TikaDocumentParserAutoConfiguration.class));

@Test
void legacyStarterKeepsHistoricalPropertyAndBeanName() {
    contextRunner.run(
            context -> {
                assertThat(context).hasSingleBean(RawDataPlugin.class);
                assertThat(context.getBeansOfType(ContentParser.class))
                        .containsKey("tikaDocumentContentParser")
                        .doesNotContainKey("documentNativeTextContentParser");
            });
}

@Test
void legacyStarterStillHonorsOldDisableProperty() {
    contextRunner
            .withPropertyValues("memind.parser.document.tika.enabled=false")
            .run(
                    context -> {
                        assertThat(context).doesNotHaveBean(RawDataPlugin.class);
                        assertThat(context.getBeansOfType(ContentParser.class)).isEmpty();
                    });
}
```

Update the server runtime test so it verifies plugin forwarding rather than implicit core parser injection:

```java
@Test
void runtimeFactoryForwardsRawDataPluginsAlongsideContentParsers() {
    var plugin = new TestRawDataPlugin();
    var parser = new TestContentParser();
    var configuration = new MemindServerRuntimeConfiguration();

    MemoryRuntimeFactory factory =
            configuration.memoryRuntimeFactory(
                    provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                    provider(MemoryStore.class, memoryStore()),
                    provider(MemoryBuffer.class, memoryBuffer()),
                    provider(MemoryVector.class, proxy(MemoryVector.class)),
                    emptyProvider(MemoryTextSearch.class),
                    provider(Reranker.class, new NoopReranker()),
                    provider(ContentParser.class, parser),
                    provider(RawDataPlugin.class, plugin),
                    emptyProvider(ResourceFetcher.class));

    Memory memory = factory.create(MemoryBuildOptions.defaults());
    var extractor = readField((DefaultMemory) memory, "extractor", MemoryExtractor.class);

    RawContentProcessorRegistry registry =
            readField(extractor, "rawContentProcessorRegistry", RawContentProcessorRegistry.class);

    assertThat(registry.resolve(new TestRawContent())).isInstanceOf(TestRawContentProcessor.class);
}
```

Update the real Spring Boot server test so it proves the application context now contributes a plugin-aware MVC `ObjectMapper`, not only a runtime factory. Reuse the existing `MemindServerApplicationTest` harness and add one HTTP binding regression that covers both builtin and plugin-owned raw content:

```java
@Test
void extractApiAcceptsBuiltinAndDocumentRawContentViaApplicationObjectMapper() throws Exception {
    mockMvc.perform(
                    post("/open/v1/memory/extract")
                            .contentType(APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "userId": "u1",
                                      "agentId": "a1",
                                      "rawContent": {
                                        "type": "conversation",
                                        "messages": [
                                          {
                                            "role": "USER",
                                            "content": [
                                              {
                                                "type": "text",
                                                "text": "hello"
                                              }
                                            ],
                                            "timestamp": "2026-03-31T10:00:00Z"
                                          }
                                        ]
                                      }
                                    }
                                    """))
            .andExpect(status().isOk());

    mockMvc.perform(
                    post("/open/v1/memory/extract")
                            .contentType(APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "userId": "u1",
                                      "agentId": "a1",
                                      "rawContent": {
                                        "type": "document",
                                        "title": "Guide",
                                        "mimeType": "text/plain",
                                        "parsedText": "hello",
                                        "sections": [],
                                        "metadata": {}
                                      }
                                    }
                                    """))
            .andExpect(status().isOk());
}
```

Update the core extraction/builder tests so they expect document support only when the plugin is explicitly registered.

- [ ] **Step 2: Run the cutover tests and verify they fail**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter -Dtest=RawDataJacksonAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter -Dtest=DocumentRawDataAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter -Dtest=TikaDocumentParserAutoConfigurationTest test
mvn -pl memind-server -Dtest=MemindServerApplicationTest,MemindServerRuntimeConfigurationTest test
mvn -pl memind-core -Dtest=ExtractionRequestTest,MemoryExtractorMultimodalFileTest,MemoryAssemblersTest,RawDataPluginAssemblyTest test
```

Expected: FAIL because the shared rawdata Jackson starter and document starter modules do not exist yet, `memind-server` does not yet depend on the new document starter, the server runtime does not yet accept `RawDataPlugin` beans, the application `ObjectMapper` is not yet registering all active rawdata subtypes, and the legacy Tika artifacts still point at core-owned document classes.

- [ ] **Step 3: Execute the runtime cutover in one atomic change**

First create the shared rawdata Jackson starter and the document starter modules, then register both in `memind-plugin-spring-boot-starters/pom.xml`:

```xml
<module>memind-plugin-rawdata-jackson-starter</module>
<module>memind-plugin-rawdata-document-starter</module>
```

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.openmemind.ai</groupId>
        <artifactId>memind-plugin-spring-boot-starters</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>memind-plugin-rawdata-jackson-starter</artifactId>
    <name>Memind - RawData Jackson Starter</name>

    <dependencies>
        <dependency>
            <groupId>com.openmemind.ai</groupId>
            <artifactId>memind-core</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Create the document starter POM so it depends on both the document plugin and the shared rawdata Jackson starter:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.openmemind.ai</groupId>
        <artifactId>memind-plugin-spring-boot-starters</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>memind-plugin-rawdata-document-starter</artifactId>
    <name>Memind - Document RawData Starter</name>

    <dependencies>
        <dependency>
            <groupId>com.openmemind.ai</groupId>
            <artifactId>memind-plugin-rawdata-jackson-starter</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>com.openmemind.ai</groupId>
            <artifactId>memind-plugin-rawdata-document</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Update `memind-server/pom.xml` so the default server distribution continues to support document extraction and inherits the shared MVC `RawContent` binding path transitively through the new document starter:

```xml
<dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-rawdata-document-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

Create `RawDataJacksonAutoConfiguration` as the only application-level raw-content Jackson Spring bridge. The customizer must register builtin rawdata types plus every `RawDataPlugin.typeRegistrars()` contribution visible in the Spring context so the application `ObjectMapper` stays aligned with runtime assembly:

```java
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(RawContent.class)
public class RawDataJacksonAutoConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer rawDataJacksonCustomizer(
            ObjectProvider<RawDataPlugin> rawDataPluginProvider) {
        return builder ->
                builder.postConfigurer(
                        mapper -> {
                            List<RawContentTypeRegistrar> registrars =
                                    new ArrayList<>(new CoreBuiltinRawDataPlugin().typeRegistrars());
                            rawDataPluginProvider
                                    .orderedStream()
                                    .forEach(plugin -> registrars.addAll(plugin.typeRegistrars()));
                            RawContentJackson.registerAll(mapper, registrars);
                        });
    }
}
```

Create `DocumentRawDataAutoConfiguration` as the Spring bridge for the document plugin only. Application-level raw-content Jackson registration must stay in `RawDataJacksonAutoConfiguration` so future image/audio/toolcall plugin starters can reuse the same path:

```java
@AutoConfiguration
@ConditionalOnClass(DocumentRawDataPlugin.class)
@EnableConfigurationProperties(DocumentRawDataProperties.class)
@ConditionalOnProperty(
        prefix = "memind.rawdata.document",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DocumentRawDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "documentRawDataPlugin")
    RawDataPlugin documentRawDataPlugin() {
        return new DocumentRawDataPlugin();
    }
}
```

Create parser-specific auto-configuration classes so parser exposure is independently controllable and the legacy Tika bridge can import only the runtime pieces it needs:

```java
@AutoConfiguration
@ConditionalOnClass(NativeTextDocumentContentParser.class)
@EnableConfigurationProperties(DocumentRawDataProperties.class)
@ConditionalOnProperty(
        prefix = "memind.rawdata.document",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "memind.rawdata.document",
        name = "native-text-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DocumentNativeTextParserAutoConfiguration {

    @Bean("documentNativeTextContentParser")
    @ConditionalOnMissingBean(name = "documentNativeTextContentParser")
    ContentParser documentNativeTextContentParser() {
        return new NativeTextDocumentContentParser();
    }
}
```

```java
@AutoConfiguration
@ConditionalOnClass(TikaDocumentContentParser.class)
@EnableConfigurationProperties(DocumentRawDataProperties.class)
@ConditionalOnProperty(
        prefix = "memind.rawdata.document",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "memind.rawdata.document",
        name = "tika-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DocumentTikaParserAutoConfiguration {

    @Bean("tikaDocumentContentParser")
    @ConditionalOnMissingBean(name = "tikaDocumentContentParser")
    ContentParser tikaDocumentContentParser() {
        return new TikaDocumentContentParser();
    }
}
```

Keep `DocumentRawDataProperties` limited to bean enablement:

```java
@ConfigurationProperties(prefix = "memind.rawdata.document")
public class DocumentRawDataProperties {

    private boolean enabled = true;
    private boolean nativeTextEnabled = true;
    private boolean tikaEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNativeTextEnabled() {
        return nativeTextEnabled;
    }

    public void setNativeTextEnabled(boolean nativeTextEnabled) {
        this.nativeTextEnabled = nativeTextEnabled;
    }

    public boolean isTikaEnabled() {
        return tikaEnabled;
    }

    public void setTikaEnabled(boolean tikaEnabled) {
        this.tikaEnabled = tikaEnabled;
    }
}
```

Register `RawDataJacksonAutoConfiguration` in the shared starter `AutoConfiguration.imports`, then register the three document auto-configurations in the document starter `AutoConfiguration.imports`.

At the same time, remove document ownership from core and switch the runtime wiring:

```java
// CoreBuiltinRawDataPlugin
@Override
public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
    var rawdata = context.buildOptions().extraction().rawdata();
    return List.of(
            new ToolCallContentProcessor(
                    new ToolCallChunker(rawdata.toolCall()),
                    new ToolCallCaptionGenerator(),
                    new LlmToolCallItemExtractionStrategy(
                            context.chatClientRegistry().resolve(ChatClientSlot.TOOL_CALL_EXTRACTION),
                            context.promptRegistry())),
            new ImageContentProcessor(new ImageSegmentComposer(), rawdata.image()),
            new AudioContentProcessor(new TranscriptSegmentChunker(), rawdata.audio()));
}

@Override
public List<RawContentTypeRegistrar> typeRegistrars() {
    return List.of(
            () -> Map.of("tool_call", ToolCallContent.class),
            () -> Map.of("image", ImageContent.class),
            () -> Map.of("audio", AudioContent.class));
}
```

```java
// MemoryExtractor
private RawContentProcessorRegistry createBuiltinProcessorRegistry(
        RawDataExtractionOptions options) {
    return new RawContentProcessorRegistry(
            List.of(
                    new ImageContentProcessor(new ImageSegmentComposer(), options.image()),
                    new AudioContentProcessor(new TranscriptSegmentChunker(), options.audio())));
}
```

Update `MemindServerRuntimeConfiguration` with these exact changes:

```java
MemoryRuntimeFactory memoryRuntimeFactory(
        ObjectProvider<StructuredChatClient> structuredChatClientProvider,
        ObjectProvider<MemoryStore> memoryStoreProvider,
        ObjectProvider<MemoryBuffer> memoryBufferProvider,
        ObjectProvider<MemoryVector> memoryVectorProvider,
        ObjectProvider<MemoryTextSearch> memoryTextSearch,
        ObjectProvider<Reranker> reranker,
        ObjectProvider<ContentParser> contentParserProvider,
        ObjectProvider<RawDataPlugin> rawDataPluginProvider,
        ObjectProvider<ResourceFetcher> resourceFetcherProvider)
```

```java
private static List<ContentParser> resolveContentParsers(
        ObjectProvider<ContentParser> contentParserProvider) {
    return List.copyOf(contentParserProvider.orderedStream().toList());
}
```

```java
rawDataPluginProvider.orderedStream().forEach(builder::rawDataPlugin);
```

Convert the legacy Tika module and starter into compile-safe deprecated bridges in the same task so there is no intermediate broken checkpoint. Update the legacy module POM to keep `memind-core` for SPI contracts but add an explicit dependency on `memind-plugin-rawdata-document`, then turn the old parser class into a delegating wrapper:

```xml
<dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-rawdata-document</artifactId>
    <version>${revision}</version>
</dependency>
```

```java
@Deprecated(forRemoval = false)
public final class TikaDocumentContentParser implements ContentParser {

    private final com.openmemind.ai.memory.plugin.rawdata.document.parser.tika.TikaDocumentContentParser delegate =
            new com.openmemind.ai.memory.plugin.rawdata.document.parser.tika.TikaDocumentContentParser();

    @Override
    public String parserId() {
        return delegate.parserId();
    }

    @Override
    public String contentType() {
        return delegate.contentType();
    }

    @Override
    public String contentProfile() {
        return delegate.contentProfile();
    }

    @Override
    public int priority() {
        return delegate.priority();
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return delegate.supportedMimeTypes();
    }

    @Override
    public Set<String> supportedExtensions() {
        return delegate.supportedExtensions();
    }

    @Override
    public boolean supports(SourceDescriptor source) {
        return delegate.supports(source);
    }

    @Override
    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
        return delegate.parse(data, source);
    }
}
```

Update the legacy starter POM so it depends on `memind-plugin-rawdata-document-starter` as well as the deprecated parser bridge artifact, then keep the old property key and old bean name explicit:

```xml
<dependency>
    <groupId>com.openmemind.ai</groupId>
    <artifactId>memind-plugin-rawdata-document-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

```java
@AutoConfiguration
@ConditionalOnClass(TikaDocumentContentParser.class)
@EnableConfigurationProperties(TikaDocumentParserProperties.class)
@ConditionalOnProperty(
        prefix = "memind.parser.document.tika",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@Import(DocumentRawDataAutoConfiguration.class)
@Deprecated(forRemoval = false)
public class TikaDocumentParserAutoConfiguration {

    @Bean("tikaDocumentContentParser")
    @ConditionalOnMissingBean(name = "tikaDocumentContentParser")
    ContentParser tikaDocumentContentParser() {
        return new TikaDocumentContentParser();
    }
}
```

Also in this step:

- delete the core document content, processor, chunker, and parser classes
- remove `ExtractionRequest.document(...)` from core
- update document-related core tests to use `ExtractionRequest.of(...)` plus explicit plugin registration
- keep `DocumentExtractionOptions` in core and continue routing it through `RawDataPluginContext.buildOptions().extraction().rawdata().document()`

- [ ] **Step 4: Re-run the cutover tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter -Dtest=RawDataJacksonAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter -Dtest=DocumentRawDataAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter -Dtest=TikaDocumentParserAutoConfigurationTest test
mvn -pl memind-server -Dtest=MemindServerApplicationTest,MemindServerRuntimeConfigurationTest test
mvn -pl memind-core -Dtest=ExtractionRequestTest,MemoryExtractorMultimodalFileTest,MemoryAssemblersTest,RawDataPluginAssemblyTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-plugins/memind-plugin-spring-boot-starters/pom.xml \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter \
        memind-core/src/main/java/com/openmemind/ai/memory/core/plugin/CoreBuiltinRawDataPlugin.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/MemoryExtractor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/ExtractionRequest.java \
        memind-server/pom.xml \
        memind-server/src/main/java/com/openmemind/ai/memory/server/configuration/MemindServerRuntimeConfiguration.java \
        memind-server/src/test/java/com/openmemind/ai/memory/server/MemindServerApplicationTest.java \
        memind-server/src/test/java/com/openmemind/ai/memory/server/configuration/MemindServerRuntimeConfigurationTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/ExtractionRequestTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/extraction/MemoryExtractorMultimodalFileTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/MemoryAssemblersTest.java \
        memind-core/src/test/java/com/openmemind/ai/memory/core/builder/RawDataPluginAssemblyTest.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/DocumentContent.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/content/document/DocumentSection.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/processor/DocumentContentProcessor.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/extraction/rawdata/chunk/ProfileAwareDocumentChunker.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/resource/NativeTextDocumentContentParser.java \
        memind-core/src/main/java/com/openmemind/ai/memory/core/resource/HtmlTextExtractor.java \
        memind-plugins/memind-plugin-content-parser-document-tika \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter
git commit -m "refactor(plugin): cut over document rawdata runtime to plugin"
```

---

### Task 5: Align store JSON infrastructure with the managed ObjectMapper path

**Files:**
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodec.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java`
- Modify: `memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodecTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/main/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfiguration.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/test/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfigurationSqliteTest.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryMybatisPlusAutoConfiguration.java`
- Modify: `memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryStoreAutoConfigurationTest.java`

- [ ] **Step 1: Write the failing persistence mapper tests**

Keep `JsonCodecTest` focused on baseline parity and add starter-level regressions that prove user-provided `ObjectMapper` beans are reused end-to-end:

```java
@Test
void createDefaultObjectMapperStartsFromJsonUtilsBaseline() throws Exception {
    ObjectMapper mapper = JsonCodec.createDefaultObjectMapper();

    assertThat(mapper).isNotSameAs(JsonUtils.mapper());
    assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    assertThat(mapper.writeValueAsString(new SamplePayload("alpha", Instant.parse("2026-03-22T00:00:00Z"))))
            .contains("2026-03-22T00:00:00Z");
}
```

```java
@Test
void reusesUserProvidedObjectMapperForSqliteMemoryStore() {
    ObjectMapper customMapper = JsonUtils.mapper().copy();

    contextRunner
            .withUserConfiguration(SqliteDataSourceConfig.class)
            .withBean(ObjectMapper.class, () -> customMapper)
            .run(
                    context -> {
                        SqliteMemoryStore store =
                                (SqliteMemoryStore) context.getBean(MemoryStore.class).rawDataOperations();
                        JsonCodec codec =
                                (JsonCodec) ReflectionTestUtils.getField(store, "jsonHelper");

                        assertThat(ReflectionTestUtils.getField(codec, "objectMapper"))
                                .isSameAs(customMapper);
                    });
}
```

```java
@Test
void reusesUserProvidedObjectMapperForJacksonTypeHandler() {
    ObjectMapper original = JacksonTypeHandler.getObjectMapper();
    ObjectMapper customMapper = JsonUtils.mapper().copy();

    try {
        newContextRunner()
                .withUserConfiguration(ExistingDataSourceConfig.class)
                .withBean(ObjectMapper.class, () -> customMapper)
                .run(context -> assertThat(JacksonTypeHandler.getObjectMapper()).isSameAs(customMapper));
    } finally {
        JacksonTypeHandler.setObjectMapper(original);
    }
}
```

- [ ] **Step 2: Run the persistence mapper tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-jdbc -Dtest=JsonCodecTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter -Dtest=JdbcPluginAutoConfigurationSqliteTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter -Dtest=MemoryStoreAutoConfigurationTest test
```

Expected: FAIL because `JsonCodec` still builds its own mapper baseline, the JDBC starter ignores any user-provided `ObjectMapper`, and the MyBatis auto-configuration still overwrites `JacksonTypeHandler` with a bare static mapper.

- [ ] **Step 3: Align the default and injected mapper paths**

Update `JsonCodec` so its default mapper delegates to `JsonUtils.mapper().copy()`:

```java
public static ObjectMapper createDefaultObjectMapper() {
    return JsonUtils.mapper().copy();
}
```

Expose the mapper-accepting store constructors so the JDBC starter can pass a managed mapper, then thread `ObjectProvider<ObjectMapper>` through the JDBC auto-configuration:

```java
public MemoryStore memoryStore(
        DataSource dataSource,
        Environment environment,
        ObjectProvider<ResourceStore> resourceStoreProvider,
        ObjectProvider<ObjectMapper> objectMapperProvider) {
    boolean createIfNotExist =
            environment.getProperty("memind.store.init-schema", Boolean.class, true);
    ResourceStore resourceStore = resourceStoreProvider.getIfAvailable();
    ObjectMapper objectMapper =
            objectMapperProvider.getIfAvailable(JsonCodec::createDefaultObjectMapper);
    return switch (detectDialect(dataSource)) {
        case SQLITE -> new SqliteMemoryStore(dataSource, resourceStore, objectMapper, createIfNotExist);
        case MYSQL -> new MysqlMemoryStore(dataSource, resourceStore, objectMapper, createIfNotExist);
        case POSTGRESQL ->
                new PostgresqlMemoryStore(dataSource, resourceStore, objectMapper, createIfNotExist);
    };
}
```

Split the MyBatis static block so SQL parser cache setup remains static, but `JacksonTypeHandler.setObjectMapper(...)` is driven from the Spring auto-configuration constructor using the user bean when present and `JsonUtils.mapper().copy()` otherwise:

```java
public MemoryMybatisPlusAutoConfiguration(ObjectProvider<ObjectMapper> objectMapperProvider) {
    JacksonTypeHandler.setObjectMapper(
            objectMapperProvider.getIfAvailable(() -> JsonUtils.mapper().copy()));
}
```

- [ ] **Step 4: Re-run the persistence mapper tests**

Run:

```bash
mvn -pl memind-plugins/memind-plugin-jdbc -Dtest=JsonCodecTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter -Dtest=JdbcPluginAutoConfigurationSqliteTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter -Dtest=MemoryStoreAutoConfigurationTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodec.java \
        memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/mysql/MysqlMemoryStore.java \
        memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/sqlite/SqliteMemoryStore.java \
        memind-plugins/memind-plugin-jdbc/src/main/java/com/openmemind/ai/memory/plugin/jdbc/postgresql/PostgresqlMemoryStore.java \
        memind-plugins/memind-plugin-jdbc/src/test/java/com/openmemind/ai/memory/plugin/jdbc/internal/support/JsonCodecTest.java \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/main/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfiguration.java \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter/src/test/java/com/openmemind/ai/memory/plugin/jdbc/autoconfigure/JdbcPluginAutoConfigurationSqliteTest.java \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/main/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryMybatisPlusAutoConfiguration.java \
        memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter/src/test/java/com/openmemind/ai/memory/plugin/store/mybatis/MemoryStoreAutoConfigurationTest.java
git commit -m "chore(store): align managed object mapper wiring"
```

---

### Task 6: Full regression and Phase 2A exit criteria

**Files:**
- Review all files touched in Tasks 1-5

- [ ] **Step 1: Run focused regression for the moved document stack**

Run:

```bash
mvn -pl memind-core -Dtest=RawDataPluginAssemblyTest,MemoryAssemblersTest,MemoryExtractorMultimodalFileTest test
mvn -pl memind-server -Dtest=MemindServerApplicationTest,MemindServerRuntimeConfigurationTest test
mvn -pl memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-document test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-jackson-starter -Dtest=RawDataJacksonAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-rawdata-document-starter -Dtest=DocumentRawDataAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-jdbc -Dtest=JsonCodecTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-jdbc-starter -Dtest=JdbcPluginAutoConfigurationSqliteTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-mybatis-plus-starter -Dtest=MemoryStoreAutoConfigurationTest test
mvn -pl memind-plugins/memind-plugin-spring-boot-starters/memind-plugin-content-parser-document-tika-starter -Dtest=TikaDocumentParserAutoConfigurationTest test
```

Expected: PASS

- [ ] **Step 2: Run full project verification**

Run:

```bash
mvn clean verify -DskipTests=false
```

Expected: PASS

- [ ] **Step 3: Verify the Phase 2A exit criteria**

Confirm all of the following are true:

- `RawContent` no longer uses `@JsonSubTypes`
- `RawContentJackson` plus registrars is the only subtype-registration mechanism for `RawContent`
- builder-based runtimes can assemble document extraction only when `DocumentRawDataPlugin` is registered
- `memind-plugin-rawdata-jackson-starter` is the only Spring module that customizes the application `ObjectMapper` for `RawContent`, and document starter only contributes document beans
- Spring runtimes can assemble document extraction through `RawDataPlugin` beans and the shared rawdata Jackson starter, not parser beans alone
- `memind-server` depends on `memind-plugin-rawdata-document-starter`, so the default server distribution keeps document extraction and MVC raw-content binding after the core cutover
- the `/open/v1/memory/extract` HTTP endpoint accepts both builtin `conversation` raw content and plugin-owned `document` raw content in a real Spring Boot application context
- the application `ObjectMapper` can deserialize both builtin raw content (`image`, `audio`, `tool_call`, `conversation`) and plugin-owned `document` content after the cutover
- `memind-plugin-rawdata-document` owns `DocumentContent`, `DocumentSection`, `DocumentContentProcessor`, `ProfileAwareDocumentChunker`, `NativeTextDocumentContentParser`, `HtmlTextExtractor`, and Tika parser support
- `memind-core` no longer contains document implementation classes except the transitional `DocumentExtractionOptions` contract kept for Phase 2A
- `ExtractionRequest.document(...)` is gone from core and the migration path is `DocumentExtractionRequests.document(...)`
- JDBC and MyBatis reuse a user-provided `ObjectMapper` when present and otherwise start from `JsonUtils.mapper().copy()`
- the legacy Tika artifact and starter remain only as compatibility bridges and no longer own parser logic
- the legacy Tika module depends on `memind-plugin-rawdata-document`, and the legacy starter depends on `memind-plugin-rawdata-document-starter`
- the legacy Tika starter still honors `memind.parser.document.tika.enabled` and still exposes the historical bean name `tikaDocumentContentParser`
- `DocumentExtractionOptions` is still core-owned in this phase and is consumed from `RawDataPluginContext.buildOptions().extraction().rawdata().document()`

- [ ] **Step 4: Commit the regression checkpoint**

```bash
git add memind-core \
        memind-server \
        memind-plugins \
        docs/plans/2026-04-11-rawdata-plugin-extraction-phase2a-document-plugin.md
git commit -m "test: verify rawdata plugin extraction phase 2a"
```

---

## Guardrails

- Do not migrate image, audio, or toolcall implementation files in this plan.
- Do not keep duplicate document business logic in both core and plugin after Task 5; compatibility bridges may exist only at the artifact boundary.
- Do not leave Spring runtime assembly depending only on `ContentParser` discovery once document has moved out of core.
- Do not anchor shared rawdata `ObjectMapper` customization in the document starter; it must live in a common rawdata Spring module that future plugins can reuse.
- Do not let the application `ObjectMapper` register only the document subtype; it must stay aligned with builtin rawdata subtypes as well.
- Do not keep any `ObjectMapper` path that relies on `RawContent` annotations after removing `@JsonSubTypes`.
- Do not describe Task 5 as `RawContent` subtype persistence work; the actual goal is shared JSON baseline and managed `ObjectMapper` reuse.
- Do not remove the legacy Tika artifact or starter entirely in this slice; convert them into thin deprecated bridges first.
- Do not create a checkpoint where core document classes are deleted but the legacy Tika artifact still compiles against them.
- Do not silently drop the legacy Tika starter compatibility contract; preserve both the old property key `memind.parser.document.tika.enabled` and the old bean name `tikaDocumentContentParser`.
- Do not change document parser IDs (`document-native-text`, `document-tika`) or built-in document content profiles in this slice.
- Do not move `DocumentExtractionOptions` out of core in this phase; that requires a separate source-limit SPI follow-up.

---

## Follow-up Plans

- **Phase 2B:** Extract toolcall processing into `memind-plugin-rawdata-toolcall`
- **Phase 2C:** Extract image and audio rawdata stacks into their own plugin modules
- **Phase 2D:** Extract plugin-specific option ownership out of core after introducing a plugin-owned source-limit/config SPI
- **Phase 3 cleanup:** remove the deprecated Tika compatibility bridge artifacts after the new rawdata-document starter is the only supported Spring path
