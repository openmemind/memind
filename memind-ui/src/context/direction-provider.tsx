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

import { useEffect, useState } from 'react'
import { DirectionProvider as RdxDirProvider } from '@radix-ui/react-direction'
import { getCookie } from '@/lib/cookies'

type Direction = 'ltr' | 'rtl'

const DEFAULT_DIRECTION = 'ltr'
const DIRECTION_COOKIE_NAME = 'dir'

export function DirectionProvider({ children }: { children: React.ReactNode }) {
  const [dir] = useState<Direction>(
    () => (getCookie(DIRECTION_COOKIE_NAME) as Direction) || DEFAULT_DIRECTION
  )

  useEffect(() => {
    const htmlElement = document.documentElement
    htmlElement.setAttribute('dir', dir)
  }, [dir])

  return <RdxDirProvider dir={dir}>{children}</RdxDirProvider>
}
