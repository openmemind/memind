/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql;

import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.util.Map;

/**
 * Batch mutation SQL for transactional item-graph commits.
 */
public final class ItemGraphMutationSqlProvider {

    private static final String JACKSON_TYPE_HANDLER =
            "com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler";
    private static final String INSTANT_TYPE_HANDLER =
            "com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler";

    public String insertMemoryItems(Map<String, Object> params) {
        return """
        <script>
        INSERT INTO memory_item (
            biz_id,
            user_id,
            agent_id,
            memory_id,
            content,
            scope,
            category,
            vector_id,
            raw_data_id,
            content_hash,
            occurred_at,
            occurred_start,
            occurred_end,
            time_granularity,
            observed_at,
            temporal_start,
            temporal_end_or_anchor,
            temporal_anchor,
            metadata,
            type,
            raw_data_type,
            source_client,
            extraction_batch_id,
            created_at,
            updated_at
        ) VALUES
        <foreach collection="items" item="item" separator=",">
            (
                #{item.bizId},
                #{item.userId},
                #{item.agentId},
                #{item.memoryId},
                #{item.content},
                #{item.scope},
                #{item.category},
                #{item.vectorId},
                #{item.rawDataId},
                #{item.contentHash},
                #{item.occurredAt, typeHandler=%1$s},
                #{item.occurredStart, typeHandler=%1$s},
                #{item.occurredEnd, typeHandler=%1$s},
                #{item.timeGranularity},
                #{item.observedAt, typeHandler=%1$s},
                #{item.temporalStart, typeHandler=%1$s},
                #{item.temporalEndOrAnchor, typeHandler=%1$s},
                #{item.temporalAnchor, typeHandler=%1$s},
                #{item.metadata, typeHandler=%2$s},
                #{item.type},
                #{item.rawDataType},
                #{item.sourceClient},
                #{item.extractionBatchId},
                #{item.createdAt, typeHandler=%1$s},
                #{item.updatedAt, typeHandler=%1$s}
            )
        </foreach>
        </script>
        """
                .formatted(INSTANT_TYPE_HANDLER, JACKSON_TYPE_HANDLER);
    }

    public String insertGraphEntities(Map<String, Object> params) {
        return """
        <script>
        INSERT INTO memory_graph_entity (
            user_id,
            agent_id,
            memory_id,
            entity_key,
            display_name,
            entity_type,
            metadata,
            created_at,
            updated_at
        ) VALUES
        <foreach collection="entities" item="entity" separator=",">
            (
                #{entity.userId},
                #{entity.agentId},
                #{entity.memoryId},
                #{entity.entityKey},
                #{entity.displayName},
                #{entity.entityType},
                #{entity.metadata, typeHandler=%1$s},
                #{entity.createdAt, typeHandler=%2$s},
                #{entity.updatedAt, typeHandler=%2$s}
            )
        </foreach>
        </script>
        """
                .formatted(JACKSON_TYPE_HANDLER, INSTANT_TYPE_HANDLER);
    }

    public String insertItemEntityMentions(Map<String, Object> params) {
        return """
        <script>
        INSERT INTO memory_item_entity_mention (
            user_id,
            agent_id,
            memory_id,
            item_id,
            entity_key,
            confidence,
            metadata,
            created_at,
            updated_at
        ) VALUES
        <foreach collection="mentions" item="mention" separator=",">
            (
                #{mention.userId},
                #{mention.agentId},
                #{mention.memoryId},
                #{mention.itemId},
                #{mention.entityKey},
                #{mention.confidence},
                #{mention.metadata, typeHandler=%1$s},
                #{mention.createdAt, typeHandler=%2$s},
                #{mention.updatedAt, typeHandler=%2$s}
            )
        </foreach>
        </script>
        """
                .formatted(JACKSON_TYPE_HANDLER, INSTANT_TYPE_HANDLER);
    }

    public String insertItemLinks(Map<String, Object> params) {
        return """
        <script>
        INSERT INTO memory_item_link (
            user_id,
            agent_id,
            memory_id,
            source_item_id,
            target_item_id,
            link_type,
            relation_code,
            evidence_source,
            strength,
            metadata,
            created_at,
            updated_at
        ) VALUES
        <foreach collection="links" item="link" separator=",">
            (
                #{link.userId},
                #{link.agentId},
                #{link.memoryId},
                #{link.sourceItemId},
                #{link.targetItemId},
                #{link.linkType},
                #{link.relationCode},
                #{link.evidenceSource},
                #{link.strength},
                #{link.metadata, typeHandler=%1$s},
                #{link.createdAt, typeHandler=%2$s},
                #{link.updatedAt, typeHandler=%2$s}
            )
        </foreach>
        </script>
        """
                .formatted(JACKSON_TYPE_HANDLER, INSTANT_TYPE_HANDLER);
    }

    public String insertEntityAliases(Map<String, Object> params) {
        return """
        <script>
        INSERT INTO memory_graph_entity_alias (
            user_id,
            agent_id,
            memory_id,
            entity_key,
            entity_type,
            normalized_alias,
            evidence_count,
            metadata,
            created_at,
            updated_at
        ) VALUES
        <foreach collection="aliases" item="alias" separator=",">
            (
                #{alias.userId},
                #{alias.agentId},
                #{alias.memoryId},
                #{alias.entityKey},
                #{alias.entityType},
                #{alias.normalizedAlias},
                #{alias.evidenceCount},
                #{alias.metadata, typeHandler=%1$s},
                #{alias.createdAt, typeHandler=%2$s},
                #{alias.updatedAt, typeHandler=%2$s}
            )
        </foreach>
        </script>
        """
                .formatted(JACKSON_TYPE_HANDLER, INSTANT_TYPE_HANDLER);
    }

    public String insertAliasBatchReceiptIfAbsent(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        String insertPrefix =
                switch (dialect) {
                    case SQLITE -> "INSERT OR IGNORE INTO";
                    case MYSQL -> "INSERT IGNORE INTO";
                    case POSTGRESQL -> "INSERT INTO";
                };
        String conflictClause =
                dialect == DatabaseDialect.POSTGRESQL
                        ? """
                        ON CONFLICT (memory_id, entity_key, entity_type, normalized_alias, extraction_batch_id)
                        DO NOTHING
                        """
                        : "";
        return """
        <script>
        %1$s memory_graph_alias_batch_receipt (
            user_id,
            agent_id,
            memory_id,
            entity_key,
            entity_type,
            normalized_alias,
            extraction_batch_id,
            created_at,
            updated_at
        ) VALUES (
            #{receipt.userId},
            #{receipt.agentId},
            #{receipt.memoryId},
            #{receipt.entityKey},
            #{receipt.entityType},
            #{receipt.normalizedAlias},
            #{receipt.extractionBatchId},
            #{receipt.createdAt, typeHandler=%2$s},
            #{receipt.updatedAt, typeHandler=%2$s}
        )
        %3$s
        </script>
        """
                .formatted(insertPrefix, INSTANT_TYPE_HANDLER, conflictClause);
    }
}
