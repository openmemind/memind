//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import hljs from "highlight.js/lib/core"
import jsonLanguage from "highlight.js/lib/languages/json"

hljs.registerLanguage("json", jsonLanguage)

export function JsonBlock({ label, value }: { label: string; value: string }) {
  let formattedValue = value

  try {
    formattedValue = JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    formattedValue = value
  }

  const highlightedJson = hljs.highlight(formattedValue, {
    language: "json",
    ignoreIllegals: true,
  }).value

  return (
    <div>
      <div className="mb-2 text-xs font-medium text-muted-foreground">
        {label}
      </div>
      <pre className="overflow-x-auto rounded-md bg-muted p-4 font-mono text-[11px] leading-relaxed">
        <code
          className="language-json"
          dangerouslySetInnerHTML={{ __html: highlightedJson }}
        />
      </pre>
    </div>
  )
}
