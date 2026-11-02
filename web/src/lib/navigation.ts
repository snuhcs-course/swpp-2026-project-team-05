export type ArticleRouteState = { from: string }

export function articleRouteState(pathname: string, search: string): ArticleRouteState {
  return { from: `${pathname}${search}` }
}

export function articleOrigin(state: unknown): string | null {
  if (!state || typeof state !== 'object' || !('from' in state)) return null
  const from = (state as { from?: unknown }).from
  if (typeof from !== 'string') return null
  return from === '/saved' || from === '/' || from.startsWith('/?') ? from : null
}
