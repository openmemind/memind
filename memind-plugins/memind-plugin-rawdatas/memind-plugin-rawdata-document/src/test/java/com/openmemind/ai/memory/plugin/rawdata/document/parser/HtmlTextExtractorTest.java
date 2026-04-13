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
package com.openmemind.ai.memory.plugin.rawdata.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTextExtractorTest {

    @Test
    void extractBuildsReadableTextForHeadingsListsAndTables() {
        String html =
                """
                <html>
                  <body>
                    <h1>Quarterly Review</h1>
                    <p>Revenue grew strongly.</p>
                    <ul><li>North America</li><li>EMEA</li></ul>
                    <table>
                      <tr><th>Region</th><th>Growth</th></tr>
                      <tr><td>NA</td><td>18%</td></tr>
                    </table>
                  </body>
                </html>
                """;

        assertThat(new HtmlTextExtractor().extract(html))
                .isEqualTo(
                        String.join(
                                "\n",
                                "Quarterly Review",
                                "",
                                "Revenue grew strongly.",
                                "",
                                "- North America",
                                "- EMEA",
                                "",
                                "Region | Growth",
                                "NA | 18%"));
    }

    @Test
    void extractToleratesMalformedHtmlWithoutScriptNoise() {
        String html = "<div>Hello<script>alert(1)</script><span>world";

        assertThat(new HtmlTextExtractor().extract(html)).isEqualTo("Hello world");
    }

    @Test
    void extractDoesNotDuplicateContainerTextWhenInlineChildrenExist() {
        String html = "<div>Hello <span>world</span></div>";

        assertThat(new HtmlTextExtractor().extract(html)).isEqualTo("Hello world");
    }
}
