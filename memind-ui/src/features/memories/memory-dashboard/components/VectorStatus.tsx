import { Badge } from "@/components/ui/badge"

export function VectorStatus({ status }: { status: string }) {
  return (
    <Badge variant="outline">
      <span className="size-1.5 rounded-full bg-primary" />
      {status}
    </Badge>
  )
}
