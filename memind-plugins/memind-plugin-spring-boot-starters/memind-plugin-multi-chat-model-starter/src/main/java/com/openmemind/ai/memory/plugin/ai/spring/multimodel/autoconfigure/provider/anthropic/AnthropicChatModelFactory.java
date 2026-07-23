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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.anthropic;

import com.anthropic.models.messages.ToolChoice;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiAnthropicChatProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.ProviderPropertyMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicCacheTtl;
import org.springframework.ai.anthropic.AnthropicServiceTier;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicCacheProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;

public final class AnthropicChatModelFactory
        implements MultiAiChatModelFactory<MultiAiAnthropicChatProperties> {

    @Override
    public MultiAiChatModelProviderType providerType() {
        return MultiAiChatModelProviderType.ANTHROPIC;
    }

    @Override
    public ChatModel createChatModel(
            String modelId,
            MultiAiAnthropicChatProperties properties,
            MultiAiModelProviderContext context) {
        AnthropicConnectionProperties connectionProperties = connectionProperties(properties);
        AnthropicChatProperties chatProperties = chatProperties(properties);
        ObservationRegistry observationRegistry = context.observationRegistry();
        return new AnthropicChatAutoConfiguration()
                .anthropicChatModel(
                        connectionProperties,
                        chatProperties,
                        context.toolCallingManager(observationRegistry),
                        context.provider(ObservationRegistry.class),
                        context.provider(MeterRegistry.class),
                        context.provider(ChatModelObservationConvention.class),
                        context.provider(AnthropicHttpClientBuilderCustomizer.class));
    }

    private static AnthropicConnectionProperties connectionProperties(
            MultiAiAnthropicChatProperties properties) {
        AnthropicConnectionProperties target = new AnthropicConnectionProperties();
        ProviderPropertyMapper.copyProperties(properties, target);
        return target;
    }

    private static AnthropicChatProperties chatProperties(
            MultiAiAnthropicChatProperties properties) {
        AnthropicChatProperties target = new AnthropicChatProperties();
        ProviderPropertyMapper.copyProperties(
                properties,
                target,
                "metadata",
                "toolChoice",
                "thinking",
                "outputConfig",
                "webSearchTool",
                "serviceTier",
                "cacheOptions");
        if (properties.getToolChoice() instanceof ToolChoice toolChoice) {
            target.setToolChoice(toolChoice);
        }
        target.setWebSearchTool(webSearchTool(properties.getWebSearchTool()));
        target.setServiceTier(
                ProviderPropertyMapper.convert(
                        properties.getServiceTier(), AnthropicServiceTier.class));
        target.setCacheOptions(cacheOptions(properties.getCacheOptions()));
        return target;
    }

    private static AnthropicWebSearchTool webSearchTool(
            MultiAiAnthropicChatProperties.WebSearchTool source) {
        if (source == null) {
            return null;
        }
        AnthropicWebSearchTool target = new AnthropicWebSearchTool();
        target.setAllowedDomains(source.getAllowedDomains());
        target.setBlockedDomains(source.getBlockedDomains());
        target.setMaxUses(source.getMaxUses());
        target.setUserLocation(userLocation(source.getUserLocation()));
        return target;
    }

    private static AnthropicWebSearchTool.UserLocation userLocation(
            MultiAiAnthropicChatProperties.UserLocation source) {
        if (source == null) {
            return null;
        }
        return new AnthropicWebSearchTool.UserLocation(
                source.getCity(), source.getCountry(), source.getRegion(), source.getTimezone());
    }

    private static AnthropicCacheProperties cacheOptions(
            MultiAiAnthropicChatProperties.CacheOptions source) {
        if (source == null) {
            return null;
        }
        AnthropicCacheProperties target = new AnthropicCacheProperties();
        target.setStrategy(
                ProviderPropertyMapper.convert(source.getStrategy(), AnthropicCacheStrategy.class));
        target.setMessageTypeTtl(messageTypeTtl(source.getMessageTypeTtl()));
        target.setMessageTypeMinContentLengths(
                messageTypeMinContentLengths(source.getMessageTypeMinContentLengths()));
        target.setMultiBlockSystemCaching(source.getMultiBlockSystemCaching());
        return target;
    }

    private static Map<MessageType, AnthropicCacheTtl> messageTypeTtl(Map<String, String> source) {
        if (source == null) {
            return null;
        }
        Map<MessageType, AnthropicCacheTtl> target = new LinkedHashMap<>();
        source.forEach(
                (messageType, ttl) ->
                        target.put(
                                ProviderPropertyMapper.convert(messageType, MessageType.class),
                                ProviderPropertyMapper.convert(ttl, AnthropicCacheTtl.class)));
        return target;
    }

    private static Map<MessageType, Integer> messageTypeMinContentLengths(
            Map<String, Integer> source) {
        if (source == null) {
            return null;
        }
        Map<MessageType, Integer> target = new LinkedHashMap<>();
        source.forEach(
                (messageType, minContentLength) ->
                        target.put(
                                ProviderPropertyMapper.convert(messageType, MessageType.class),
                                minContentLength));
        return target;
    }
}
