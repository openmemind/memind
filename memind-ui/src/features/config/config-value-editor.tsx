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

import { useId, type ChangeEvent, type ReactNode } from 'react'
import { compactJson } from '@/lib/format'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { JsonViewer } from '@/features/components/json-viewer'
import type { JsonRecord, MemoryOptionItemView } from '@/features/types'

type ConfigValueEditorProps = {
  option: MemoryOptionItemView
  onChange: (value: unknown) => void
}

export function ConfigValueEditor({
  option,
  onChange,
}: ConfigValueEditorProps) {
  const id = useId()
  const enumOptions = readEnumOptions(option.constraints)

  if (option.type === 'boolean') {
    return (
      <div className='flex min-w-0 items-center gap-3'>
        <Switch
          id={id}
          checked={option.value === true}
          onCheckedChange={onChange}
        />
        <Label
          htmlFor={id}
          className='min-w-0 leading-snug [overflow-wrap:anywhere] break-words'
        >
          <span className='min-w-0 [overflow-wrap:anywhere] break-words'>
            {option.key}
          </span>
        </Label>
      </div>
    )
  }

  if (
    option.type === 'integer' ||
    option.type === 'double' ||
    option.type === 'number'
  ) {
    const numericConstraints = readNumericConstraints(option.constraints)
    return (
      <EditorField label={option.key} id={id}>
        <Input
          id={id}
          type='number'
          value={toInputValue(option.value)}
          min={numericConstraints.min}
          max={numericConstraints.max}
          step={option.type === 'integer' ? 1 : 'any'}
          onChange={(event) =>
            onChange(parseNumberValue(event, option.type === 'integer'))
          }
        />
      </EditorField>
    )
  }

  if (option.type === 'string' || option.type === 'enum') {
    if (enumOptions.length > 0) {
      return (
        <EditorField label={option.key} id={id}>
          <select
            id={id}
            value={toInputValue(option.value)}
            className='h-9 w-full max-w-full min-w-0 rounded-md border bg-background px-3 text-sm'
            onChange={(event) => onChange(event.target.value)}
          >
            {enumOptions.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </EditorField>
      )
    }

    return (
      <EditorField label={option.key} id={id}>
        <Input
          id={id}
          type='text'
          value={toInputValue(option.value)}
          onChange={(event) => onChange(event.target.value)}
        />
      </EditorField>
    )
  }

  if (option.type === 'duration') {
    return (
      <EditorField label={option.key} id={id}>
        <Input
          id={id}
          type='text'
          value={toInputValue(option.value)}
          onChange={(event) => onChange(event.target.value)}
        />
      </EditorField>
    )
  }

  return (
    <EditorField label={`${option.key} JSON`} id={id}>
      <div className='flex flex-col gap-3'>
        <JsonViewer label={`${option.key} value`} value={option.value} />
        <Textarea
          id={id}
          className='min-w-0'
          value={compactJson(option.value)}
          onChange={(event) => onChange(parseJsonOrText(event.target.value))}
        />
      </div>
    </EditorField>
  )
}

function EditorField({
  label,
  id,
  children,
}: {
  label: string
  id: string
  children: ReactNode
}) {
  return (
    <div className='flex min-w-0 flex-col gap-2'>
      <Label
        htmlFor={id}
        className='min-w-0 leading-snug [overflow-wrap:anywhere] break-words'
      >
        <span className='min-w-0 [overflow-wrap:anywhere] break-words'>
          {label}
        </span>
      </Label>
      {children}
    </div>
  )
}

function toInputValue(value: unknown) {
  if (value === null || value === undefined) return ''
  return String(value)
}

function parseNumberValue(
  event: ChangeEvent<HTMLInputElement>,
  integer: boolean
) {
  const value = event.target.value
  if (value === '') return null
  const parsed = integer ? Number.parseInt(value, 10) : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function readNumericConstraints(constraints: JsonRecord | null) {
  return {
    min: readNumberConstraint(constraints, 'min'),
    max: readNumberConstraint(constraints, 'max'),
  }
}

function readNumberConstraint(
  constraints: JsonRecord | null,
  key: 'min' | 'max'
) {
  const value = constraints?.[key]
  if (typeof value === 'number') return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

function readEnumOptions(constraints: JsonRecord | null) {
  const value =
    constraints?.enum ??
    constraints?.options ??
    constraints?.values ??
    constraints?.allowedValues

  if (!Array.isArray(value)) return []

  return value
    .map((item) => {
      if (
        typeof item === 'string' ||
        typeof item === 'number' ||
        typeof item === 'boolean'
      ) {
        return String(item)
      }
      if (item && typeof item === 'object' && 'value' in item) {
        return String((item as { value: unknown }).value)
      }
      return ''
    })
    .filter(Boolean)
}

function parseJsonOrText(value: string) {
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}
