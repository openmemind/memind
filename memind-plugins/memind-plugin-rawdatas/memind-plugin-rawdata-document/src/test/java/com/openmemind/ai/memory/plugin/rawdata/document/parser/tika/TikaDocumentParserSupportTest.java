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
package com.openmemind.ai.memory.plugin.rawdata.document.parser.tika;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TikaDocumentParserSupportTest {

    @Test
    void parserSupportDoesNotLoadExternalOrOcrParsers() {
        var support = new TikaDocumentParserSupport();

        assertThat(support.componentParserClassNames())
                .doesNotContain(
                        "org.apache.tika.parser.external.CompositeExternalParser",
                        "org.apache.tika.parser.ocr.TesseractOCRParser");
    }
}
