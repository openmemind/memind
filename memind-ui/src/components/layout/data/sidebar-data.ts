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
