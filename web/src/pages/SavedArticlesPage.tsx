import { useInfiniteQuery } from '@tanstack/react-query'
import { Bookmark, LoaderCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ArticleRow } from '../components/ArticleRow'
import { AppShell } from '../components/AppShell'
import { Button } from '../components/ui/Button'
import { api } from '../lib/api'

export function SavedArticlesPage() {
  const { session } = useAuth()
  const token = session!.token
  const userId = session!.user.id
  const savedQuery = useInfiniteQuery({
    queryKey: ['saved-articles', userId],
    queryFn: ({ pageParam }) => api.savedArticles(token, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.next_cursor ?? undefined,
  })
  const articles = savedQuery.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <AppShell>
      <main className="mx-auto w-full max-w-[1100px] px-5 sm:px-8">
        <header className="border-b border-black py-9 sm:py-12">
          <div className="flex items-center gap-3 text-sm font-bold text-zinc-500">
            <Bookmark size={17} fill="currentColor" aria-hidden="true" /> Your reading list
          </div>
          <h1 className="mt-3 font-serif text-5xl font-black tracking-[-0.055em] sm:text-7xl">Saved Articles</h1>
          <p className="mt-4 max-w-xl text-sm leading-6 text-zinc-600">Articles you want to return to, most recently saved first.</p>
        </header>

        {savedQuery.isLoading && (
          <div className="flex min-h-[45vh] items-center justify-center gap-2 text-sm text-zinc-500">
            <LoaderCircle className="animate-spin" size={18} /> Loading saved articles…
          </div>
        )}

        {savedQuery.isError && articles.length === 0 && (
          <div className="py-20 text-center">
            <h2 className="font-serif text-3xl font-black">Saved articles could not be loaded.</h2>
            <Button className="mt-6" onClick={() => void savedQuery.refetch()}>Try again</Button>
          </div>
        )}

        {!savedQuery.isLoading && !savedQuery.isError && articles.length === 0 && (
          <div className="py-20 text-center">
            <Bookmark className="mx-auto text-zinc-300" size={34} aria-hidden="true" />
            <h2 className="mt-5 font-serif text-3xl font-black">Your reading list is empty.</h2>
            <p className="mt-3 text-sm text-zinc-500">Use the bookmark on an article to keep it here.</p>
            <Link to="/" className="mt-7 inline-block border-b border-black pb-1 text-sm font-bold">Browse today’s coverage</Link>
          </div>
        )}

        {articles.length > 0 && (
          <section className="py-5">
            {articles.map((article, index) => (
              <ArticleRow key={article.id} article={article} index={index} />
            ))}
            {savedQuery.hasNextPage && (
              <div className="border-t border-zinc-300 py-8 text-center">
                <Button
                  variant="outline"
                  disabled={savedQuery.isFetchingNextPage}
                  onClick={() => void savedQuery.fetchNextPage()}
                >
                  {savedQuery.isFetchingNextPage ? 'Loading…' : 'Load more'}
                </Button>
                {savedQuery.isFetchNextPageError && (
                  <p className="mt-3 text-sm text-red-700">The next page could not be loaded. Try again.</p>
                )}
              </div>
            )}
          </section>
        )}
      </main>
    </AppShell>
  )
}
