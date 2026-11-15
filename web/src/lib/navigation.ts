import type { Evidence } from './types'

export interface ComparisonFocus {
  articleId: string
  scope: Evidence['scope']
  start: number
  end: number
}

export type ArticleRouteState = {
  from: string
  comparisonFocus?: ComparisonFocus
}

export function articleRouteState(pathname: string, search: string): ArticleRouteState {
  return { from: `${pathname}${search}` }
}

export function comparisonArticleRouteState(
  pathname: string,
  search: string,
  articleId: string,
  evidence: Evidence,
): ArticleRouteState {
  const start = evidence.sentence.start + evidence.marker.start_in_sentence
  const end = evidence.sentence.start + evidence.marker.end_in_sentence
  return {
    from: `${pathname}${search}`,
    comparisonFocus: { articleId, scope: evidence.scope, start, end },
  }
}

export function articleOrigin(state: unknown): string | null {
  if (!state || typeof state !== 'object' || !('from' in state)) return null
  const from = (state as { from?: unknown }).from
  if (typeof from !== 'string') return null
  return from === '/saved' || from === '/' || from.startsWith('/?') ||
    /^\/articles\/[A-Za-z0-9-]+$/.test(from) ? from : null
}

export function comparisonFocus(state: unknown, articleId: string): ComparisonFocus | null {
  if (!state || typeof state !== 'object' || !('comparisonFocus' in state)) return null
  const focus = (state as { comparisonFocus?: unknown }).comparisonFocus
  if (!focus || typeof focus !== 'object') return null
  const candidate = focus as Partial<ComparisonFocus>
  if (candidate.articleId !== articleId ||
      (candidate.scope !== 'TITLE' && candidate.scope !== 'BODY') ||
      !Number.isInteger(candidate.start) || !Number.isInteger(candidate.end) ||
      Number(candidate.start) < 0 || Number(candidate.end) <= Number(candidate.start)) return null
  return candidate as ComparisonFocus
}
