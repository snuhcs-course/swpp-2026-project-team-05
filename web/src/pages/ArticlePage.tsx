import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, ArrowUpRight, ChevronLeft, ChevronRight, LoaderCircle, Sparkles } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AnalysisBadge } from '../components/AnalysisBadge'
import { AppShell } from '../components/AppShell'
import { SaveArticleButton } from '../components/SaveArticleButton'
import type { EconomicComparison, EconomicLens, Finding } from '../lib/types'
import { api } from '../lib/api'
import { articleOrigin, comparisonArticleRouteState, comparisonFocus } from '../lib/navigation'
import { segmentText, sourceRange, type HighlightRange } from '../lib/text'
import {
  categoryLabel,
  channelLabel,
  cn,
  economicSourceLabel,
  economicToneLabel,
  economicVoiceLabel,
  findingCategoryLabel,
  formatDate,
} from '../lib/utils'

const ECONOMIC_PREFIX = 'economic:'

function economicRanges(lens: EconomicLens | null, scope: 'TITLE' | 'BODY'): HighlightRange[] {
  return (lens?.evidence ?? []).flatMap((evidence, index) => (
    evidence.sentence.scope === scope
      ? [{ id: `${ECONOMIC_PREFIX}${index}`, start: evidence.sentence.start, end: evidence.sentence.end }]
      : []
  ))
}

