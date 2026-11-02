import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import type { ArticlePage } from '../lib/types'
import { useSavedArticle } from './useSavedArticle'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    session: { token: 'token', user: { id: 'user-1' } },
  }),
}))

vi.mock('../lib/api', () => ({
  api: {
    saveArticle: vi.fn(),
    removeSavedArticle: vi.fn(),
  },
}))

const page: ArticlePage = {
  items: [{
    id: 'article-1',
    publisher: { code: 'yna', name: '연합뉴스', logo_url: '' },
    title: '기사',
    category: 'POLITICS',
    published_at: '2026-11-02T10:00:00+09:00',
    analysis_state: 'READY',
    is_saved: false,
  }],
  next_cursor: null,
}

function Harness() {
  const mutation = useSavedArticle()
  return <button onClick={() => mutation.mutate({ articleId: 'article-1', isSaved: false })}>Save</button>
}

function renderHookHarness(client: QueryClient) {
  client.setQueryData(['articles', 'user-1'], { pages: [page], pageParams: [undefined] })
  render(<QueryClientProvider client={client}><Harness /></QueryClientProvider>)
}

function savedState(client: QueryClient) {
  const data = client.getQueryData<{ pages: ArticlePage[] }>(['articles', 'user-1'])
  return data?.pages[0].items[0].is_saved
}

describe('useSavedArticle', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('updates personalized article caches before the save request completes', async () => {
    let resolve!: () => void
    vi.mocked(api.saveArticle).mockReturnValue(new Promise((done) => { resolve = () => done({
      article_id: 'article-1', is_saved: true, saved_at: '2026-11-02T17:00:00+09:00',
    }) }))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHookHarness(client)

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(savedState(client)).toBe(true))
    resolve()
  })

  it('restores cached state when the save request fails', async () => {
    vi.mocked(api.saveArticle).mockRejectedValue(new Error('failed'))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHookHarness(client)

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(api.saveArticle).toHaveBeenCalled())
    await waitFor(() => expect(savedState(client)).toBe(false))
  })
})
