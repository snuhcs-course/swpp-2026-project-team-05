import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { api } from '../lib/api'
import { SavedArticlesPage } from './SavedArticlesPage'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    session: {
      token: 'token',
      user: { id: 'user-1', username: 'reader', email: 'reader@example.com', is_developer: false },
    },
    logout: vi.fn(),
  }),
}))

vi.mock('../lib/api', () => ({
  api: {
    savedArticles: vi.fn(),
    saveArticle: vi.fn(),
    removeSavedArticle: vi.fn(),
  },
}))

const savedArticle = {
  id: 'article-1',
  publisher: { code: 'yna', name: '연합뉴스', logo_url: '' },
  title: '저장한 기사',
  category: 'POLITICS' as const,
  published_at: '2026-11-02T10:00:00+09:00',
  analysis_state: 'READY' as const,
  is_saved: true,
  saved_at: '2026-11-02T17:00:00+09:00',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><SavedArticlesPage /></MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SavedArticlesPage', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('shows the personal list and removes an article through the shared control', async () => {
    vi.mocked(api.savedArticles)
      .mockResolvedValueOnce({ items: [savedArticle], next_cursor: null })
      .mockResolvedValue({ items: [], next_cursor: null })
    vi.mocked(api.removeSavedArticle).mockResolvedValue(undefined)
    renderPage()

    expect(await screen.findByText('저장한 기사')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Remove from saved articles' }))

    await waitFor(() => expect(api.removeSavedArticle).toHaveBeenCalledWith('token', 'article-1'))
    await waitFor(() => expect(screen.queryByText('저장한 기사')).not.toBeInTheDocument())
  })

  it('renders the empty state', async () => {
    vi.mocked(api.savedArticles).mockResolvedValue({ items: [], next_cursor: null })
    renderPage()
    expect(await screen.findByText('Your reading list is empty.')).toBeInTheDocument()
  })
})
