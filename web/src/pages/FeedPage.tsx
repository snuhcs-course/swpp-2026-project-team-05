import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { ArrowRight, Check, ChevronDown, Filter, LoaderCircle, Search, X } from 'lucide-react'
import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AnalysisBadge } from '../components/AnalysisBadge'
import { AppShell } from '../components/AppShell'
import { Button } from '../components/ui/Button'
import { api } from '../lib/api'
import type { ArticleSummary, NewsCategory } from '../lib/types'
import { categoryLabel, cn, formatDate } from '../lib/utils'

function ArticleRow({ article, index }: { article: ArticleSummary; index: number }) {
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
      <div className="flex items-center justify-between gap-5 sm:flex-col sm:items-end">
        <AnalysisBadge state={article.analysis_state} />
        <Link to={`/articles/${article.id}`} className="flex items-center gap-1 text-xs font-bold">Open <ArrowRight size={14} /></Link>
      </div>
    </article>
  )
}

export function FeedPage() {
  const { session } = useAuth()
  const token = session!.token
  const [params, setParams] = useSearchParams()
  const category = (params.get('category') === 'SOCIETY' ? 'SOCIETY' : 'POLITICS') as NewsCategory
  const queryInput = params.get('q') ?? ''
  const normalizedInput = queryInput.trim().replace(/\s+/g, ' ')
  const [activeQuery, setActiveQuery] = useState(
    normalizedInput.length >= 2 ? normalizedInput : '',
  )
  const [selectedPublishers, setSelectedPublishers] = useState<string[]>([])
  const [filtersOpen, setFiltersOpen] = useState(false)
  const queryTooShort = normalizedInput.length === 1
  const queryPending = normalizedInput.length >= 2 && activeQuery !== normalizedInput

  useEffect(() => {
    if (normalizedInput.length < 2) return
    const timer = window.setTimeout(() => setActiveQuery(normalizedInput), 300)
    return () => window.clearTimeout(timer)
  }, [normalizedInput])

  const publisherQuery = useQuery({
    queryKey: ['publishers'],
    queryFn: () => api.publishers(token),
  })

  const articlesQuery = useInfiniteQuery({
    queryKey: ['articles', category, [...selectedPublishers].sort(), activeQuery],
    queryFn: ({ pageParam }) => api.articles(token, {
      category,
      publishers: selectedPublishers,
      query: activeQuery || undefined,
      cursor: pageParam,
    }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.next_cursor ?? undefined,
    enabled: !queryTooShort,
  })

  const articles = useMemo(
    () => queryPending || queryTooShort
      ? []
      : articlesQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [articlesQuery.data, queryPending, queryTooShort],
  )
  const hero = articles[0]
  const rest = articles.slice(1)

  function chooseCategory(next: NewsCategory) {
    const updated = new URLSearchParams(params)
    updated.set('category', next)
    setParams(updated)
  }

  function updateQuery(value: string) {
    const updated = new URLSearchParams(params)
    if (value) updated.set('q', value)
    else updated.delete('q')
    setParams(updated, { replace: true })
    if (value.trim().length < 2) setActiveQuery('')
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    if (normalizedInput.length >= 2) setActiveQuery(normalizedInput)
  }

  function togglePublisher(code: string) {
    setSelectedPublishers((selected) => selected.includes(code)
      ? selected.filter((item) => item !== code)
      : [...selected, code])
  }

  return (
    <AppShell>
      <main className="mx-auto w-full max-w-[1440px] px-5 sm:px-8">
        <section className="border-b border-black py-8 lg:py-10">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <h1 className="font-serif text-5xl font-black tracking-[-0.055em] sm:text-7xl">Today’s Coverage</h1>
            <form className="relative w-full lg:max-w-md" role="search" onSubmit={submitSearch}>
              <Search className="pointer-events-none absolute left-0 top-1/2 -translate-y-1/2" size={19} aria-hidden="true" />
              <input
                type="search"
                aria-label="Search articles"
                placeholder="Search articles, publishers, or text"
                value={queryInput}
                onChange={(event) => updateQuery(event.target.value)}
                className="h-12 w-full border-0 border-b border-black bg-transparent pl-8 pr-10 text-sm font-medium outline-none placeholder:text-zinc-400 focus:border-b-2"
              />
              {queryInput && (
                <button
                  type="button"
                  aria-label="Clear search"
                  onClick={() => updateQuery('')}
                  className="absolute right-0 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center text-zinc-500 hover:text-black"
                >
                  <X size={18} />
                </button>
              )}
            </form>
          </div>
        </section>

        <div className="grid lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-10 xl:grid-cols-[240px_minmax(0,1fr)] xl:gap-14">
          <aside className="py-6 lg:border-r lg:border-zinc-300 lg:pr-7">
            <div className="flex items-center justify-between lg:block">
              <h2 className="text-sm font-bold">Section</h2>
              <button className="flex items-center gap-2 text-sm font-bold lg:hidden" onClick={() => setFiltersOpen((open) => !open)}>
                <Filter size={15} /> Filters <ChevronDown size={15} className={cn('transition-transform', filtersOpen && 'rotate-180')} />
              </button>
            </div>
            <div className={cn('mt-4', !filtersOpen && 'hidden lg:block')}>
              <div className="grid grid-cols-2 gap-px bg-zinc-300 lg:grid-cols-1">
                {(['POLITICS', 'SOCIETY'] as const).map((item) => (
                  <button
                    key={item}
                    className={cn('flex items-center justify-between bg-[var(--paper)] px-3 py-3 text-left text-sm font-semibold', category === item && 'bg-black text-white')}
                    onClick={() => chooseCategory(item)}
                  >
                    {categoryLabel(item)} {category === item && <Check size={15} />}
                  </button>
                ))}
              </div>
              <h2 className="mt-9 border-b border-black pb-3 text-sm font-bold">Publishers</h2>
              <div className="mt-2 space-y-1">
                {publisherQuery.data?.map((publisher) => (
                  <label key={publisher.code} className="flex cursor-pointer items-center gap-3 py-2 text-sm">
                    <input
                      type="checkbox"
                      className="size-4 accent-black"
                      checked={selectedPublishers.includes(publisher.code)}
                      onChange={() => togglePublisher(publisher.code)}
                    />
                    {publisher.name}
                  </label>
                ))}
                {publisherQuery.isLoading && <span className="text-sm text-zinc-400">Loading…</span>}
              </div>
              {selectedPublishers.length > 0 && (
                <button className="mt-4 text-xs font-bold underline underline-offset-4" onClick={() => setSelectedPublishers([])}>Show all publishers</button>
              )}
            </div>
          </aside>

          <section className="min-w-0 py-6 lg:py-9">
            <div className="mb-5 flex items-center justify-between border-b border-black pb-3">
              <h2 className="text-sm font-black">
                {activeQuery ? `Search results · ${categoryLabel(category)}` : `${categoryLabel(category)} Headlines`}
              </h2>
              <span className="text-xs text-zinc-500">
                {activeQuery ? `“${activeQuery}” · ${articles.length} loaded` : `Newest · ${articles.length} stories`}
              </span>
            </div>

            {queryTooShort && (
              <div className="border-y border-zinc-300 py-20 text-center text-sm text-zinc-500">Enter at least 2 characters to search.</div>
            )}
            {(queryPending || articlesQuery.isLoading) && !queryTooShort && (
              <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={18} /> Loading the latest coverage…</div>
            )}
            {articlesQuery.isError && !queryTooShort && (
              <div className="border border-red-300 p-8 text-center text-sm text-red-700">We could not load the feed. Please try again shortly.</div>
            )}
            {!activeQuery && hero && !queryPending && (
              <article className="border-b-2 border-black pb-8">
                <div className="mb-4 flex flex-wrap items-center gap-3 text-xs">
                  <strong>{hero.publisher.name}</strong>
                  <span className="text-zinc-500">{formatDate(hero.published_at, true)}</span>
                  <AnalysisBadge state={hero.analysis_state} />
                </div>
                <Link to={`/articles/${hero.id}`}>
                  <h2 className="max-w-4xl font-serif text-[clamp(2.25rem,4.5vw,4.8rem)] font-black leading-[1.03] tracking-[-0.055em] hover:underline hover:decoration-2 hover:underline-offset-8">
                    {hero.title}
                  </h2>
                </Link>
              </article>
            )}
            {activeQuery
              ? articles.map((article, index) => <ArticleRow key={article.id} article={article} index={index} />)
              : rest.map((article, index) => <ArticleRow key={article.id} article={article} index={index} />)}
            {!articlesQuery.isLoading && !queryPending && !queryTooShort && !articles.length && (
              <div className="border-y border-zinc-300 py-20 text-center text-sm text-zinc-500">
                {activeQuery ? `No stories found for “${activeQuery}”.` : 'No stories match these filters.'}
              </div>
            )}
            {articlesQuery.hasNextPage && !queryPending && !queryTooShort && (
              <Button variant="outline" className="mt-7 w-full" disabled={articlesQuery.isFetchingNextPage} onClick={() => void articlesQuery.fetchNextPage()}>
                {articlesQuery.isFetchingNextPage ? 'Loading…' : 'Load more stories'}
              </Button>
            )}
          </section>

        </div>
      </main>
    </AppShell>
  )
}
