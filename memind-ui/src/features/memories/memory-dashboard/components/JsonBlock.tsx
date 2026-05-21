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
