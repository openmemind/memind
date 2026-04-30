import {
  Database,
  FileText,
  GitBranch,
  Inbox,
  LayoutDashboard,
  Lightbulb,
  Network,
  Search,
  SlidersHorizontal,
} from 'lucide-react'
import { type SidebarData } from '../types'

export const sidebarData: SidebarData = {
  navGroups: [
    {
      title: 'Memind',
      items: [
        { title: 'Dashboard', url: '/', icon: LayoutDashboard },
        { title: 'Memory Items', url: '/items', icon: Database },
        { title: 'Raw Data', url: '/raw-data', icon: FileText },
        { title: 'Insights', url: '/insights', icon: Lightbulb },
        { title: 'Buffers', url: '/buffers', icon: Inbox },
        { title: 'Memory Threads', url: '/memory-threads', icon: GitBranch },
        { title: 'Item Graph', url: '/item-graph', icon: Network },
        { title: 'Config', url: '/config', icon: SlidersHorizontal },
        { title: 'Retrieve', url: '/retrieve', icon: Search },
      ],
    },
  ],
}
