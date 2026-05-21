import { Clock3, Copy, MoreVertical, Plus, Trash2 } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  PageHeader,
  PagePagination,
  PageSurface,
  StatusBadge,
  TableSurface,
  type Tone,
} from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import {
  type ApiKeyRecord,
  type ApiKeyStatus,
  useApiKeysData,
} from "./api-keys-data"

function statusTone(status: ApiKeyStatus): Tone {
  switch (status) {
    case "active":
      return "success"
    case "expiring":
      return "warning"
    case "disabled":
      return "default"
  }
}

function ScopeBadge({ scope }: { scope: string }) {
  return <Badge variant="secondary">{scope}</Badge>
}

function ApiKeyActions({ apiKey }: { apiKey: ApiKeyRecord }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            aria-label={`Actions for ${apiKey.name}`}
            size="icon"
            variant="ghost"
          />
        }
      >
        <MoreVertical />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-40">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Key actions</DropdownMenuLabel>
          <DropdownMenuItem>
            <Copy />
            Copy prefix
          </DropdownMenuItem>
          <DropdownMenuItem>
            <Clock3 />
            Rotate
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive">
          <Trash2 />
          Revoke
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function ApiKeyRow({ apiKey }: { apiKey: ApiKeyRecord }) {
  return (
    <TableRow className={cn(apiKey.status === "disabled" && "opacity-70")}>
      <TableCell>
        <div className="font-medium">{apiKey.name}</div>
        <div className="mt-1 flex items-center gap-2">
          <code>{apiKey.prefix}</code>
          <Copy className="size-3.5 text-muted-foreground" />
        </div>
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-2">
          <Avatar size="sm">
            <AvatarFallback>{apiKey.ownerInitials}</AvatarFallback>
          </Avatar>
          <span>{apiKey.owner}</span>
        </div>
      </TableCell>
      <TableCell>
        <div className="flex flex-wrap gap-1">
          {apiKey.scopes.map((scope) => (
            <ScopeBadge key={scope} scope={scope} />
          ))}
        </div>
      </TableCell>
      <TableCell>
        <StatusBadge label={apiKey.status} tone={statusTone(apiKey.status)} />
      </TableCell>
      <TableCell className="text-muted-foreground">{apiKey.lastUsed}</TableCell>
      <TableCell>{apiKey.requests}</TableCell>
      <TableCell className="text-right">
        {apiKey.status === "expiring" ? (
          <div className="flex flex-col items-end gap-1">
            <span className="font-medium">{apiKey.expires}</span>
            <Badge variant="outline">Soon</Badge>
          </div>
        ) : (
          <span className="text-muted-foreground">{apiKey.expires}</span>
        )}
      </TableCell>
      <TableCell className="text-center">
        <ApiKeyActions apiKey={apiKey} />
      </TableCell>
    </TableRow>
  )
}

function ApiKeysTable({ keys }: { keys: ApiKeyRecord[] }) {
  return (
    <TableSurface>
      <Table className="min-w-[920px]">
        <TableHeader>
          <TableRow>
            <TableHead>Key Name & Prefix</TableHead>
            <TableHead>Owner</TableHead>
            <TableHead>Scopes</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Last Used</TableHead>
            <TableHead>Requests</TableHead>
            <TableHead className="text-right">Expires</TableHead>
            <TableHead className="text-center">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {keys.map((apiKey) => (
            <ApiKeyRow key={apiKey.id} apiKey={apiKey} />
          ))}
        </TableBody>
      </Table>
      <PagePagination label="Showing 3 of 12 keys" />
    </TableSurface>
  )
}

export function ApiKeys() {
  const apiKeysQuery = useApiKeysData()

  if (apiKeysQuery.isLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
        Loading API keys...
      </div>
    )
  }

  if (apiKeysQuery.isError || !apiKeysQuery.data) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-destructive">
        Failed to load API keys.
      </div>
    )
  }

  return (
    <PageSurface>
      <PageHeader
        action={
          <Button>
            <Plus data-icon="inline-start" />
            Create API Key
          </Button>
        }
        description="Create, rotate, and monitor keys used to access Memind."
        title="API Keys"
      />

      <ApiKeysTable keys={apiKeysQuery.data.keys} />
    </PageSurface>
  )
}
