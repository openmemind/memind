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

import { Button } from "@/components/ui/button"
import { siGithub } from "simple-icons"
import { ButtonGroup } from "@/components/ui/button-group.tsx"
import { Star } from "lucide-react"
import { useEffect, useState } from "react"
function GitHubIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4 fill-current">
      <path d={siGithub.path} />
    </svg>
  )
}

async function fetchStarCount(): Promise<number> {
  const resp = await fetch(
    "https://api.github.com/repos/openmemind/memind"
  )
  const json = await resp.json()
  return json["stargazers_count"]
}

export function GithubButton() {
  const [starCount, setStarCount] = useState<number | null>()
  useEffect(() => {
    fetchStarCount().then(setStarCount).catch(console.error)
  }, [])
  return (
    <a href={"https://github.com/openmemind/memind"} target={"_blank"}>
      <ButtonGroup>
        <Button variant={"outline"} size="lg">
          <GitHubIcon />
        </Button>
        <Button variant={"outline"} size="lg">
          <Star /> {starCount}
        </Button>
      </ButtonGroup>
    </a>
  )
}
