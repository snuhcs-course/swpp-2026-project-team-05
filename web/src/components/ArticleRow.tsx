import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { ArticleSummary } from '../lib/types'
import { categoryLabel, formatDate } from '../lib/utils'
import { AnalysisBadge } from './AnalysisBadge'

export function ArticleRow({ article, index }: { article: ArticleSummary; index: number }) {
  return (
    <article className="group grid gap-4 border-t border-zinc-300 py-6 sm:grid-cols-[52px_minmax(0,1fr)_auto] sm:gap-6">
      <span className="hidden font-serif text-2xl text-zinc-300 sm:block">{String(index + 1).padStart(2, '0')}</span>
      <div>
        <div className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-zinc-500">
          <strong className="text-black">{article.publisher.name}</strong>
          <span>{categoryLabel(article.category)}</span>
          <span>{formatDate(article.published_at, true)}</span>
        </div>
        <Link to={`/articles/${article.id}`} className="block font-serif text-[22px] font-bold leading-[1.35] tracking-[-0.025em] decoration-1 underline-offset-4 group-hover:underline sm:text-[25px]">
          {article.title}
        </Link>
        {article.search_excerpt && (
          <p className="mt-3 line-clamp-2 max-w-3xl text-sm leading-6 text-zinc-600">
            {article.search_excerpt}
          </p>
        )}
      </div>
      <div className="flex items-center justify-between gap-3 sm:flex-col sm:items-end">
        <AnalysisBadge state={article.analysis_state} />
        <Link to={`/articles/${article.id}`} className="flex items-center gap-1 text-xs font-bold">Open <ArrowRight size={14} /></Link>
      </div>
    </article>
  )
}
