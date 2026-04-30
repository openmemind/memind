# Memind UI

Local Memory Admin for a developer-run `memind-server`.

`memind-ui` is a standalone Vite React application for inspecting and managing local Memind memory data. It is intentionally not a Maven module and is not bundled into `memind-server` in this first version.

## Local-Only Warning

This UI has no login or authorization flow. It can expose delete, rebuild, and runtime configuration operations from the connected Memind server. Do not expose the Vite dev server, built frontend, or proxied Memind server endpoints to public networks.

## Requirements

- Node.js `^20.19.0 || ^22.12.0 || >=24.0.0`
- pnpm `>=9.0.0`
- Corepack is recommended so the pinned `packageManager` value is used.

## Memind Server

Run `memind-server` separately before using the UI. The default local server URL is:

```text
http://127.0.0.1:8366
```

During development, Vite proxies same-origin requests:

- `/admin` -> `http://127.0.0.1:8366`
- `/open` -> `http://127.0.0.1:8366`

## Development

Install dependencies:

```bash
pnpm install
```

Install the browser runtime used by Vitest browser tests:

```bash
pnpm test:browser:install
```

Start the dev server:

```bash
pnpm dev
```

Run checks:

```bash
pnpm lint
pnpm test
pnpm build
```

## First Run

1. Start `memind-server`.
2. Start this UI with `pnpm dev`.
3. Open the Vite URL shown in the terminal.
4. Set the global memory scope when detail views require `userId` or `agentId`.

## Attribution

This module is derived from selected source files and project structure from `shadcn-admin`. See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).
