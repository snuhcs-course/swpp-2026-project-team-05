import type { AnalysisState } from '../lib/types'
import { cn } from '../lib/utils'

const labels: Record<AnalysisState, string> = {
  READY: '분석 완료',
  PARTIAL: '일부 분석',
  PROCESSING: '분석 중',
  NOT_STARTED: '분석 예정',
  FAILED: '분석 확인 중',
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
