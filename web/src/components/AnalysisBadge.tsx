import type { AnalysisState } from '../lib/types'
import { cn } from '../lib/utils'

const labels: Record<AnalysisState, string> = {
  READY: 'Analysis ready',
  PARTIAL: 'Partial analysis',
  PROCESSING: 'Analyzing',
  NOT_STARTED: 'Analysis pending',
  FAILED: 'Analysis unavailable',
}

export function AnalysisBadge({ state }: { state: AnalysisState }) {
  return (
    <span className={cn(
      'inline-flex items-center gap-1.5 text-xs font-semibold',
      state === 'READY' ? 'text-emerald-800' : 'text-zinc-500',
    )}>
      <span className={cn('size-1.5 rounded-full bg-zinc-400', state === 'READY' && 'bg-emerald-600')} />
      {labels[state]}
    </span>
  )
}
