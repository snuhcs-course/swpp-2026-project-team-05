import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LoaderCircle, Search, Settings2, X } from 'lucide-react'
import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ArticleRow } from '../components/ArticleRow'
import { AppShell } from '../components/AppShell'
import { ManageSourcesDialog } from '../components/ManageSourcesDialog'
import { UrlAnalysisMock } from '../components/UrlAnalysisMock'
import { Button } from '../components/ui/Button'
import { api } from '../lib/api'
import { orderFeedArticles } from '../lib/feedOrdering'
import { articleRouteState } from '../lib/navigation'
import type { ArticleSummary, FeedScope, NewsCategory, Publisher } from '../lib/types'
import { categoryLabel, cn, formatDate } from '../lib/utils'

type SearchFilter = 'ALL' | NewsCategory

export function FeedPage() {
  const { session } = useAuth()
  const token = session!.token
  const userId = session!.user.id
  const queryClient = useQueryClient()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const category = (params.get('category') === 'SOCIETY' ? 'SOCIETY' : 'POLITICS') as NewsCategory
  const scope = (params.get('scope') === 'following' ? 'FOLLOWING' : 'ALL') as FeedScope
  const [queryInput, setQueryInput] = useState('')
  const [isComposing, setIsComposing] = useState(false)
  const normalizedInput = queryInput.trim().replace(/\s+/g, ' ')
  const [activeQuery, setActiveQuery] = useState('')
  const [searchFilter, setSearchFilter] = useState<SearchFilter>('ALL')
  const [sourcesOpen, setSourcesOpen] = useState(false)
  const queryTooShort = !isComposing && normalizedInput.length === 1
  const queryPending = !isComposing && normalizedInput.length >= 2 && activeQuery !== normalizedInput

  useEffect(() => {
    if (!params.has('q')) return
    const updated = new URLSearchParams(params)
    updated.delete('q')
    setParams(updated, { replace: true })
  }, [params, setParams])

  useEffect(() => {
    if (isComposing || normalizedInput.length < 2) return
    const timer = window.setTimeout(() => setActiveQuery(normalizedInput), 300)
    return () => window.clearTimeout(timer)
  }, [isComposing, normalizedInput])

  const publisherQuery = useQuery({
    queryKey: ['publishers', userId],
    queryFn: () => api.publishers(token),
  })

  const articlesQuery = useInfiniteQuery({
    queryKey: ['articles', userId, category, scope],
    queryFn: ({ pageParam }) => api.articles(token, {
      category,
      scope,
      cursor: pageParam,
    }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.next_cursor ?? undefined,
    enabled: !activeQuery && !queryTooShort,
  })

  const searchQuery = useInfiniteQuery({
    queryKey: ['article-search', userId, searchFilter, activeQuery],
    queryFn: ({ pageParam }) => api.articles(token, {
      category: searchFilter === 'ALL' ? undefined : searchFilter,
      scope: 'ALL',
      query: activeQuery,
      cursor: pageParam,
    }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.next_cursor ?? undefined,
    enabled: Boolean(activeQuery) && !queryPending && !queryTooShort,
  })

  const followMutation = useMutation({
    mutationFn: (codes: string[]) => api.replaceFollowedPublishers(token, codes),
    onSuccess: async (codes) => {
      const followed = new Set(codes)
      queryClient.setQueryData<Publisher[]>(
        ['publishers', userId],
        (current = []) => current.map((publisher) => ({
          ...publisher,
          is_followed: followed.has(publisher.code),
        })),
      )
      await queryClient.invalidateQueries({ queryKey: ['articles', userId] })
      setSourcesOpen(false)
    },
  })

  const articles = useMemo(
    () => {
      if (queryPending || queryTooShort || activeQuery) return []
      const loaded = articlesQuery.data?.pages.flatMap((page) => page.items) ?? []
      return orderFeedArticles(loaded, scope, false)
    },
    [activeQuery, articlesQuery.data, queryPending, queryTooShort, scope],
  )
  const searchResults = searchQuery.data?.pages.flatMap((page) => page.items) ?? []
  const searchInitialLoading = Boolean(activeQuery) && searchQuery.isLoading
  const hero = articles[0]
  const rest = articles.slice(1)
  const followedCount = publisherQuery.data?.filter((publisher) => publisher.is_followed).length ?? 0

  function chooseScope(next: FeedScope) {
    const updated = new URLSearchParams(params)
    if (next === 'FOLLOWING') updated.set('scope', 'following')
    else updated.delete('scope')
    updated.delete('q')
    setParams(updated)
  }

  function updateQuery(value: string) {
    const nextNormalized = value.trim().replace(/\s+/g, ' ')
    if (!activeQuery && normalizedInput.length < 2 && nextNormalized.length >= 2) {
      setSearchFilter('ALL')
    }
    setQueryInput(value)
    if (value.trim().length < 2) setActiveQuery('')
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    if (isComposing) return
    if (normalizedInput.length >= 2) setActiveQuery(normalizedInput)
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
                onCompositionStart={() => setIsComposing(true)}
                onCompositionEnd={(event) => {
                  setQueryInput(event.currentTarget.value)
                  setIsComposing(false)
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && event.nativeEvent.isComposing) {
                    event.preventDefault()
                  }
                }}
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

        {!activeQuery && <section className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-300 py-4" aria-label="Feed scope">
          <div className="flex items-center gap-1 border border-zinc-300 p-1">
            <button
              type="button"
              aria-pressed={scope === 'ALL'}
              className={cn('px-4 py-2 text-sm font-bold', scope === 'ALL' && 'bg-black text-white')}
              onClick={() => chooseScope('ALL')}
            >
              Latest
            </button>
            <button
              type="button"
              aria-pressed={scope === 'FOLLOWING'}
              className={cn('px-4 py-2 text-sm font-bold', scope === 'FOLLOWING' && 'bg-black text-white')}
              onClick={() => chooseScope('FOLLOWING')}
            >
              Following
            </button>
          </div>
          <button type="button" className="flex items-center gap-2 text-sm font-bold underline decoration-zinc-400 underline-offset-4 hover:decoration-black" onClick={() => {
            followMutation.reset()
            setSourcesOpen(true)
          }}>
            <Settings2 size={16} /> Manage sources
          </button>
        </section>}

        {activeQuery && (
          <nav className="flex h-14 items-end gap-3 border-b border-zinc-300" aria-label="Search filters">
            {(['ALL', 'POLITICS', 'SOCIETY'] as const).map((filter) => (
              <button
                key={filter}
                type="button"
                aria-pressed={searchFilter === filter}
                className={cn(
                  'h-11 min-w-16 border-b-2 px-3 text-sm transition-colors',
                  searchFilter === filter
                    ? 'border-black font-bold text-black'
                    : 'border-transparent font-medium text-zinc-500 hover:text-black',
                )}
                onClick={() => setSearchFilter(filter)}
              >
                {filter === 'ALL' ? 'All' : categoryLabel(filter)}
              </button>
            ))}
          </nav>
        )}

        <div className="mx-auto max-w-5xl">
          <section className="min-w-0 py-6 lg:py-9">
            <div className="mb-5 flex items-center justify-between border-b border-black pb-3">
              <h2 className="text-sm font-black">
                {activeQuery
                  ? searchFilter === 'ALL' ? 'Search results' : `Search results · ${categoryLabel(searchFilter)}`
                  : `${scope === 'FOLLOWING' ? 'Following' : categoryLabel(category)} Headlines`}
              </h2>
              <span className="text-xs text-zinc-500">
                {activeQuery
                  ? `“${activeQuery}” · all sources`
                  : 'Newest first'}
              </span>
            </div>

            {queryTooShort && (
              <div className="border-y border-zinc-300 py-20 text-center text-sm text-zinc-500">Enter at least 2 characters to search.</div>
            )}
            {(queryPending || (!activeQuery && articlesQuery.isLoading) || searchInitialLoading) && !queryTooShort && (
              <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-zinc-500"><LoaderCircle className="animate-spin" size={18} /> Loading the latest coverage…</div>
            )}
            {!activeQuery && articlesQuery.isError && !queryTooShort && (
              <div className="border border-red-300 p-8 text-center text-sm text-red-700">We could not load the feed. Please try again shortly.</div>
            )}
            {!activeQuery && hero && !queryPending && (
              <article className="border-b-2 border-black pb-8">
                <div className="mb-4 flex flex-wrap items-center gap-3 text-xs">
                  <strong>{hero.publisher.name}</strong>
                  <span className="text-zinc-500">{formatDate(hero.published_at, true)}</span>
                </div>
                <Link to={`/articles/${hero.id}`} state={articleRouteState(location.pathname, location.search)}>
                  <h2 className="max-w-4xl font-serif text-[clamp(2.25rem,4.5vw,4.8rem)] font-black leading-[1.03] tracking-[-0.055em] hover:underline hover:decoration-2 hover:underline-offset-8">
                    {hero.title}
                  </h2>
                </Link>
              </article>
            )}
            {activeQuery && !queryPending && !searchInitialLoading && (
              <SearchResultSection
                filter={searchFilter}
                query={activeQuery}
                articles={searchResults}
                isLoading={searchQuery.isLoading}
                isError={searchQuery.isError}
                isFetchingNextPage={searchQuery.isFetchingNextPage}
                hasNextPage={searchQuery.hasNextPage}
                onRetry={() => void searchQuery.refetch()}
                onLoadMore={() => void searchQuery.fetchNextPage()}
              />
            )}
            {!activeQuery && rest.map((article, index) => <ArticleRow key={article.id} article={article} index={index} />)}
            {!activeQuery && !articlesQuery.isLoading && !queryPending && !queryTooShort && !articles.length && (
              <div className="border-y border-zinc-300 px-5 py-20 text-center text-sm text-zinc-500">
                {scope === 'FOLLOWING' && followedCount === 0 ? (
                  <>
                    <p>Choose a few trusted sources to build your Following feed.</p>
                    <Button className="mt-5" onClick={() => setSourcesOpen(true)}>Manage sources</Button>
                  </>
                ) : scope === 'FOLLOWING' ? (
                  <>
                    <p>Your followed sources have no {categoryLabel(category).toLowerCase()} stories right now.</p>
                    <Button variant="outline" className="mt-5" onClick={() => chooseScope('ALL')}>Browse Latest</Button>
                  </>
                ) : (
                  'No stories are available in this section.'
                )}
              </div>
            )}
            {!activeQuery && articlesQuery.hasNextPage && !queryPending && !queryTooShort && (
              <Button variant="outline" className="mt-7 w-full" disabled={articlesQuery.isFetchingNextPage} onClick={() => void articlesQuery.fetchNextPage()}>
                {articlesQuery.isFetchingNextPage ? 'Loading…' : 'Load more stories'}
              </Button>
            )}
          </section>

        </div>
      </main>
      {sourcesOpen && (
        <ManageSourcesDialog
          open
          publishers={publisherQuery.data ?? []}
          isSaving={followMutation.isPending}
          errorMessage={followMutation.isError ? 'Your source preferences could not be saved. Please try again.' : undefined}
          onClose={() => setSourcesOpen(false)}
          onSave={(codes) => followMutation.mutate(codes)}
        />
      )}
    </AppShell>
  )
}

interface SearchResultSectionProps {
  filter: SearchFilter
  query: string
  articles: ArticleSummary[]
  isLoading: boolean
  isError: boolean
  isFetchingNextPage: boolean
  hasNextPage: boolean
  onRetry: () => void
  onLoadMore: () => void
}

function SearchResultSection({
  filter,
  query,
  articles,
  isLoading,
  isError,
  isFetchingNextPage,
  hasNextPage,
  onRetry,
  onLoadMore,
}: SearchResultSectionProps) {
  const filterLabel = filter === 'ALL' ? 'all' : categoryLabel(filter).toLowerCase()
  return (
    <section aria-label={`${filterLabel} search results`}>
      {isLoading && (
        <div className="flex min-h-28 items-center justify-center gap-2 text-sm text-zinc-500">
          <LoaderCircle className="animate-spin" size={16} /> Searching this section…
        </div>
      )}
      {isError && (
        <div className="border-b border-zinc-300 py-8 text-sm text-red-700">
          <p>We could not search this section.</p>
          <Button variant="outline" className="mt-4" onClick={onRetry}>Try again</Button>
        </div>
      )}
      {!isLoading && !isError && articles.map((article, index) => (
        <ArticleRow key={article.id} article={article} index={index} />
      ))}
      {!isLoading && !isError && articles.length === 0 && (
        filter === 'ALL' ? (
          <div className="border-y border-zinc-300 px-5 py-12 text-center text-sm text-zinc-500">
            <UrlAnalysisMock key={query} query={query} />
          </div>
        ) : (
          <p className="border-b border-zinc-300 py-7 text-sm text-zinc-500">
            No matching {filterLabel} stories.
          </p>
        )
      )}
      {hasNextPage && !isError && (
        <Button variant="outline" className="mt-5 w-full" disabled={isFetchingNextPage} onClick={onLoadMore}>
          {isFetchingNextPage ? 'Loading…' : 'Load more stories'}
        </Button>
      )}
    </section>
  )
}
