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

import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

final class PdfPageTextExtractor {

    PageAwarePdfText extract(byte[] data) throws Exception {
        try (var document = Loader.loadPDF(data)) {
            List<String> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text =
                        TikaDocumentParserSupport.normalizeBlockText(stripper.getText(document));
                if (!text.isBlank()) {
                    pages.add("Page " + (pages.size() + 1) + ":\n" + text);
                }
            }
            if (pages.isEmpty()) {
                return new PageAwarePdfText("", 0, false);
            }
            return new PageAwarePdfText(String.join("\n\n", pages), pages.size(), true);
        }
    }

    record PageAwarePdfText(String text, int emittedPageCount, boolean pageAware) {}
}
