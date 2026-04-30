import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { EmptyState } from '@/features/components/data-state'

export function BuffersPage() {
  return (
    <>
      <Header>
        <h1 className='truncate text-lg font-semibold'>Buffers</h1>
      </Header>
      <Main>
        <EmptyState title='No data loaded yet.' />
      </Main>
    </>
  )
}
