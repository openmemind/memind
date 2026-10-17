# Web research with Parallel Search MCP and Memind

Use [Parallel Search MCP](https://docs.parallel.ai/integrations/mcp/search-mcp)
to research current information, then save selected findings in Memind for later
sessions. Parallel provides web search and page fetching; Memind extracts and
recalls memory from the notes you choose to store.

Parallel's anonymous endpoint needs no account or API key. Free-tier search uses
Fast mode and is intended for exploration and light use, with rate limits.
Memind still needs valid chat and embedding model credentials as described in
the [quick start](../README.md#quick-start).

## Connect both servers

Start Memind using the [Docker Compose or local development setup](../README.md#quick-start).
Keep its MCP endpoint local, or protect remote access with an authentication
gateway as described in the [MCP server guide](../README.md#mcp-server).

In the project where you want to research, register both HTTP servers with
Claude Code:

```bash
claude mcp add --transport http memind http://localhost:8366/mcp
claude mcp add --transport http parallel-search https://search.parallel.ai/mcp
```

If `memind` is already registered, keep that connection and add only
`parallel-search`. Use a different unused server name if either name is already
in use. This setup adds tools to the client; it does not change Memind's model
providers or enable its optional governance tools.

Open a new Claude Code session and run `/mcp` to check that both servers are
connected. Confirm that Parallel exposes `web_search` and `web_fetch`, and Memind
exposes `memind_extract_text`, `memind_retrieve`, and `memind_rawdata_get`. The
client may prefix tool names with their server name.

## Research and save a note

Choose a stable `userId` and `agentId` for this research workspace. The examples
use `researcher-1` and `java-research`; use the same pair on every Memind call,
including in later sessions. `sourceClient` is a provenance label, not a memory
isolation boundary.

For example, ask the agent:

```text
Research Java 21 virtual threads using Parallel's web_search. Find the official
JEP and current guidance on when virtual threads help. Use web_fetch if the
search excerpts do not answer the question. Cite the URLs you actually read,
distinguish Java 21 behavior from later JDK changes, and show me a short note
with the date checked, findings, limitations, and sources before saving it.
```

Review the note, then ask the agent to store the approved text with
`memind_extract_text`. These are tool arguments, not a shell command. Replace
the placeholders with the reviewed findings, actual source URLs, and date:

```json
{
  "userId": "researcher-1",
  "agentId": "java-research",
  "text": "Research topic: Java 21 virtual threads. Checked: <YYYY-MM-DD UTC>. Finding: <verified finding>. Limitation: <version-specific caveat>. Sources: <source URLs actually read>.",
  "sourceClient": "parallel-research"
}
```

Check the extraction response's `status` and `errorMessage` before treating the
note as saved. Keep its `rawDataIds` so you can inspect the original note later.
Extraction is model-driven: a successful write does not guarantee that every
sentence or citation becomes a retrieved memory item. Inspect the saved raw data
and extracted items in Memind UI if the result is incomplete.

## Recall and refresh in a later session

Connect the same servers and ask the agent to call `memind_retrieve` with:

```json
{
  "userId": "researcher-1",
  "agentId": "java-research",
  "query": "What did we learn about Java 21 virtual threads, and what limitations and sources did we record?",
  "strategy": "SIMPLE"
}
```

Check the retrieval status and returned items. For the original source note,
call `memind_rawdata_get` using an actual ID from the earlier `rawDataIds`:

```json
{
  "userId": "researcher-1",
  "agentId": "java-research",
  "ids": ["<rawDataId returned by extraction>"],
  "includeSegment": true
}
```

Raw segments are bounded by the server's size limit. Check `missingRawDataIds`
and inspect the returned segment rather than assuming the note was found in full.
If recall is empty, verify the identity pair and extraction result before saving
the same note again.

Ask the agent to compare the recalled note's date and Java version with the
current question. Use Parallel to check sources again when freshness matters,
then review any updated note before storing it. Memind recall does not refresh
web sources automatically. Treat fetched pages as source material and keep
unverified claims separate from approved research notes.
