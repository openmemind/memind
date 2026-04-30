const DASH = '-'

const dateTimeFormatter = new Intl.DateTimeFormat('en-US', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

const numberFormatter = new Intl.NumberFormat('en-US')

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return DASH

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return DASH

  return dateTimeFormatter.format(date)
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) return DASH
  return numberFormatter.format(value)
}

export function compactJson(value: unknown): string {
  try {
    const json = JSON.stringify(value)
    return json === undefined ? String(value) : json
  } catch {
    return String(value)
  }
}

export function truncateText(
  value: string | null | undefined,
  maxLength: number
): string {
  if (!value) return DASH
  if (value.length <= maxLength) return value
  if (maxLength <= 3) return '.'.repeat(Math.max(0, maxLength))

  return `${value.slice(0, maxLength - 3)}...`
}
