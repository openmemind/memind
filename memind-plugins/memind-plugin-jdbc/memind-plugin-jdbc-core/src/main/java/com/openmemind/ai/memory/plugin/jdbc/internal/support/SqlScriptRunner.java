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
package com.openmemind.ai.memory.plugin.jdbc.internal.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class SqlScriptRunner {

    private SqlScriptRunner() {}

    public static void execute(DataSource dataSource, String classpathResource) {
        String script = loadResource(classpathResource);
        int statementNumber = 0;
        String failingSql = "";
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(script)) {
                if (!sql.isBlank()) {
                    statementNumber++;
                    failingSql = sql;
                    statement.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new JdbcPluginException(
                    "Failed to execute SQL script: "
                            + classpathResource
                            + " near statement "
                            + statementNumber
                            + ": "
                            + abbreviateSql(failingSql),
                    e);
        }
    }

    public static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int beginEndDepth = 0;

        for (int i = 0; i < script.length(); i++) {
            char currentChar = script.charAt(i);
            char nextChar = i + 1 < script.length() ? script.charAt(i + 1) : 0;

            if (inLineComment) {
                if (currentChar == '\n') {
                    inLineComment = false;
                    current.append(currentChar);
                }
                continue;
            }
            if (inBlockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingleQuote && currentChar == '-' && nextChar == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (!inSingleQuote && currentChar == '/' && nextChar == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (currentChar == '\'') {
                inSingleQuote = !inSingleQuote;
            }

            if (!inSingleQuote) {
                if (isWordAt(script, i, "BEGIN")) {
                    beginEndDepth++;
                } else if (isWordAt(script, i, "END")
                        && !isCompoundEndModifier(nextWordAfter(script, i + "END".length()))
                        && beginEndDepth > 0) {
                    beginEndDepth--;
                }
            }

            if (currentChar == ';' && !inSingleQuote && beginEndDepth == 0) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }

        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        return statements;
    }

    private static boolean isWordAt(String text, int index, String word) {
        int end = index + word.length();
        return end <= text.length()
                && text.substring(index, end).equalsIgnoreCase(word)
                && (index == 0 || !isIdentifierPart(text.charAt(index - 1)))
                && (end == text.length() || !isIdentifierPart(text.charAt(end)));
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static String nextWordAfter(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        int wordStart = index;
        while (index < text.length()
                && (Character.isLetterOrDigit(text.charAt(index)) || text.charAt(index) == '_')) {
            index++;
        }
        return text.substring(wordStart, index);
    }

    private static boolean isCompoundEndModifier(String word) {
        return word.equalsIgnoreCase("CASE")
                || word.equalsIgnoreCase("IF")
                || word.equalsIgnoreCase("LOOP")
                || word.equalsIgnoreCase("REPEAT")
                || word.equalsIgnoreCase("WHILE");
    }

    private static String abbreviateSql(String sql) {
        String compact = sql.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 237) + "...";
    }

    private static String loadResource(String path) {
        try (InputStream inputStream =
                SqlScriptRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JdbcPluginException("Failed to load SQL script: " + path, e);
        }
    }
}
