import { ArrowLeft } from "lucide-react"

import { Button } from "@/components/ui/button"

export function MobileBackBar({ onBack }: { onBack: () => void }) {
  return (
    <div className="mb-6 lg:hidden">
      <Button onClick={onBack} type="button" variant="ghost">
        <ArrowLeft data-icon="inline-start" />
        Back to Console
      </Button>
    </div>
  )
}
