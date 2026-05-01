import { useQuery } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { apiGet } from '@/lib/api-client'

const SERVER_STATUS_REFETCH_INTERVAL_MS = 30_000

type HealthResponse = {
  status: string
  service: string
}

export function ServerStatus() {
  const query = useQuery({
    queryKey: ['server-status'],
    queryFn: () => apiGet<HealthResponse>('/open/v1/health'),
    refetchInterval: SERVER_STATUS_REFETCH_INTERVAL_MS,
    retry: false,
  })

  const status = query.isPending
    ? 'connecting'
    : query.isError
      ? 'disconnected'
      : 'connected'

  return (
    <Badge
      variant={status === 'connected' ? 'secondary' : 'outline'}
      aria-label={`server ${status}`}
    >
      {status}
    </Badge>
  )
}
