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

import * as React from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { RotateCcw, Save, Search } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Field,
  FieldContent,
  FieldGroup,
  FieldDescription,
  FieldLabel,
  FieldTitle,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"

import type {
  DefaultMemoryView,
  DefaultTimeRange,
  SettingsData,
  ThemePreference,
} from "./settings-data"
import { useSettingsData } from "./settings-data"
import type { MemoryOptionItem, MemoryOptionsConfig } from "./settings-api"
import { updateMemoryOptions, updateUiPreferences } from "./settings-api"

type SettingsNavItem = {
  id: SettingsSectionId
  active?: boolean
  attention?: boolean
  label: string
}

type SettingsSectionId = "general" | `memory:${string}`

type MemoryOptionGroupView = {
  id: string
  label: string
  options: MemoryOptionItem[]
}

function memorySettingsSectionId(groupId: string): SettingsSectionId {
  return `memory:${groupId}`
}

function createSettingsNavItems(
  activeSection: SettingsSectionId,
  memoryOptionGroups: MemoryOptionGroupView[]
): SettingsNavItem[] {
  const items: SettingsNavItem[] = [
    { id: "general", label: "General" },
    ...memoryOptionGroups.map(
      (group): SettingsNavItem => ({
        id: memorySettingsSectionId(group.id),
        label: group.label,
      })
    ),
  ]

  return items.map(
    (item): SettingsNavItem => ({
      ...item,
      active: item.id === activeSection,
    })
  )
}

