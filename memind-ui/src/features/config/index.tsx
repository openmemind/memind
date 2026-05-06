import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ApiError } from '@/lib/api-client'
import { cn } from '@/lib/utils'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { getMemoryOptions, updateMemoryOptions } from '@/features/api/config'
import {
  EmptyState,
  PageError,
  PageLoading,
} from '@/features/components/data-state'
import type { MemoryOptionItemView } from '@/features/types'
import { ConfigValueEditor } from './config-value-editor'

export function ConfigPage() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['config', 'memory-options'],
    queryFn: getMemoryOptions,
  })
  const [edits, setEdits] = useState<ConfigEdits>({})
  const draft = useMemo(
    () => (query.data ? buildDraftConfig(query.data.config, edits) : null),
    [edits, query.data]
  )
  const sections = useMemo(() => (draft ? Object.entries(draft) : []), [draft])
  const [selectedSectionKey, setSelectedSectionKey] = useState<string | null>(
    null
  )

  const activeSection = useMemo(() => {
    if (!selectedSectionKey) return sections[0] ?? null
    return (
      sections.find(([sectionKey]) => sectionKey === selectedSectionKey) ??
      sections[0] ??
      null
    )
  }, [selectedSectionKey, sections])

  const modifiedCount = useMemo(
    () => (query.data ? countModifiedEdits(query.data.config, edits) : 0),
    [edits, query.data]
  )
  const isDirty = modifiedCount > 0

  useEffect(() => {
    if (!isDirty) return

    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [isDirty])

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!query.data || !draft) {
        throw new Error('Config is not loaded.')
      }

      return updateMemoryOptions({
        expectedVersion: query.data.version,
        config: draft,
      })
    },
    onSuccess: (response) => {
      setEdits({})
      queryClient.setQueryData(['config', 'memory-options'], response)
    },
  })

  const saveError = saveMutation.error as ApiError | null

  const updateOptionValue = (
    sectionKey: string,
    optionKey: string,
    value: unknown
  ) => {
    saveMutation.reset()
    setEdits((current) => {
      const originalValue = findOptionValue(
        query.data?.config,
        sectionKey,
        optionKey
      )
      const section = { ...(current[sectionKey] ?? {}) }

      if (jsonEqual(value, originalValue)) {
        delete section[optionKey]
      } else {
        section[optionKey] = value
      }

      const next = { ...current }
      if (Object.keys(section).length === 0) {
        delete next[sectionKey]
      } else {
        next[sectionKey] = section
      }
      return next
    })
  }

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Settings</h1>
      </Header>
      <Main>
        <div className='flex min-w-0 flex-col gap-4'>
          <div className='flex flex-wrap items-start justify-between gap-3'>
            <div className='flex min-w-0 flex-col gap-1'>
              <h2 className='text-2xl font-semibold'>Memory Options</h2>
              <p className='text-sm text-muted-foreground'>
                Edit local memory options and save them as one versioned config
                update.
              </p>
            </div>
            <div className='flex items-center gap-2'>
              {isDirty ? (
                <Badge variant='secondary'>
                  {modifiedCount} unsaved{' '}
                  {modifiedCount === 1 ? 'change' : 'changes'}
                </Badge>
              ) : null}
              <Button
                type='button'
                disabled={!isDirty || saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                Save changes
              </Button>
            </div>
          </div>

          {saveError?.status === 409 ? (
            <Alert variant='destructive'>
              <AlertTitle>Save conflict</AlertTitle>
              <AlertDescription>
                The server config changed. Refresh before saving again.
              </AlertDescription>
            </Alert>
          ) : null}

          {saveError && saveError.status !== 409 ? (
            <PageError
              message={saveError.message}
              traceId={saveError.traceId}
            />
          ) : null}

          {query.isLoading ? <PageLoading /> : null}
          {query.isError ? (
            <PageError
              message='Unable to load memory options.'
              onRetry={query.refetch}
            />
          ) : null}
          {draft && Object.keys(draft).length === 0 ? (
            <EmptyState title='No memory options found.' />
          ) : null}
          {sections.length > 0 && activeSection ? (
            <div className='grid min-w-0 gap-4 lg:grid-cols-[minmax(15rem,18rem)_minmax(0,1fr)]'>
              <ConfigSectionNav
                sections={sections}
                activeSectionKey={activeSection[0]}
                onSelect={setSelectedSectionKey}
              />
              <ConfigSection
                sectionKey={activeSection[0]}
                options={activeSection[1]}
                onChange={updateOptionValue}
              />
            </div>
          ) : null}
        </div>
      </Main>
    </>
  )
}

