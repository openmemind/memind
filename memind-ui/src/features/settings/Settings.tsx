import {
  AlertTriangle,
  Circle,
  RotateCcw,
  Save,
  Search,
  Trash2,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldLabel,
  FieldTitle,
} from "@/components/ui/field"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import { Switch } from "@/components/ui/switch"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { FilterSelect } from "@/features/shared/ui"
import { cn } from "@/lib/utils"

import { useSettingsData } from "./settings-data"

const settingsNavItems = [
  { label: "General", active: true },
  { label: "Memory Runtime", attention: true },
  { label: "Extraction" },
  { label: "Retrieval" },
  { label: "Insights" },
  { label: "Threads" },
  { label: "Models" },
  { label: "API Keys" },
  { label: "Security" },
  { label: "Observability" },
  { label: "Advanced" },
]

function SettingsNav() {
  return (
    <nav className="hidden w-64 shrink-0 border-r border-border/80 bg-sidebar/80 lg:block">
      <div className="flex flex-col gap-6 p-4">
        <InputGroup className="h-8">
          <InputGroupAddon>
            <Search />
          </InputGroupAddon>
          <InputGroupInput placeholder="Search settings..." type="search" />
        </InputGroup>

        <ul className="flex flex-col gap-1">
          {settingsNavItems.map((item) => (
            <li key={item.label}>
              <button
                className={cn(
                  "flex h-8 w-full cursor-pointer items-center justify-between rounded-lg px-3 text-left text-sm transition-colors",
                  item.active
                    ? "bg-sidebar-accent font-semibold text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/70 hover:text-sidebar-accent-foreground"
                )}
                type="button"
              >
                <span>{item.label}</span>
                {item.attention ? (
                  <span className="size-2 rounded-full bg-destructive" />
                ) : null}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </nav>
  )
}

function SettingsHeader() {
  return (
    <div className="mb-6 flex flex-col gap-5 border-b border-border/70 pb-6 md:flex-row md:items-end md:justify-between">
      <div className="max-w-2xl">
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
          Settings
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Configure memory runtime behavior, model providers, security, and
          system preferences.
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-4">
        <span className="font-mono text-xs text-destructive">
          3 unsaved changes
        </span>
        <div className="flex gap-2">
          <Button variant="outline">
            <RotateCcw data-icon="inline-start" />
            Reset changes
          </Button>
          <Button>
            <Save data-icon="inline-start" />
            Save changes
          </Button>
        </div>
      </div>
    </div>
  )
}

function SectionCard({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <Card className="gap-0 py-0">
      <CardHeader className="border-b border-border/70 py-5">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-6">{children}</CardContent>
    </Card>
  )
}

function PreferenceRow({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <Field
      className="grid gap-3 md:grid-cols-[minmax(0,220px)_minmax(0,1fr)] md:items-center"
      orientation="vertical"
    >
      <FieldLabel className="text-sm font-semibold">{label}</FieldLabel>
      <div>{children}</div>
    </Field>
  )
}

function DisplayPreferences({
  preferences,
}: {
  preferences: {
    defaultTimeRange: string
    defaultMemoryView: string
    theme: string
  }
}) {
  return (
    <SectionCard title="Display Preferences">
      <FieldContent className="gap-6">
        <PreferenceRow label="Default Time Range">
          <FilterSelect
            aria-label="Default time range"
            className="w-full md:w-64"
            defaultValue={preferences.defaultTimeRange}
            items={[
              { value: "24h", label: "24h" },
              { value: "7d", label: "7d" },
              { value: "30d", label: "30d" },
            ]}
          />
        </PreferenceRow>

        <PreferenceRow label="Default Memory View">
          <ToggleGroup
            className="rounded-lg border bg-muted p-1"
            defaultValue={[preferences.defaultMemoryView]}
            spacing={0}
            variant="outline"
          >
            <ToggleGroupItem value="table">Table</ToggleGroupItem>
            <ToggleGroupItem value="list">List</ToggleGroupItem>
            <ToggleGroupItem value="grid">Grid</ToggleGroupItem>
          </ToggleGroup>
        </PreferenceRow>

        <PreferenceRow label="Theme">
          <FilterSelect
            aria-label="Theme"
            className="w-full md:w-64"
            defaultValue={preferences.theme}
            items={[
              { value: "light", label: "Light" },
              { value: "dark", label: "Dark" },
              { value: "system", label: "System" },
            ]}
          />
        </PreferenceRow>
      </FieldContent>
    </SectionCard>
  )
}

function BehaviorSwitch({
  id,
  title,
  description,
  checked,
}: {
  id: string
  title: string
  description: string
  checked: boolean
}) {
  return (
    <FieldLabel htmlFor={id}>
      <Field orientation="horizontal">
        <FieldContent>
          <FieldTitle>{title}</FieldTitle>
          <FieldDescription>{description}</FieldDescription>
        </FieldContent>
        <Switch defaultChecked={checked} id={id} />
      </Field>
    </FieldLabel>
  )
}

function EmptyStateBehavior({
  emptyState,
}: {
  emptyState: {
    showOnboardingTips: boolean
    autoHideEmptyCollections: boolean
  }
}) {
  return (
    <SectionCard title="Empty State Behavior">
      <FieldContent className="gap-6">
        <BehaviorSwitch
          checked={emptyState.showOnboardingTips}
          description="Display helpful hints for new collections and empty states."
          id="settings-onboarding-tips"
          title="Show onboarding tips"
        />
        <BehaviorSwitch
          checked={emptyState.autoHideEmptyCollections}
          description="Automatically hide folders and categories that contain no memories."
          id="settings-auto-hide-empty"
          title="Auto-hide empty collections"
        />
      </FieldContent>
    </SectionCard>
  )
}

function DangerZone() {
  return (
    <section className="pt-4">
      <div className="border-t border-destructive/20 pt-6">
        <h2 className="mb-4 text-xl font-semibold text-destructive">
          Danger Zone
        </h2>
        <Card className="border-destructive/30 bg-destructive/5 py-0 ring-destructive/20">
          <CardContent className="flex flex-col gap-4 p-4 md:flex-row md:items-center md:justify-between">
            <div className="flex items-start gap-3">
              <AlertTriangle className="mt-0.5 text-destructive" />
              <div>
                <p className="text-sm font-bold">Purge Runtime Cache</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Clearing the cache will re-trigger full extraction on next
                  retrieval.
                </p>
              </div>
            </div>
            <Button variant="destructive">
              <Trash2 data-icon="inline-start" />
              Purge Cache
            </Button>
          </CardContent>
        </Card>
      </div>
    </section>
  )
}

export function Settings() {
  const settingsQuery = useSettingsData()

  if (settingsQuery.isLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
        Loading settings...
      </div>
    )
  }

  if (settingsQuery.isError || !settingsQuery.data) {
    return (
      <div className="flex min-h-svh items-center justify-center text-sm text-destructive">
        Failed to load settings.
      </div>
    )
  }

  const data = settingsQuery.data

  return (
    <main className="flex min-h-full overflow-hidden">
      <SettingsNav />
      <div className="min-w-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 lg:px-10 lg:py-8">
          <SettingsHeader />

          <div className="flex flex-col gap-6">
            <DisplayPreferences preferences={data.preferences} />
            <EmptyStateBehavior emptyState={data.emptyState} />
            <DangerZone />
          </div>

          <div className="mt-8 flex items-center justify-end gap-2 rounded-lg bg-card/95 p-3 shadow-sm ring-1 ring-border/80">
            <span className="mr-auto hidden items-center gap-2 font-mono text-xs text-muted-foreground md:flex">
              <Circle className="fill-destructive text-destructive" />
              General
            </span>
            <Button variant="outline">Reset changes</Button>
            <Button>
              <Save data-icon="inline-start" />
              Save changes
            </Button>
          </div>
        </div>
      </div>
    </main>
  )
}