function SettingsMobileTabs({
  activeSection,
  memoryOptionGroups,
  onSectionChange,
}: {
  activeSection: SettingsSectionId
  memoryOptionGroups: MemoryOptionGroupView[]
  onSectionChange: (section: SettingsSectionId) => void
}) {
  const items = createSettingsNavItems(activeSection, memoryOptionGroups)

  return (
    <div className="-mx-4 mb-6 overflow-x-auto px-4 sm:-mx-6 sm:px-6 lg:hidden">
      <ToggleGroup
        className="w-max rounded-lg border bg-muted p-1"
        onValueChange={(value) => {
          if (value[0]) {
            onSectionChange(value[0] as SettingsSectionId)
          }
        }}
        spacing={0}
        value={[activeSection]}
        variant="outline"
      >
        {items.map((item) => (
          <ToggleGroupItem key={item.id} value={item.id}>
            {item.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  )
}

function SettingsNav({
  activeSection,
  memoryOptionGroups,
  onSectionChange,
}: {
  activeSection: SettingsSectionId
  memoryOptionGroups: MemoryOptionGroupView[]
  onSectionChange: (section: SettingsSectionId) => void
}) {
  const settingsNavItems = createSettingsNavItems(
    activeSection,
    memoryOptionGroups
  )

  return (
    <nav
      className="hidden h-full w-64 shrink-0 overflow-hidden border-r border-border/80 bg-sidebar/80 lg:block"
      data-testid="settings-secondary-nav"
    >
      <div className="flex h-full flex-col gap-6 overflow-hidden p-4">
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
                aria-current={item.active ? "page" : undefined}
                className={cn(
                  "flex h-8 w-full cursor-pointer items-center justify-between rounded-lg px-3 text-left text-sm transition-colors",
                  item.active
                    ? "bg-sidebar-accent font-semibold text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/70 hover:text-sidebar-accent-foreground"
                )}
                onClick={() => onSectionChange(item.id)}
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
    <div className="mb-6 border-b border-border/70 pb-6">
      <div className="max-w-2xl">
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
          Settings
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Configure memory runtime behavior, model providers, security, and
          system preferences.
        </p>
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
        <CardTitle className="text-base">
          <h2>{title}</h2>
        </CardTitle>
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
  form,
  onDefaultMemoryViewChange,
  onDefaultTimeRangeChange,
  onThemeChange,
}: {
  form: SettingsFormState
  onDefaultMemoryViewChange: (value: DefaultMemoryView) => void
  onDefaultTimeRangeChange: (value: DefaultTimeRange) => void
  onThemeChange: (value: ThemePreference) => void
}) {
  return (
    <SectionCard title="Display Preferences">
      <FieldContent className="gap-6">
        <PreferenceRow label="Default Time Range">
          <Select
            onValueChange={(value) =>
              value ? onDefaultTimeRangeChange(value as DefaultTimeRange) : null
            }
            value={form.defaultTimeRange}
          >
            <SelectTrigger
              aria-label="Default time range"
              className="w-full md:w-64"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value="24h">24h</SelectItem>
                <SelectItem value="7d">7d</SelectItem>
                <SelectItem value="30d">30d</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
        </PreferenceRow>

        <PreferenceRow label="Default Memory View">
          <ToggleGroup
            className="rounded-lg border bg-muted p-1"
            onValueChange={(value) => {
              if (value[0]) {
                onDefaultMemoryViewChange(value[0] as DefaultMemoryView)
              }
            }}
            spacing={0}
            value={[form.defaultMemoryView]}
            variant="outline"
          >
            <ToggleGroupItem value="table">Table</ToggleGroupItem>
            <ToggleGroupItem value="list">List</ToggleGroupItem>
            <ToggleGroupItem value="grid">Grid</ToggleGroupItem>
          </ToggleGroup>
        </PreferenceRow>

        <PreferenceRow label="Theme">
          <Select
            onValueChange={(value) =>
              value ? onThemeChange(value as ThemePreference) : null
            }
            value={form.theme}
          >
            <SelectTrigger aria-label="Theme" className="w-full md:w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value="light">Light</SelectItem>
                <SelectItem value="dark">Dark</SelectItem>
                <SelectItem value="system">System</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
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
  onCheckedChange,
}: {
  id: string
  title: string
  description: string
  checked: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <FieldLabel htmlFor={id}>
      <Field orientation="horizontal">
        <FieldContent>
          <FieldTitle>{title}</FieldTitle>
          <FieldDescription>{description}</FieldDescription>
        </FieldContent>
        <Switch checked={checked} id={id} onCheckedChange={onCheckedChange} />
      </Field>
    </FieldLabel>
  )
}

function EmptyStateBehavior({
  form,
  onAutoHideEmptyCollectionsChange,
  onShowOnboardingTipsChange,
}: {
  form: SettingsFormState
  onAutoHideEmptyCollectionsChange: (checked: boolean) => void
  onShowOnboardingTipsChange: (checked: boolean) => void
}) {
  return (
    <SectionCard title="Empty State Behavior">
      <FieldContent className="gap-6">
        <BehaviorSwitch
          checked={form.showOnboardingTips}
          description="Display helpful hints for new collections and empty states."
          id="settings-onboarding-tips"
          onCheckedChange={onShowOnboardingTipsChange}
          title="Show onboarding tips"
        />
        <BehaviorSwitch
          checked={form.autoHideEmptyCollections}
          description="Automatically hide folders and categories that contain no memories."
          id="settings-auto-hide-empty"
          onCheckedChange={onAutoHideEmptyCollectionsChange}
          title="Auto-hide empty collections"
        />
      </FieldContent>
    </SectionCard>
  )
}

function formatLabel(value: string) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[._-]+/g, " ")
    .trim()
    .replace(/\b\w/g, (character) => character.toUpperCase())
}

function buildMemoryOptionGroups(
  config: MemoryOptionsConfig
): MemoryOptionGroupView[] {
  return Object.entries(config).map(([groupKey, options]) => ({
    id: groupKey,
    label: formatLabel(groupKey),
    options,
  }))
}

function fieldNameFromKey(key: string) {
  const segments = key.split(".")
  return formatLabel(segments[segments.length - 1] ?? key)
}

function fieldId(key: string) {
  return `settings-memory-option-${key.replace(/[^a-zA-Z0-9_-]/g, "-")}`
}

function formatStructuredValue(value: unknown) {
  if (value === undefined || value === null) {
    return ""
  }

  if (typeof value === "object") {
    return JSON.stringify(value, null, 2)
  }

  return String(value)
}

function parseOptionInputValue(option: MemoryOptionItem, value: string) {
  if (option.type === "integer") {
    return Number.parseInt(value, 10)
  }

  if (option.type === "double") {
    return Number.parseFloat(value)
  }

  if (option.type === "array" || option.type === "object") {
    try {
      return JSON.parse(value) as unknown
    } catch {
      return value
    }
  }

  return value
}

function allowedValues(option: MemoryOptionItem) {
  const values = option.constraints?.allowedValues
  return Array.isArray(values) ? values.map(String) : []
}

function MemoryOptionField({
  onValueChange,
  option,
}: {
  onValueChange: (key: string, value: unknown) => void
  option: MemoryOptionItem
}) {
  const id = fieldId(option.key)
  const label = fieldNameFromKey(option.key)
  const selectValues = allowedValues(option)

  if (option.type === "boolean" && typeof option.value === "boolean") {
    return (
      <FieldLabel htmlFor={id}>
        <Field orientation="horizontal">
          <FieldContent>
            <FieldTitle>{label}</FieldTitle>
            {option.description ? (
              <FieldDescription>{option.description}</FieldDescription>
            ) : null}
          </FieldContent>
          <Switch
            checked={option.value}
            id={id}
            onCheckedChange={(checked) => onValueChange(option.key, checked)}
          />
        </Field>
      </FieldLabel>
    )
  }

  if (selectValues.length) {
    return (
      <Field>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {option.description ? (
          <FieldDescription>{option.description}</FieldDescription>
        ) : null}
        <Select
          onValueChange={(value) => onValueChange(option.key, value)}
          value={formatStructuredValue(option.value)}
        >
          <SelectTrigger aria-label={label} className="w-full md:w-64" id={id}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {selectValues.map((value) => (
                <SelectItem key={value} value={value}>
                  {formatLabel(value)}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>
    )
  }

  if (option.type === "array" || option.type === "object") {
    return (
      <Field>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {option.description ? (
          <FieldDescription>{option.description}</FieldDescription>
        ) : null}
        <Textarea
          aria-label={label}
          id={id}
          onChange={(event) =>
            onValueChange(
              option.key,
              parseOptionInputValue(option, event.target.value)
            )
          }
          value={formatStructuredValue(option.value)}
        />
      </Field>
    )
  }

  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      {option.description ? (
        <FieldDescription>{option.description}</FieldDescription>
      ) : null}
      <Input
        aria-label={label}
        id={id}
        onChange={(event) =>
          onValueChange(
            option.key,
            parseOptionInputValue(option, event.target.value)
          )
        }
        type={
          option.type === "integer" || option.type === "double"
            ? "number"
            : "text"
        }
        value={formatStructuredValue(option.value)}
      />
    </Field>
  )
}

function MemoryOptionsGroupPanel({
  group,
  onValueChange,
}: {
  group: MemoryOptionGroupView
  onValueChange: (key: string, value: unknown) => void
}) {
  return (
    <SectionCard title={group.label}>
      <FieldGroup>
        {group.options.map((option) => (
          <MemoryOptionField
            key={option.key}
            onValueChange={onValueChange}
            option={option}
          />
        ))}
      </FieldGroup>
    </SectionCard>
  )
}

function cloneValue<T>(value: T): T {
  if (value === undefined || value === null || typeof value !== "object") {
    return value
  }

  return JSON.parse(JSON.stringify(value)) as T
}

function cloneMemoryOptionsConfig(
  config?: MemoryOptionsConfig
): MemoryOptionsConfig {
  return Object.fromEntries(
    Object.entries(config ?? {}).map(([groupKey, options]) => [
      groupKey,
      options.map((option) => ({
        ...option,
        constraints: option.constraints ? { ...option.constraints } : undefined,
        defaultValue: cloneValue(option.defaultValue),
        value: cloneValue(option.value),
      })),
    ])
  )
}

function updateMemoryOptionValue(
  config: MemoryOptionsConfig,
  key: string,
  value: unknown
) {
  return Object.fromEntries(
    Object.entries(config).map(([groupKey, options]) => [
      groupKey,
      options.map((option) =>
        option.key === key ? { ...option, value } : option
      ),
    ])
  )
}

type SettingsFormState = {
  defaultTimeRange: DefaultTimeRange
  defaultMemoryView: DefaultMemoryView
  theme: ThemePreference
  showOnboardingTips: boolean
  autoHideEmptyCollections: boolean
  memoryOptionsConfig: MemoryOptionsConfig
  memoryOptionsVersion?: number
}

function createSettingsFormState(data: SettingsData): SettingsFormState {
  return {
    defaultTimeRange: data.preferences.defaultTimeRange,
    defaultMemoryView: data.preferences.defaultMemoryView,
    theme: data.preferences.theme,
    showOnboardingTips: data.emptyState.showOnboardingTips,
    autoHideEmptyCollections: data.emptyState.autoHideEmptyCollections,
    memoryOptionsConfig: cloneMemoryOptionsConfig(data.memoryOptions?.config),
    memoryOptionsVersion: data.memoryOptions?.version,
  }
}

function hasUiPreferenceChanges(
  form: SettingsFormState,
  initialForm: SettingsFormState
) {
  return (
    form.defaultTimeRange !== initialForm.defaultTimeRange ||
    form.defaultMemoryView !== initialForm.defaultMemoryView ||
    form.theme !== initialForm.theme ||
    form.showOnboardingTips !== initialForm.showOnboardingTips ||
    form.autoHideEmptyCollections !== initialForm.autoHideEmptyCollections
  )
}

function hasMemoryOptionsChanges(
  form: SettingsFormState,
  initialForm: SettingsFormState
) {
  return (
    JSON.stringify(form.memoryOptionsConfig) !==
    JSON.stringify(initialForm.memoryOptionsConfig)
  )
}

function hasSettingsChanges(
  form: SettingsFormState,
  initialForm: SettingsFormState
) {
  return (
    hasUiPreferenceChanges(form, initialForm) ||
    hasMemoryOptionsChanges(form, initialForm)
  )
}

function FloatingSettingsActions({
  isSaving,
  onDiscard,
  onSave,
}: {
  isSaving: boolean
  onDiscard: () => void
  onSave: () => void
}) {
  return (
    <div className="pointer-events-none absolute right-0 bottom-5 left-0 flex justify-center px-4 lg:left-64">
      <div className="pointer-events-auto flex w-full max-w-xl items-center justify-between gap-3 rounded-lg border bg-card/95 p-3 shadow-lg ring-1 ring-border/80 backdrop-blur">
        <span className="text-sm text-muted-foreground">
          You have unsaved settings changes.
        </span>
        <div className="flex shrink-0 gap-2">
          <Button onClick={onDiscard} type="button" variant="outline">
            <RotateCcw data-icon="inline-start" />
            Discard changes
          </Button>
          <Button disabled={isSaving} onClick={onSave} type="button">
            <Save data-icon="inline-start" />
            {isSaving ? "Saving..." : "Save changes"}
          </Button>
        </div>
      </div>
    </div>
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

  return <SettingsContent data={data} />
}

function SettingsContent({ data }: { data: SettingsData }) {
  const queryClient = useQueryClient()
  const [savedForm, setSavedForm] = React.useState(() =>
    createSettingsFormState(data)
  )
  const [form, setForm] = React.useState(savedForm)
  const [activeSection, setActiveSection] =
    React.useState<SettingsSectionId>("general")
  const memoryOptionGroups = React.useMemo(
    () => buildMemoryOptionGroups(form.memoryOptionsConfig),
    [form.memoryOptionsConfig]
  )
  const saveSettings = useMutation({
    mutationFn: async (nextForm: SettingsFormState) => {
      const [preferences, memoryOptions] = await Promise.all([
        hasUiPreferenceChanges(nextForm, savedForm)
          ? updateUiPreferences({
              autoHideEmptyCollections: nextForm.autoHideEmptyCollections,
              defaultMemoryView: nextForm.defaultMemoryView,
              defaultTimeRange: nextForm.defaultTimeRange,
              showOnboardingTips: nextForm.showOnboardingTips,
              theme: nextForm.theme,
            })
          : Promise.resolve(null),
        hasMemoryOptionsChanges(nextForm, savedForm) &&
        nextForm.memoryOptionsVersion !== undefined
          ? updateMemoryOptions({
              config: nextForm.memoryOptionsConfig,
              expectedVersion: nextForm.memoryOptionsVersion,
            })
          : Promise.resolve(null),
      ])

      return { memoryOptions, preferences }
    },
    onSuccess: ({ memoryOptions, preferences }, submittedForm) => {
      const nextPreferences = preferences ?? submittedForm
      const nextForm: SettingsFormState = {
        autoHideEmptyCollections: nextPreferences.autoHideEmptyCollections,
        defaultMemoryView: nextPreferences.defaultMemoryView,
        defaultTimeRange: nextPreferences.defaultTimeRange,
        memoryOptionsConfig: memoryOptions
          ? cloneMemoryOptionsConfig(memoryOptions.config)
          : submittedForm.memoryOptionsConfig,
        memoryOptionsVersion:
          memoryOptions?.version ?? submittedForm.memoryOptionsVersion,
        showOnboardingTips: nextPreferences.showOnboardingTips,
        theme: nextPreferences.theme,
      }
      setSavedForm(nextForm)
      setForm(nextForm)
      void queryClient.invalidateQueries({
        queryKey: ["settings", "workspace"],
      })
    },
  })
  const isDirty = hasSettingsChanges(form, savedForm)

  function updateForm(update: Partial<SettingsFormState>) {
    setForm((currentForm) => ({ ...currentForm, ...update }))
  }

  function updateMemoryOption(key: string, value: unknown) {
    setForm((currentForm) => ({
      ...currentForm,
      memoryOptionsConfig: updateMemoryOptionValue(
        currentForm.memoryOptionsConfig,
        key,
        value
      ),
    }))
  }

  const activeMemoryGroup =
    activeSection === "general"
      ? undefined
      : memoryOptionGroups.find(
          (group) => memorySettingsSectionId(group.id) === activeSection
        )

  return (
    <main className="relative flex h-svh min-h-0 overflow-hidden">
      <SettingsNav
        activeSection={activeSection}
        memoryOptionGroups={memoryOptionGroups}
        onSectionChange={setActiveSection}
      />
      <div
        className="min-h-0 min-w-0 flex-1 overflow-y-auto"
        data-testid="settings-detail-pane"
      >
        <div className="mx-auto max-w-4xl px-4 py-6 pb-28 sm:px-6 lg:px-10 lg:py-8">
          <SettingsHeader />
          <SettingsMobileTabs
            activeSection={activeSection}
            memoryOptionGroups={memoryOptionGroups}
            onSectionChange={setActiveSection}
          />

          <div className="flex flex-col gap-6">
            {activeSection === "general" ? (
              <>
                <DisplayPreferences
                  form={form}
                  onDefaultMemoryViewChange={(defaultMemoryView) =>
                    updateForm({ defaultMemoryView })
                  }
                  onDefaultTimeRangeChange={(defaultTimeRange) =>
                    updateForm({ defaultTimeRange })
                  }
                  onThemeChange={(theme) => updateForm({ theme })}
                />
                <EmptyStateBehavior
                  form={form}
                  onAutoHideEmptyCollectionsChange={(
                    autoHideEmptyCollections
                  ) => updateForm({ autoHideEmptyCollections })}
                  onShowOnboardingTipsChange={(showOnboardingTips) =>
                    updateForm({ showOnboardingTips })
                  }
                />
              </>
            ) : null}

            {activeMemoryGroup ? (
              <MemoryOptionsGroupPanel
                group={activeMemoryGroup}
                onValueChange={updateMemoryOption}
              />
            ) : null}
          </div>
        </div>
      </div>

      {isDirty ? (
        <FloatingSettingsActions
          isSaving={saveSettings.isPending}
          onDiscard={() => setForm(savedForm)}
          onSave={() => saveSettings.mutate(form)}
        />
      ) : null}
    </main>
  )
}
