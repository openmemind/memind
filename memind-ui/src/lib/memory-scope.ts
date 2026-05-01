type ParsedMemoryScope = {
  memoryId: string
  userId: string
  agentId: string
  hasUserId: boolean
  hasAgentId: boolean
}

class MemoryScopeError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'MemoryScopeError'
  }
}

export function parseMemoryScope(value: string): ParsedMemoryScope {
  const memoryId = value.trim()
  const colonIndex = memoryId.indexOf(':')
  const userId = colonIndex === -1 ? memoryId : memoryId.slice(0, colonIndex)
  const agentId = colonIndex === -1 ? '' : memoryId.slice(colonIndex + 1)

  return {
    memoryId,
    userId,
    agentId,
    hasUserId: userId !== '',
    hasAgentId: agentId !== '',
  }
}

export function toMemoryIdQuery(value: string): { memoryId?: string } {
  const scope = parseMemoryScope(value)
  return scope.memoryId ? { memoryId: scope.memoryId } : {}
}

export function toUserAgentQuery(
  value: string
): { userId?: string; agentId?: string } {
  const scope = parseMemoryScope(value)
  if (!scope.hasUserId) return {}

  return {
    userId: scope.userId,
    ...(scope.hasAgentId ? { agentId: scope.agentId } : {}),
  }
}

export function requireUserScope(value: string): ParsedMemoryScope {
  const scope = parseMemoryScope(value)
  if (!scope.hasUserId) {
    throw new MemoryScopeError('Memory scope must include userId')
  }
  return scope
}

export function requireRetrieveScope(value: string): ParsedMemoryScope {
  const scope = parseMemoryScope(value)
  if (!scope.hasUserId || !scope.hasAgentId) {
    throw new MemoryScopeError('Memory scope must include userId and agentId')
  }
  return scope
}