function HighlightedText({
  text, findings, selectedId, onSelect, focus = null, economic = [], selectedEconomic = null, onSelectEconomic, className,
}: {
  text: string
  findings: Finding[]
  selectedId: string | null
  onSelect: (id: string) => void
  focus?: { start: number; end: number } | null
  economic?: HighlightRange[]
  selectedEconomic?: number | null
  onSelectEconomic?: (index: number) => void
  className?: string
}) {
  const focusId = '__comparison_context__'
  const focusRef = useRef<HTMLElement | null>(null)
  const segments = useMemo(() => segmentText(text, [
    ...findings.map(sourceRange),
    ...economic,
    ...(focus ? [{ id: focusId, start: focus.start, end: focus.end }] : []),
  ]), [text, findings, economic, focus])

  useEffect(() => {
    focusRef.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
  }, [text, focus?.start, focus?.end])

  return (
    <div className={cn('whitespace-pre-wrap', className)}>
      {segments.map((segment, index) => {
        const findingId = segment.findingIds.find((id) => id !== focusId && !id.startsWith(ECONOMIC_PREFIX))
        const economicId = segment.findingIds.find((id) => id.startsWith(ECONOMIC_PREFIX))
        const economicIndex = economicId ? Number(economicId.slice(ECONOMIC_PREFIX.length)) : null
        const economicClass = economicIndex !== null && cn(
          'underline decoration-teal-600 decoration-2 underline-offset-[6px]',
          selectedEconomic === economicIndex && 'bg-teal-100',
        )
        const isComparisonContext = segment.findingIds.includes(focusId)
        if (!findingId && economicIndex !== null) return (
          <span
            key={index}
            role="button"
            tabIndex={0}
            data-economic-evidence={economicIndex}
            ref={isComparisonContext ? (node) => { focusRef.current = node } : undefined}
            className={cn(economicClass, 'cursor-pointer hover:bg-teal-50 focus:outline-none focus:ring-2 focus:ring-teal-700',
              isComparisonContext && 'ring-1 ring-violet-300')}
            onClick={() => onSelectEconomic?.(economicIndex)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                onSelectEconomic?.(economicIndex)
              }
            }}
          >{segment.text}</span>
        )
        if (!findingId) return (
          <span
            key={index}
            ref={isComparisonContext ? (node) => { focusRef.current = node } : undefined}
            className={cn(isComparisonContext && 'rounded-[2px] bg-violet-100 px-0.5 font-semibold ring-1 ring-violet-300')}
          >{segment.text}</span>
        )
        return (
          <button
            key={index}
            ref={isComparisonContext ? (node) => { focusRef.current = node } : undefined}
            className={cn(
              'relative rounded-[2px] bg-amber-200/75 px-0.5 text-left text-inherit decoration-2 underline-offset-4 hover:bg-amber-300 focus:outline-none focus:ring-2 focus:ring-black',
              selectedId === findingId && 'bg-amber-300 underline',
              isComparisonContext && 'ring-2 ring-violet-400',
              economicClass,
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

function EconomicSummary({ lens }: { lens: EconomicLens }) {
  const tones = lens.focus.map((channel) => lens.tone[channel]).filter(Boolean)
  const oneTone = tones.length > 0 && tones.every((tone) => tone === tones[0])
  return (
    <div className="mb-6 flex flex-wrap items-center gap-2 text-xs" aria-label="Economic lens summary">
      <span className="border border-teal-700 bg-teal-50 px-2 py-1 font-bold text-teal-900">
        Economic lens · {lens.focus.length ? lens.focus.map(channelLabel).join(' · ') : 'no clear focus'}
      </span>
      {tones.length > 0 && (
        <span className="border border-zinc-400 px-2 py-1 font-semibold text-zinc-700">
          Tone · {oneTone
            ? economicToneLabel(tones[0])
            : lens.focus.map((channel) => `${channelLabel(channel)}: ${economicToneLabel(lens.tone[channel] ?? 'OBSERVED')}`).join(' · ')}
        </span>
      )}
    </div>
  )
}

function sideName(name: string, isCurrent: boolean) {
  return <strong className={cn('font-bold', isCurrent && 'text-teal-900')}>{name}{isCurrent ? ' (this article)' : ''}</strong>
}

function AcrossCoverage({ comparison }: { comparison: EconomicComparison[] }) {
  if (!comparison.length) return null
  const rows = comparison.map((row, index) => {
    if (row.type === 'FOCUS') return (
      <div key={index} className="py-3">
        <dt className="text-[11px] font-black uppercase tracking-[0.12em] text-zinc-600">What each outlet puts first</dt>
        {row.sides.map((side) => (
          <dd key={side.article_id} className="mt-1 text-sm">
            {sideName(side.publisher.name, side.is_current)} · {(side.channels ?? []).map(channelLabel).join(' · ')}
          </dd>
        ))}
      </div>
    )
    if (row.type === 'TONE') return (
      <div key={index} className="py-3">
        <dt className="text-[11px] font-black uppercase tracking-[0.12em] text-zinc-600">
          Same effect, different tone · {channelLabel(row.channel ?? '')}
        </dt>
        {row.sides.map((side) => (
          <dd key={side.article_id} className="mt-2 text-sm leading-6">
            {sideName(side.publisher.name, side.is_current)} · {economicToneLabel(side.tone ?? 'OBSERVED')}
            {side.sentence && (
              <span className="mt-1 block border-l-2 border-zinc-300 pl-3 text-[13px] text-zinc-600">
                {side.is_current ? side.sentence.text : <Link to={`/articles/${side.article_id}`} className="hover:underline">{side.sentence.text}</Link>}
              </span>
            )}
          </dd>
        ))}
      </div>
    )
    if (row.type === 'SOURCES') return (
      <div key={index} className="py-3">
        <dt className="text-[11px] font-black uppercase tracking-[0.12em] text-zinc-600">Who is quoted</dt>
        {row.sides.map((side) => {
          const counts = Object.entries(side.counts ?? {}).sort((a, b) => b[1] - a[1])
          return (
            <dd key={side.article_id} className="mt-1 text-sm">
              {sideName(side.publisher.name, side.is_current)} · {counts.length
                ? counts.map(([source, count]) => `${economicSourceLabel(source)} ${count}`).join(' · ')
                : 'no attributed source'}
            </dd>
          )
        })}
      </div>
    )
    return (
      <div key={index} className="py-3">
        <dt className="text-[11px] font-black uppercase tracking-[0.12em] text-zinc-600">Numbers · {row.indicator}</dt>
        {row.sides.map((side) => (
          <dd key={side.article_id} className="mt-1 text-sm">
            {sideName(side.publisher.name, side.is_current)} · {(side.values ?? [])
              .map((value) => value.basis.length ? `${value.value} (${value.basis.join('·')})` : value.value).join(', ')}
          </dd>
        ))}
      </div>
    )
  })
  return (
    <div className="mt-6">
      <h3 className="border-b border-black pb-2 text-xs font-black uppercase tracking-[0.12em]">Across this event</h3>
      <dl className="divide-y divide-zinc-200">{rows}</dl>
    </div>
  )
}

function EconomicPanel({ lens, selected, onSelect }: {
  lens: EconomicLens
  selected: number
  onSelect: (index: number) => void
}) {
  const index = lens.evidence[selected] ? selected : 0
  const evidence = lens.evidence[index]
  const otherCoverage = evidence
    ? lens.comparison
      .filter((row) => row.type === 'TONE' && row.channel && evidence.channels.includes(row.channel))
      .flatMap((row) => row.sides.filter((side) => !side.is_current).map((side) => ({ channel: row.channel ?? '', side })))
    : []
  return (
    <section className="mt-10 border-t-2 border-teal-700 pt-5" aria-label="Economic lens">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-black text-teal-900">Economic lens</h2>
        {lens.evidence.length > 1 && (
          <div className="flex items-center gap-1 text-xs text-zinc-600">
            <button type="button" className="grid size-8 place-items-center rounded-full hover:bg-zinc-100 disabled:opacity-25"
              disabled={index === 0} onClick={() => onSelect(index - 1)} aria-label="Previous economic sentence"><ChevronLeft size={17} /></button>
            <span>Sentence {index + 1} of {lens.evidence.length}</span>
            <button type="button" className="grid size-8 place-items-center rounded-full hover:bg-zinc-100 disabled:opacity-25"
              disabled={index === lens.evidence.length - 1} onClick={() => onSelect(index + 1)} aria-label="Next economic sentence"><ChevronRight size={17} /></button>
          </div>
        )}
      </div>
      {evidence ? (
        <div className="border border-teal-700/40 bg-teal-50/60 p-4">
          <p className="text-[11px] font-black uppercase tracking-[0.12em] text-teal-900">
            {evidence.channels.map(channelLabel).join(' · ')} · {economicToneLabel(evidence.tone)}
          </p>
          <p className="mt-1 text-xs text-zinc-600">
            {evidence.role === 'EFFECT_FORECAST' ? 'Expected effect' : 'Reported effect'} · {economicVoiceLabel(evidence.voice)}
            {evidence.source ? ` · ${economicSourceLabel(evidence.source)}` : ''}
          </p>
          <blockquote className="mt-3 border-l-2 border-teal-700 pl-3 text-[15px] leading-7">{evidence.sentence.text}</blockquote>
          {otherCoverage.length > 0 && (
            <div className="mt-4 border-t border-teal-700/30 pt-3">
              <p className="text-xs font-bold text-zinc-700">Other coverage of the same effect</p>
              {otherCoverage.map(({ channel, side }) => (
                <div key={`${channel}-${side.article_id}`} className="mt-2 text-sm leading-6">
                  <strong>{side.publisher.name}</strong> · {channelLabel(channel)} · {economicToneLabel(side.tone ?? 'OBSERVED')}
                  {side.sentence && (
                    <Link to={`/articles/${side.article_id}`} className="mt-1 block text-[13px] text-zinc-600 hover:underline">
                      {side.sentence.text}
                    </Link>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <p className="border-y border-zinc-300 py-4 text-sm text-zinc-500">No sentence was selected as economic evidence.</p>
      )}
      <AcrossCoverage comparison={lens.comparison} />
    </section>
  )
}

function EvidenceQuote({ finding, page }: { finding: Finding; page: number }) {
  const location = useLocation()
  const article = finding.comparison_articles[page]
  if (!article) return null
  const evidence = article.evidence[0]
  const points = Array.from(evidence.sentence.text)
  const before = points.slice(0, evidence.marker.start_in_sentence).join('')
  const marker = points.slice(evidence.marker.start_in_sentence, evidence.marker.end_in_sentence).join('')
  const after = points.slice(evidence.marker.end_in_sentence).join('')

  return (
    <div>
      <div className="mb-4 border-b border-zinc-300 pb-4">
        <p className="text-xs text-zinc-500">{formatDate(article.published_at)}</p>
        <h4 className="mt-2 font-serif text-lg font-bold leading-6">{article.title}</h4>
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Link
            to={`/articles/${article.article_id}`}
            state={comparisonArticleRouteState(
              location.pathname,
              location.search,
              article.article_id,
              evidence,
            )}
            className="inline-flex min-h-10 items-center gap-2 border border-black bg-black px-4 py-2 text-xs font-bold text-white transition-colors hover:bg-white hover:text-black focus:outline-none focus:ring-2 focus:ring-black focus:ring-offset-2"
          >Read full article <ArrowRight size={14} /></Link>
          <a
            href={article.original_url}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-10 items-center gap-1 px-2 text-xs font-bold text-zinc-600 underline-offset-4 hover:text-black hover:underline focus:outline-none focus:ring-2 focus:ring-black"
          >Original <ArrowUpRight size={13} /></a>
        </div>
      </div>
      <blockquote className="border-l-2 border-black pl-4 text-[15px] leading-7 text-zinc-700">
        {before}<mark className="bg-zinc-200 px-0.5 font-semibold text-black">{marker}</mark>{after}
      </blockquote>
    </div>
  )
}

function FindingPanel({ finding }: { finding: Finding }) {
  const [page, setPage] = useState(0)
  const comparisons = finding.comparison_articles.length
  const overview = finding.comparison_overview.trim()
  const showFindingExplanation = comparisons === 0 || !overview || overview !== finding.explanation.trim()

  return (
    <div className="animate-in">
      <div className="border-b border-black pb-5">
        <p className="mb-3 text-[11px] font-black uppercase tracking-[0.12em] text-zinc-600">
          {finding.categories.map(findingCategoryLabel).join(' · ')}
        </p>
        <p className="font-serif text-xl font-bold leading-7">“{finding.source.marker.text}”</p>
        {showFindingExplanation && <p className="mt-3 text-sm leading-6 text-zinc-600">{finding.explanation}</p>}
      </div>
      <div className="py-5">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-xs font-black uppercase tracking-[0.12em]">Compared coverage</h3>
          <span className="text-xs text-zinc-500">{comparisons} {comparisons === 1 ? 'source' : 'sources'}</span>
        </div>
        {overview && (
          <p className="mb-5 border border-zinc-300 bg-zinc-50 px-4 py-3 text-sm leading-6 text-zinc-700">
            {overview}
          </p>
        )}
        {comparisons > 0 && (
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
  const token = session?.token
  const viewerId = session?.user.id ?? 'guest'
  const articleQuery = useQuery({ queryKey: ['article', viewerId, id], queryFn: () => api.article(token, id), enabled: Boolean(id) })
  const analysisQuery = useQuery({ queryKey: ['analysis', id], queryFn: () => api.analysis(token, id), enabled: Boolean(id) })
  const findings = analysisQuery.data?.findings ?? []
  const economic = analysisQuery.data?.economic ?? null
  const titleEconomic = useMemo(() => economicRanges(economic, 'TITLE'), [economic])
  const bodyEconomic = useMemo(() => economicRanges(economic, 'BODY'), [economic])
  const [selectedEconomic, setSelectedEconomic] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const focus = comparisonFocus(location.state, id)

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
    : origin?.startsWith('/articles/')
      ? 'Back to source article'
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
                {focus && (
                  <div className="mb-6 flex items-start gap-3 border-l-2 border-violet-400 bg-violet-50 px-3 py-2.5 text-xs leading-5 text-zinc-700">
                    <strong className="shrink-0 text-violet-900">Opened from comparison</strong>
                    <span>The referenced passage is highlighted in this article.</span>
                  </div>
                )}
                <div className="mb-6 flex flex-wrap items-center gap-3 text-xs">
                  <strong className="text-sm">{article.publisher.name}</strong>
                  <span className="text-zinc-500">{categoryLabel(article.category)}</span>
                  <span className="text-zinc-500">{formatDate(article.published_at, true)}</span>
                  <AnalysisBadge state={article.analysis_state} />
                </div>
                {economic && <EconomicSummary lens={economic} />}
                <HighlightedText
                  text={article.title}
                  findings={titleFindings}
                  selectedId={selectedFinding?.id ?? null}
                  onSelect={setSelectedId}
                  economic={titleEconomic}
                  selectedEconomic={selectedEconomic}
                  onSelectEconomic={setSelectedEconomic}
                  focus={focus?.scope === 'TITLE' ? { start: focus.start, end: focus.end } : null}
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
                economic={bodyEconomic}
                selectedEconomic={selectedEconomic}
                onSelectEconomic={setSelectedEconomic}
                focus={focus?.scope === 'BODY' ? { start: focus.start, end: focus.end } : null}
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
                    <p className="font-serif text-xl font-bold">
                      {focus ? 'No standalone findings for this article.' : 'No findings are available yet.'}
                    </p>
                    {focus && (
                      <p className="mt-3 text-sm leading-6 text-zinc-600">
                        The passage opened from compared coverage remains highlighted in the article.
                      </p>
                    )}
                  </div>
                )}
                {economic && <EconomicPanel lens={economic} selected={selectedEconomic} onSelect={setSelectedEconomic} />}
              </div>
            </aside>
          </div>
        </div>
      </main>
    </AppShell>
  )
}
