import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { ArrowRight, Check, ChevronDown, Filter, LoaderCircle } from 'lucide-react'
import { useMemo, useState } from 'react'
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
  const [selectedPublishers, setSelectedPublishers] = useState<string[]>([])
  const [filtersOpen, setFiltersOpen] = useState(false)

  const publisherQuery = useQuery({
    queryKey: ['publishers'],
    queryFn: () => api.publishers(token),
  })

  const articlesQuery = useInfiniteQuery({
    queryKey: ['articles', category, selectedPublishers],
    queryFn: ({ pageParam }) => api.articles(token, category, selectedPublishers, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.next_cursor ?? undefined,
  })

  const articles = useMemo(
    () => articlesQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [articlesQuery.data],
  )
  const hero = articles[0]
  const rest = articles.slice(1)

  function chooseCategory(next: NewsCategory) {
    setParams({ category: next })
  }

  function togglePublisher(code: string) {
    setSelectedPublishers((selected) => selected.includes(code)
      ? selected.filter((item) => item !== code)
      : [...selected, code])
  }

  return (
    <AppShell>
      <main className="mx-auto w-full max-w-[1440px] px-5 sm:px-8">
        <section className="flex flex-col gap-6 border-b border-black py-9 sm:flex-row sm:items-end sm:justify-between lg:py-12">
          <div>
            <p className="mb-2 text-xs font-bold uppercase tracking-[0.2em]">Politics & Society</p>
            <h1 className="font-serif text-5xl font-black tracking-[-0.055em] sm:text-7xl">Today’s Coverage</h1>
          </div>
          <p className="max-w-xl text-sm leading-6 text-zinc-600 sm:text-right">
            Read the news from your preferred publisher, then examine the language and framing that distinguish one account from another.
          </p>
        </section>

        <div className="grid lg:grid-cols-[220px_minmax(0,1fr)_230px] lg:gap-9 xl:grid-cols-[240px_minmax(0,1fr)_260px] xl:gap-12">
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
              <h2 className="text-sm font-black">{categoryLabel(category)} Headlines</h2>
              <span className="text-xs text-zinc-500">Newest · {articles.length} stories</span>
            </div>

            {articlesQuery.isLoading && (
              <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={18} /> Loading the latest coverage…</div>
            )}
            {articlesQuery.isError && (
              <div className="border border-red-300 p-8 text-center text-sm text-red-700">We could not load the feed. Please try again shortly.</div>
            )}
            {hero && (
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
                <Link to={`/articles/${hero.id}`} className="mt-7 inline-flex items-center gap-2 text-sm font-black">Read with analysis <ArrowRight size={17} /></Link>
              </article>
            )}
            {rest.map((article, index) => <ArticleRow key={article.id} article={article} index={index} />)}
            {!articlesQuery.isLoading && !articles.length && (
              <div className="border-y border-zinc-300 py-20 text-center text-sm text-zinc-500">No stories match these filters.</div>
            )}
            {articlesQuery.hasNextPage && (
              <Button variant="outline" className="mt-7 w-full" disabled={articlesQuery.isFetchingNextPage} onClick={() => void articlesQuery.fetchNextPage()}>
                {articlesQuery.isFetchingNextPage ? 'Loading…' : 'Load more stories'}
              </Button>
            )}
          </section>

          <aside className="hidden py-9 lg:block">
            <div className="sticky top-24">
              <p className="border-b border-black pb-3 text-xs font-bold uppercase tracking-[0.18em]">How to read</p>
              <ol className="divide-y divide-zinc-300">
                {[
                  ['01', 'Choose a story from a publisher you know.'],
                  ['02', 'Select a marked phrase in the article.'],
                  ['03', 'Compare how other newsrooms covered it.'],
                ].map(([number, text]) => (
                  <li key={number} className="grid grid-cols-[32px_1fr] gap-3 py-4 text-sm leading-6">
                    <span className="font-serif text-zinc-400">{number}</span><span>{text}</span>
                  </li>
                ))}
              </ol>
              <div className="mt-8 bg-black p-5 text-white">
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-zinc-400">Analysis</p>
                <p className="mt-3 font-serif text-xl font-bold leading-7">A star is an invitation to compare—not a verdict.</p>
              </div>
            </div>
          </aside>
        </div>
      </main>
    </AppShell>
  )
}
