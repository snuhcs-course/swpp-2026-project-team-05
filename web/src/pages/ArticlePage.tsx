import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowUpRight, ChevronLeft, ChevronRight, LoaderCircle, Sparkles } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AnalysisBadge } from '../components/AnalysisBadge'
import { AppShell } from '../components/AppShell'
import { SaveArticleButton } from '../components/SaveArticleButton'
import type { Finding } from '../lib/types'
import { api } from '../lib/api'
import { articleOrigin } from '../lib/navigation'
import { segmentText, sourceRange } from '../lib/text'
import { categoryLabel, cn, findingCategoryLabel, formatDate } from '../lib/utils'

function HighlightedText({ text, findings, selectedId, onSelect, className }: {
  text: string
  findings: Finding[]
  selectedId: string | null
  onSelect: (id: string) => void
  className?: string
}) {
  const segments = useMemo(() => segmentText(text, findings.map(sourceRange)), [text, findings])
  return (
    <div className={cn('whitespace-pre-wrap', className)}>
      {segments.map((segment, index) => {
        const findingId = segment.findingIds[0]
        if (!findingId) return <span key={index}>{segment.text}</span>
        return (
          <button
            key={index}
            className={cn(
              'relative rounded-[2px] bg-amber-200/75 px-0.5 text-left text-inherit decoration-2 underline-offset-4 hover:bg-amber-300 focus:outline-none focus:ring-2 focus:ring-black',
              selectedId === findingId && 'bg-amber-300 underline',
            )}
            onClick={() => onSelect(findingId)}
          >
            {segment.text}<sup className="ml-0.5 text-[10px] font-black">★</sup>
          </button>
        )
      })}
    </div>
  )
}

function EvidenceQuote({ finding, page }: { finding: Finding; page: number }) {
  const article = finding.comparison_articles[page]
  if (!article) return null
  const evidence = article.evidence[0]
  const points = Array.from(evidence.sentence.text)
  const before = points.slice(0, evidence.marker.start_in_sentence).join('')
  const marker = points.slice(evidence.marker.start_in_sentence, evidence.marker.end_in_sentence).join('')
  const after = points.slice(evidence.marker.end_in_sentence).join('')

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 border-b border-zinc-300 pb-3">
        <div>
          <strong className="text-sm">{article.publisher.name}</strong>
          <p className="mt-1 text-xs text-zinc-500">{formatDate(article.published_at)}</p>
        </div>
        <a href={article.original_url} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-xs font-bold">Original <ArrowUpRight size={13} /></a>
      </div>
      <h4 className="font-serif text-lg font-bold leading-6">{article.title}</h4>
      <blockquote className="mt-4 border-l-2 border-black pl-4 text-[15px] leading-7 text-zinc-700">
        {before}<mark className="bg-zinc-200 px-0.5 font-semibold text-black">{marker}</mark>{after}
      </blockquote>
      {evidence.comparison_summary && <p className="mt-4 text-sm leading-6 text-zinc-600">{evidence.comparison_summary}</p>}
    </div>
  )
}

function FindingPanel({ finding }: { finding: Finding }) {
  const [page, setPage] = useState(0)
  const comparisons = finding.comparison_articles.length

  return (
    <div className="animate-in">
      <div className="border-b border-black pb-5">
        <p className="mb-3 text-[11px] font-black uppercase tracking-[0.12em] text-zinc-600">
          {finding.categories.map(findingCategoryLabel).join(' · ')}
        </p>
        <p className="font-serif text-xl font-bold leading-7">“{finding.source.marker.text}”</p>
        <p className="mt-3 text-sm leading-6 text-zinc-600">{finding.explanation}</p>
      </div>
      <div className="py-5">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-xs font-black uppercase tracking-[0.12em]">Compared coverage</h3>
          <span className="text-xs text-zinc-500">{comparisons} {comparisons === 1 ? 'source' : 'sources'}</span>
        </div>
        {comparisons > 1 && (
          <div role="tablist" aria-label="Comparison sources" className="mb-5 flex gap-5 overflow-x-auto border-b border-zinc-300">
            {finding.comparison_articles.map((article, index) => (
              <button
                key={article.article_id}
                type="button"
                role="tab"
                aria-selected={page === index}
                onClick={() => setPage(index)}
                className={cn(
                  '-mb-px shrink-0 border-b-2 px-0.5 pb-2 text-xs font-bold transition-colors',
                  page === index ? 'border-black text-black' : 'border-transparent text-zinc-500 hover:text-black',
                )}
              >
                {article.publisher.name}
              </button>
            ))}
          </div>
        )}
        {comparisons ? <EvidenceQuote finding={finding} page={page} /> : <p className="border-y border-zinc-300 py-5 text-sm text-zinc-500">No comparison available.</p>}
      </div>
    </div>
  )
}

