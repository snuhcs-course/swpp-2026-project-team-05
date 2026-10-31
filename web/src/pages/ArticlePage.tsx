import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowUpRight, ChevronLeft, ChevronRight, LoaderCircle, Sparkles } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AnalysisBadge } from '../components/AnalysisBadge'
import { AppShell } from '../components/AppShell'
import type { Finding } from '../lib/types'
import { api } from '../lib/api'
import { segmentText, sourceRange } from '../lib/text'
import { categoryLabel, cn, formatDate } from '../lib/utils'

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
        <div className="mb-3 flex flex-wrap gap-1.5">
          {finding.categories.map((category) => <span key={category} className="bg-black px-2 py-1 text-[10px] font-bold tracking-wide text-white">{category}</span>)}
        </div>
        <p className="font-serif text-xl font-bold leading-7">“{finding.source.marker.text}”</p>
        <p className="mt-3 text-sm leading-6 text-zinc-600">{finding.explanation}</p>
      </div>
      <div className="py-5">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-xs font-black uppercase tracking-[0.12em]">Elsewhere in the coverage</h3>
          {comparisons > 1 && (
            <div className="flex items-center gap-2 text-xs font-bold">
              <button className="grid size-7 place-items-center border border-zinc-300 disabled:opacity-30" disabled={page === 0} onClick={() => setPage((value) => value - 1)} aria-label="Previous article"><ChevronLeft size={15} /></button>
              <span>{page + 1} / {comparisons}</span>
              <button className="grid size-7 place-items-center border border-zinc-300 disabled:opacity-30" disabled={page === comparisons - 1} onClick={() => setPage((value) => value + 1)} aria-label="Next article"><ChevronRight size={15} /></button>
            </div>
          )}
        </div>
        {comparisons ? <EvidenceQuote finding={finding} page={page} /> : <p className="border-y border-zinc-300 py-5 text-sm leading-6 text-zinc-500">No corresponding passage from another newsroom has been linked yet.</p>}
      </div>
    </div>
  )
}

export function ArticlePage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const token = session!.token
  const articleQuery = useQuery({ queryKey: ['article', id], queryFn: () => api.article(token, id), enabled: Boolean(id) })
  const analysisQuery = useQuery({ queryKey: ['analysis', id], queryFn: () => api.analysis(token, id), enabled: Boolean(id) })
  const findings = analysisQuery.data?.findings ?? []
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const selectedFinding = findings.find((finding) => finding.id === selectedId) ?? findings[0]
  const titleFindings = findings.filter((finding) => finding.source.scope === 'TITLE')
  const bodyFindings = findings.filter((finding) => finding.source.scope === 'BODY')

  if (articleQuery.isLoading) {
    return <AppShell><div className="flex min-h-[70vh] items-center justify-center gap-2 text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={18} /> Loading the article…</div></AppShell>
  }
  if (!articleQuery.data || articleQuery.isError) {
    return <AppShell><div className="mx-auto max-w-3xl px-6 py-24 text-center"><h1 className="font-serif text-3xl font-black">This article is unavailable.</h1><Link to="/" className="mt-6 inline-block font-bold underline">Return to the feed</Link></div></AppShell>
  }
  const article = articleQuery.data

  return (
    <AppShell>
      <main>
        <div className="mx-auto max-w-[1440px] px-5 sm:px-8">
          <div className="border-b border-zinc-300 py-5">
            <Link to={`/?category=${article.category}`} className="inline-flex items-center gap-2 text-sm font-bold"><ArrowLeft size={16} /> Back to {categoryLabel(article.category)}</Link>
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
                <div className="mt-7 flex items-center justify-between gap-4">
                  <p className="text-xs text-zinc-500">Select a starred phrase to explore the comparison.</p>
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
                    {findings.length > 1 && (
                      <div className="mb-5 flex gap-1 overflow-x-auto pb-2">
                        {findings.map((finding, index) => (
                          <button
                            key={finding.id}
                            className={cn('grid size-8 shrink-0 place-items-center border border-zinc-300 text-xs font-bold', selectedFinding.id === finding.id && 'border-black bg-black text-white')}
                            onClick={() => setSelectedId(finding.id)}
                            aria-label={`View finding ${index + 1}`}
                          >{index + 1}</button>
                        ))}
                      </div>
                    )}
                    <FindingPanel key={selectedFinding.id} finding={selectedFinding} />
                  </>
                ) : !analysisQuery.isLoading && (
                  <div className="border-y border-zinc-300 py-8">
                    <p className="font-serif text-xl font-bold">No findings are available yet.</p>
                    <p className="mt-3 text-sm leading-6 text-zinc-500">You can still read the full article. Its analysis will appear here once it is ready.</p>
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
