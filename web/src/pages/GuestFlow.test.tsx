import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import type { Analysis, ArticleDetail } from '../lib/types'
import { ArticlePage } from './ArticlePage'
import { FeedPage } from './FeedPage'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    session: null,
    loading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }),
}))

vi.mock('../lib/api', () => ({
  api: {
    publishers: vi.fn(),
    articles: vi.fn(),
    article: vi.fn(),
    analysis: vi.fn(),
    replaceFollowedPublishers: vi.fn(),
    saveArticle: vi.fn(),
    removeSavedArticle: vi.fn(),
  },
}))

function LoginDestination() {
  const location = useLocation()
  return <output aria-label="login-return-path">{location.state?.from ?? ''}</output>
}

function renderRoute(path: string, element: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={path.startsWith('/articles/') ? '/articles/:id' : '/'} element={element} />
          <Route path="/login" element={<LoginDestination />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('guest news flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.publishers).mockResolvedValue([])
    vi.mocked(api.articles).mockResolvedValue({ items: [], next_cursor: null })
  })

  it('loads Latest without a token and sends Following to contextual sign-in', async () => {
    const user = userEvent.setup()
    renderRoute('/?category=POLITICS', <FeedPage />)

    await waitFor(() => expect(api.articles).toHaveBeenCalledWith(
      undefined,
      expect.objectContaining({ category: 'POLITICS', scope: 'ALL' }),
    ))
    await user.click(screen.getByRole('button', { name: 'Following' }))

    expect(screen.getByLabelText('login-return-path')).toHaveTextContent('/?category=POLITICS')
    expect(api.articles).not.toHaveBeenCalledWith(
      undefined,
      expect.objectContaining({ scope: 'FOLLOWING' }),
    )
  })

  it('reads an article and analysis as guest, then protects the save action', async () => {
    const user = userEvent.setup()
    const article: ArticleDetail = {
      id: 'guest-article',
      publisher: { code: 'yna', name: 'Yonhap News', logo_url: '' },
      title: 'Guest-readable article',
      body: 'The complete article remains readable without an account.',
      original_url: 'https://example.com/guest-article',
      content_hash: 'guest-hash',
      category: 'POLITICS',
      published_at: '2026-11-14T12:00:00+09:00',
      analysis_state: 'READY',
      is_saved: false,
    }
    vi.mocked(api.article).mockResolvedValue(article)
    vi.mocked(api.analysis).mockResolvedValue({
      state: 'READY',
      version: 'reviewed-v1',
      source_content_hash: 'guest-hash',
      findings: [],
    } satisfies Analysis)

    renderRoute('/articles/guest-article', <ArticlePage />)

    expect(await screen.findByText('Guest-readable article')).toBeInTheDocument()
    expect(api.article).toHaveBeenCalledWith(undefined, 'guest-article')
    expect(api.analysis).toHaveBeenCalledWith(undefined, 'guest-article')
    await user.click(screen.getByRole('button', { name: 'Save article' }))

    expect(screen.getByLabelText('login-return-path')).toHaveTextContent('/articles/guest-article')
    expect(api.saveArticle).not.toHaveBeenCalled()
  })
})
