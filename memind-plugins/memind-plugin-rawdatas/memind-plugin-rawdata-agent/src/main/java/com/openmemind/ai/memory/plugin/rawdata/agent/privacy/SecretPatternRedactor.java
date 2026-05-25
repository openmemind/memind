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
package com.openmemind.ai.memory.plugin.rawdata.agent.privacy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based redactor for secrets commonly present in coding-agent events.
 */
public final class SecretPatternRedactor {

    private static final List<RedactionRule> RULES =
            List.of(
                    new RedactionRule(
                            "bearer_token",
                            Pattern.compile("(?i)\\bBearer\\s+([A-Za-z0-9._~+/=-]{6,})"),
                            match -> "Bearer [REDACTED:bearer_token]"),
                    new RedactionRule(
                            "database_url",
                            Pattern.compile(
                                    "(?i)\\b((?:[A-Z0-9_]*DATABASE_URL|DB_URL)\\s*=\\s*)?"
                                            + "(?:jdbc:)?(?:postgres(?:ql)?|mysql|mariadb|"
                                            + "mongodb(?:\\+srv)?|redis|rediss|amqp|amqps)"
                                            + "://[^\\s/@:]+:[^\\s/@]+@[^\\s]+"),
                            match -> prefix(match, 1) + "[REDACTED:database_url]"),
                    new RedactionRule(
                            "private_key",
                            Pattern.compile(
                                    "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?"
                                            + "(?:-----END [A-Z ]*PRIVATE KEY-----|\\z)"),
                            match -> "[REDACTED:private_key]"),
                    new RedactionRule(
                            "api_key",
                            Pattern.compile(
                                    "(?i)\\b([A-Z0-9_]*(?:API_KEY|APIKEY)\\s*=\\s*)"
                                            + "(?!\\[REDACTED:)[^\\s'\";]+"),
                            match -> prefix(match, 1) + "[REDACTED:api_key]"),
                    new RedactionRule(
                            "cloud_credential",
                            Pattern.compile(
                                    "(?i)\\b((?:AWS|AZURE|GOOGLE|GCP)_[A-Z0-9_]*"
                                            + "(?:SECRET|KEY|TOKEN|CREDENTIAL)[A-Z0-9_]*"
                                            + "\\s*=\\s*)(?!\\[REDACTED:)[^\\s'\";]+"),
                            match -> prefix(match, 1) + "[REDACTED:cloud_credential]"),
                    new RedactionRule(
                            "cloud_credential",
                            Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
                            match -> "[REDACTED:cloud_credential]"),
                    new RedactionRule(
                            "secret_env",
                            Pattern.compile(
                                    "(?i)\\b([A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|PRIVATE_KEY)"
                                            + "[A-Z0-9_]*\\s*=\\s*)(?!\\[REDACTED:)"
                                            + "[^\\s'\";]+"),
                            match -> prefix(match, 1) + "[REDACTED:secret_env]"));

    public RedactionResult redact(String text) {
        if (text == null || text.isEmpty()) {
            return new RedactionResult(text, List.of());
        }

        String redacted = text;
        Set<String> kinds = new LinkedHashSet<>();
        for (RedactionRule rule : RULES) {
            RedactionPass pass = applyRule(redacted, rule);
            redacted = pass.text();
            if (pass.redacted()) {
                kinds.add(rule.kind());
            }
        }
        return new RedactionResult(redacted, List.copyOf(kinds));
    }

    private static RedactionPass applyRule(String text, RedactionRule rule) {
        Matcher matcher = rule.pattern().matcher(text);
        StringBuilder builder = null;
        boolean redacted = false;
        while (matcher.find()) {
            if (builder == null) {
                builder = new StringBuilder(text.length());
            }
            redacted = true;
            matcher.appendReplacement(
                    builder, Matcher.quoteReplacement(rule.replacement().replace(matcher)));
        }
        if (!redacted) {
            return new RedactionPass(text, false);
        }
        matcher.appendTail(builder);
        return new RedactionPass(builder.toString(), true);
    }

    private static String prefix(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? "" : value;
    }

    private record RedactionRule(String kind, Pattern pattern, Replacement replacement) {}

    private record RedactionPass(String text, boolean redacted) {}

    @FunctionalInterface
    private interface Replacement {
        String replace(Matcher matcher);
    }

    public record RedactionResult(String text, List<String> redactionKinds) {

        public RedactionResult {
            redactionKinds = redactionKinds == null ? List.of() : List.copyOf(redactionKinds);
        }

        public boolean redacted() {
            return !redactionKinds.isEmpty();
        }

        public List<String> mutableKindsCopy() {
            return new ArrayList<>(redactionKinds);
        }
    }
}
