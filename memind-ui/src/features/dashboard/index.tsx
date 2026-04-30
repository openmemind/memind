import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'

export function DashboardPage() {
  return (
    <>
      <Header>
        <div className='min-w-0'>
          <h1 className='truncate text-lg font-semibold'>Memind UI</h1>
          <p className='truncate text-sm text-muted-foreground'>
            Local Memory Admin
          </p>
        </div>
      </Header>
      <Main>
        <div className='flex flex-col gap-2'>
          <h2 className='text-2xl font-semibold'>Dashboard</h2>
          <p className='text-sm text-muted-foreground'>
            No dashboard data loaded yet.
          </p>
        </div>
      </Main>
    </>
  )
}

export const Dashboard = DashboardPage
