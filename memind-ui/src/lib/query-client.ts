import { QueryCache, QueryClient } from '@tanstack/react-query'
import { handleServerError } from '@/lib/handle-server-error'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
      staleTime: 30 * 1000,
    },
    mutations: {
      retry: false,
      onError: handleServerError,
    },
  },
  queryCache: new QueryCache({
    onError: handleServerError,
  }),
})
