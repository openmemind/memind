import {
  useMemo,
  useState,
  type FormEvent,
  type ReactElement,
  type ReactNode,
} from 'react'
import { useMutation } from '@tanstack/react-query'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { retrieveMemory } from '@/features/api/retrieve'
import { PageError } from '@/features/components/data-state'
import { readMemoryScopeFromLocation } from '@/features/components/memory-scope-location'
import type { RetrieveMemoryResponse } from '@/features/types'
import { parseMemoryScope } from '@/lib/memory-scope'
import { RetrieveTrace } from './retrieve-trace'

type FormErrors = {
  userId?: string
  agentId?: string
  query?: string
}

export function RetrievePage() {
  const initialScope = useMemo(
    () => parseMemoryScope(readMemoryScopeFromLocation()),
    []
  )
  const [userId, setUserId] = useState(initialScope.userId)
  const [agentId, setAgentId] = useState(initialScope.agentId)
  const [queryText, setQueryText] = useState('')
  const [strategy, setStrategy] = useState('SIMPLE')
  const [trace, setTrace] = useState(false)
  const [errors, setErrors] = useState<FormErrors>({})

  const mutation = useMutation({
    mutationFn: retrieveMemory,
  })

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const nextErrors = validateForm({ userId, agentId, queryText })
    setErrors(nextErrors)
    mutation.reset()

    if (Object.keys(nextErrors).length > 0) return

    mutation.mutate({
      userId: userId.trim(),
      agentId: agentId.trim(),
      query: queryText.trim(),
      strategy,
      trace,
    })
  }

  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Retrieve</h1>
      </Header>
      <Main>
        <div className='flex flex-col gap-5'>
          <form onSubmit={onSubmit} className='flex flex-col gap-4'>
            <div className='grid gap-4 md:grid-cols-2 xl:grid-cols-4'>
              <FormField label='userId' error={errors.userId}>
                <Input
                  id='retrieve-user-id'
                  value={userId}
                  aria-invalid={Boolean(errors.userId)}
                  onChange={(event) => setUserId(event.target.value)}
                />
              </FormField>
              <FormField label='agentId' error={errors.agentId}>
                <Input
                  id='retrieve-agent-id'
                  value={agentId}
                  aria-invalid={Boolean(errors.agentId)}
                  onChange={(event) => setAgentId(event.target.value)}
                />
              </FormField>
              <FormField label='Strategy'>
                <select
                  id='retrieve-strategy'
                  value={strategy}
                  className='h-9 rounded-md border bg-background px-3 text-sm'
                  onChange={(event) => setStrategy(event.target.value)}
                >
                  <option value='SIMPLE'>SIMPLE</option>
                  <option value='DEEP'>DEEP</option>
                </select>
              </FormField>
              <div className='flex items-center gap-3 self-end pb-2'>
                <Checkbox
                  id='retrieve-trace'
                  checked={trace}
                  onCheckedChange={(checked) => setTrace(checked === true)}
                />
                <Label htmlFor='retrieve-trace'>Trace</Label>
              </div>
            </div>

            <FormField label='Query' error={errors.query}>
              <Textarea
                id='retrieve-query'
                value={queryText}
                aria-invalid={Boolean(errors.query)}
                onChange={(event) => setQueryText(event.target.value)}
              />
            </FormField>

            <Button
              type='submit'
              className='w-fit'
              disabled={mutation.isPending}
            >
              Retrieve
            </Button>
          </form>

          {mutation.isError ? (
            <PageError message='Retrieve request failed.' />
          ) : null}

          {mutation.data ? <RetrieveResults result={mutation.data} /> : null}
        </div>
      </Main>
    </>
  )
}

function FormField({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: ReactElement<{ id: string }>
}) {
  const id = children.props.id

  return (
    <div className='flex flex-col gap-2'>
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? <p className='text-sm text-destructive'>{error}</p> : null}
    </div>
  )
}

function RetrieveResults({ result }: { result: RetrieveMemoryResponse }) {
  return (
    <div className='flex flex-col gap-4'>
      <ResultSection title='Items'>
        {result.items.map((item) => (
          <ResultRow key={item.id} title={item.id} text={item.text} />
        ))}
      </ResultSection>
      <ResultSection title='Insights'>
        {result.insights.map((insight) => (
          <ResultRow key={insight.id} title={insight.id} text={insight.text} />
        ))}
      </ResultSection>
      <ResultSection title='Raw data'>
        {result.rawData.map((rawData) => (
          <ResultRow
            key={rawData.rawDataId}
            title={rawData.rawDataId}
            text={rawData.caption ?? '-'}
          />
        ))}
      </ResultSection>
      <ResultSection title='Evidences'>
        {result.evidences.map((evidence) => (
          <p key={evidence} className='text-sm'>
            {evidence}
          </p>
        ))}
      </ResultSection>
      <RetrieveTrace trace={result.trace} />
    </div>
  )
}

function ResultSection({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <section className='rounded-md border p-4'>
      <h2 className='font-semibold'>{title}</h2>
      <div className='mt-3 flex flex-col gap-2'>{children}</div>
    </section>
  )
}

function ResultRow({ title, text }: { title: string; text: string }) {
  return (
    <article className='rounded-md border p-3 text-sm'>
      <p className='font-medium'>{title}</p>
      <p className='mt-1'>{text}</p>
    </article>
  )
}

function validateForm({
  userId,
  agentId,
  queryText,
}: {
  userId: string
  agentId: string
  queryText: string
}) {
  const errors: FormErrors = {}
  if (!userId.trim()) errors.userId = 'userId is required.'
  if (!agentId.trim()) errors.agentId = 'agentId is required.'
  if (!queryText.trim()) errors.query = 'Query is required.'
  return errors
}
