import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { EmptyState } from '@/features/components/data-state'

export function MemoryThreadsPage() {
  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Memory Threads</h1>
      </Header>
      <Main>
        <EmptyState title='No data loaded yet.' />
      </Main>
    </>
  )
}