export function ArticlePage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { session } = useAuth()
  const token = session!.token
  const userId = session!.user.id
  const articleQuery = useQuery({ queryKey: ['article', userId, id], queryFn: () => api.article(token, id), enabled: Boolean(id) })
  const analysisQuery = useQuery({ queryKey: ['analysis', id], queryFn: () => api.analysis(token, id), enabled: Boolean(id) })
  const findings = analysisQuery.data?.findings ?? []
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const selectedFinding = findings.find((finding) => finding.id === selectedId) ?? findings[0]
  const selectedIndex = selectedFinding ? findings.findIndex((finding) => finding.id === selectedFinding.id) : -1
  const titleFindings = findings.filter((finding) => finding.source.scope === 'TITLE')
  const bodyFindings = findings.filter((finding) => finding.source.scope === 'BODY')

  if (articleQuery.isLoading) {
    return <AppShell><div className="flex min-h-[70vh] items-center justify-center gap-2 text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={18} /> Loading the article…</div></AppShell>
  }
  if (!articleQuery.data || articleQuery.isError) {
    return <AppShell><div className="mx-auto max-w-3xl px-6 py-24 text-center"><h1 className="font-serif text-3xl font-black">This article is unavailable.</h1><Link to="/" className="mt-6 inline-block font-bold underline">Return to the feed</Link></div></AppShell>
  }
  const article = articleQuery.data
  const origin = articleOrigin(location.state)
  const backLabel = origin === '/saved'
    ? 'Back to saved articles'
    : `Back to ${categoryLabel(article.category)}`
  const goBack = () => {
    if (origin) navigate(-1)
    else navigate(`/?category=${article.category}`, { replace: true })
  }

  return (
    <AppShell>
      <main>
        <div className="mx-auto max-w-[1440px] px-5 sm:px-8">
          <div className="border-b border-zinc-300 py-5">
            <button type="button" onClick={goBack} className="inline-flex items-center gap-2 text-sm font-bold"><ArrowLeft size={16} /> {backLabel}</button>
          </div>
          <div className="grid lg:grid-cols-[minmax(0,1fr)_380px] xl:grid-cols-[minmax(0,1fr)_430px]">
            <article className="min-w-0 py-9 lg:border-r lg:border-zinc-300 lg:py-14 lg:pr-12 xl:pr-20">
              <header className="border-b-2 border-black pb-9">
                <div className="mb-6 flex flex-wrap items-center gap-3 text-xs">
                  <strong className="text-sm">{article.publisher.name}</strong>
                  <span className="text-zinc-500">{categoryLabel(article.category)}</span>
                  <span className="text-zinc-500">{formatDate(article.published_at, true)}</span>
                  <AnalysisBadge state={article.analysis_state} />
                </div>
                <HighlightedText
                  text={article.title}
                  findings={titleFindings}
                  selectedId={selectedFinding?.id ?? null}
                  onSelect={setSelectedId}
                  className="font-serif text-[clamp(2.5rem,5vw,5.6rem)] font-black leading-[1.03] tracking-[-0.06em]"
                />
                <div className="mt-7 flex items-center justify-end gap-3">
                  <SaveArticleButton articleId={article.id} isSaved={article.is_saved} />
                  <a href={article.original_url} target="_blank" rel="noreferrer" className="flex shrink-0 items-center gap-1 text-xs font-bold">View original <ArrowUpRight size={14} /></a>
                </div>
              </header>
              <HighlightedText
                text={article.body}
                findings={bodyFindings}
                selectedId={selectedFinding?.id ?? null}
                onSelect={setSelectedId}
                className="article-body py-10"
              />
            </article>

            <aside className="border-t border-black py-8 lg:border-t-0 lg:py-14 lg:pl-8 xl:pl-10">
              <div className="sticky top-24">
                <div className="mb-6 flex items-center justify-between border-b-2 border-black pb-4">
                  <div className="flex items-center gap-2"><Sparkles size={17} /><h2 className="text-sm font-black">FrameLESS Analysis</h2></div>
                  <span className="text-xs text-zinc-500">{findings.length} {findings.length === 1 ? 'finding' : 'findings'}</span>
                </div>
                {analysisQuery.isLoading && <div className="flex items-center gap-2 py-8 text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={16} /> Loading the analysis…</div>}
                {analysisQuery.isError && <p className="py-6 text-sm text-red-700">The analysis could not be loaded.</p>}
                {selectedFinding ? (
                  <>
                    <div className="mb-5 flex items-center justify-between border-b border-zinc-300 pb-4">
                      <p className="text-xs font-bold text-zinc-600">Finding {selectedIndex + 1} of {findings.length}</p>
                      {findings.length > 1 && (
                        <div className="flex items-center gap-1">
                          <button
                            type="button"
                            className="grid size-8 place-items-center rounded-full hover:bg-zinc-100 disabled:opacity-25"
                            disabled={selectedIndex === 0}
                            onClick={() => setSelectedId(findings[selectedIndex - 1].id)}
                            aria-label="Previous finding"
                          ><ChevronLeft size={17} /></button>
                          <button
                            type="button"
                            className="grid size-8 place-items-center rounded-full hover:bg-zinc-100 disabled:opacity-25"
                            disabled={selectedIndex === findings.length - 1}
                            onClick={() => setSelectedId(findings[selectedIndex + 1].id)}
                            aria-label="Next finding"
                          ><ChevronRight size={17} /></button>
                        </div>
                      )}
                    </div>
                    <FindingPanel key={selectedFinding.id} finding={selectedFinding} />
                  </>
                ) : !analysisQuery.isLoading && (
                  <div className="border-y border-zinc-300 py-8">
                    <p className="font-serif text-xl font-bold">No findings are available yet.</p>
                  </div>
                )}
              </div>
            </aside>
          </div>
        </div>
      </main>
    </AppShell>
  )
}