function ConfigSectionNav({
  sections,
  activeSectionKey,
  onSelect,
}: {
  sections: [string, MemoryOptionItemView[]][]
  activeSectionKey: string
  onSelect: (sectionKey: string) => void
}) {
  return (
    <div className='min-w-0 lg:sticky lg:top-20 lg:self-start'>
      <div className='min-w-0 rounded-md border p-3 lg:hidden'>
        <label
          htmlFor='config-section-select'
          className='mb-2 block text-xs font-medium text-muted-foreground'
        >
          Section
        </label>
        <select
          id='config-section-select'
          className='h-9 w-full min-w-0 rounded-md border bg-background px-3 text-sm'
          value={activeSectionKey}
          onChange={(event) => onSelect(event.target.value)}
        >
          {sections.map(([sectionKey, options]) => (
            <option key={sectionKey} value={sectionKey}>
              {formatSectionLabel(sectionKey)} ({options.length})
            </option>
          ))}
        </select>
      </div>

      <nav
        aria-label='Config sections'
        className='hidden min-w-0 rounded-md border lg:block'
      >
        <div className='border-b px-3 py-2'>
          <p className='text-xs font-medium text-muted-foreground'>
            Sections ({sections.length})
          </p>
        </div>
        <div className='flex min-w-0 flex-col gap-1 overflow-y-auto p-2 lg:max-h-[calc(100vh-12rem)]'>
          {sections.map(([sectionKey, options]) => {
            const isActive = sectionKey === activeSectionKey

            return (
              <button
                key={sectionKey}
                type='button'
                aria-label={`${formatSectionLabel(sectionKey)} ${options.length}`}
                aria-current={isActive ? 'page' : undefined}
                className={cn(
                  'flex min-w-0 items-start justify-between gap-3 rounded-md px-3 py-2 text-left transition-colors',
                  isActive
                    ? 'bg-accent text-accent-foreground'
                    : 'hover:bg-accent/60'
                )}
                onClick={() => onSelect(sectionKey)}
              >
                <span className='flex min-w-0 flex-col gap-0.5'>
                  <span className='font-medium [overflow-wrap:anywhere] break-words'>
                    {formatSectionLabel(sectionKey)}
                  </span>
                  <span className='text-xs [overflow-wrap:anywhere] break-words text-muted-foreground'>
                    {sectionKey}
                  </span>
                </span>
                <Badge variant='secondary'>{options.length}</Badge>
              </button>
            )
          })}
        </div>
      </nav>
    </div>
  )
}

function ConfigSection({
  sectionKey,
  options,
  onChange,
}: {
  sectionKey: string
  options: MemoryOptionItemView[]
  onChange: (sectionKey: string, optionKey: string, value: unknown) => void
}) {
  return (
    <section className='min-w-0 rounded-md border'>
      <div className='flex min-w-0 flex-col gap-1 border-b px-4 py-3'>
        <h3 className='text-base font-semibold [overflow-wrap:anywhere] break-words'>
          {formatSectionLabel(sectionKey)}
        </h3>
        <p className='text-xs [overflow-wrap:anywhere] break-words text-muted-foreground'>
          {sectionKey}
        </p>
      </div>
      <div className='min-w-0 divide-y'>
        {options.map((option) => (
          <div
            key={option.key}
            className='grid min-w-0 gap-3 p-4 lg:grid-cols-[minmax(0,20rem)_minmax(0,1fr)]'
          >
            <div className='flex min-w-0 flex-col gap-1'>
              <p className='font-medium [overflow-wrap:anywhere] break-words'>
                {option.key}
              </p>
              <p className='text-sm [overflow-wrap:anywhere] break-words text-muted-foreground'>
                {option.description ?? 'No description.'}
              </p>
              <p className='text-xs [overflow-wrap:anywhere] break-words text-muted-foreground'>
                type: {option.type}
              </p>
            </div>
            <ConfigValueEditor
              option={option}
              onChange={(value) => onChange(sectionKey, option.key, value)}
            />
          </div>
        ))}
      </div>
    </section>
  )
}

type ConfigEdits = Record<string, Record<string, unknown>>

function buildDraftConfig(
  config: Record<string, MemoryOptionItemView[]>,
  edits: ConfigEdits
) {
  return Object.fromEntries(
    Object.entries(config).map(([sectionKey, options]) => [
      sectionKey,
      options.map((option) => {
        const sectionEdits = edits[sectionKey] ?? {}
        return {
          ...option,
          value: Object.prototype.hasOwnProperty.call(sectionEdits, option.key)
            ? sectionEdits[option.key]
            : option.value,
        }
      }),
    ])
  )
}

function countModifiedEdits(
  original: Record<string, MemoryOptionItemView[]>,
  edits: ConfigEdits
) {
  let count = 0

  for (const [sectionKey, values] of Object.entries(edits)) {
    for (const [optionKey, value] of Object.entries(values)) {
      if (!jsonEqual(value, findOptionValue(original, sectionKey, optionKey))) {
        count += 1
      }
    }
  }

  return count
}

function findOptionValue(
  config: Record<string, MemoryOptionItemView[]> | undefined,
  sectionKey: string,
  optionKey: string
) {
  return config?.[sectionKey]?.find((option) => option.key === optionKey)?.value
}

function jsonEqual(left: unknown, right: unknown) {
  return JSON.stringify(left) === JSON.stringify(right)
}

function formatSectionLabel(sectionKey: string) {
  return sectionKey
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .split(/[.\s_-]+/)
    .filter(Boolean)
    .map(formatSectionLabelSegment)
    .join(' ')
}

function formatSectionLabelSegment(segment: string) {
  if (segment.toLowerCase() === 'rawdata') {
    return 'Raw Data'
  }

  return segment.charAt(0).toUpperCase() + segment.slice(1)
}
