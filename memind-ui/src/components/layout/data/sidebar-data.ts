import {
  Database,
  FileText,
  GitBranch,
  Inbox,
  LayoutDashboard,
  Lightbulb,
  Network,
  SlidersHorizontal,
} from 'lucide-react'
import { type SidebarData } from '../types'

export const sidebarData: SidebarData = {
  navGroups: [
    {
      title: 'Memind',
      items: [
        { title: 'Dashboard', url: '/', icon: LayoutDashboard },
        { title: 'Buffers', url: '/buffers', icon: Inbox },
        { title: 'Raw Data', url: '/raw-data', icon: FileText },
        { title: 'Memory Items', url: '/items', icon: Database },
        { title: 'Item Graph', url: '/item-graph', icon: Network },
        { title: 'Memory Threads', url: '/memory-threads', icon: GitBranch },
        { title: 'Insight Tree', url: '/insights', icon: Lightbulb },
        { title: 'Settings', url: '/config', icon: SlidersHorizontal },
      ],
    },
  ],
}
