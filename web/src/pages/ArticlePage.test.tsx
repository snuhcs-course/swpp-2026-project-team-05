import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import type { Analysis, ArticleDetail, Finding } from '../lib/types'
import { ArticlePage } from './ArticlePage'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    session: {
      token: 'test-token',
      user: { id: 'user-1', username: 'reader', email: 'reader@example.com', is_developer: false },
    },
    logout: vi.fn(),
  }),
}))

vi.mock('../lib/api', () => ({
  api: {
    article: vi.fn(),
    analysis: vi.fn(),
    saveArticle: vi.fn(),
    removeSavedArticle: vi.fn(),
  },
}))

const article: ArticleDetail = {
  id: 'source-1',
  publisher: { code: 'source', name: 'Source News', logo_url: '' },
  title: 'A reviewed headline',
  body: 'Evaluative wording appears here. Another framing appears here.',
  original_url: 'https://example.com/source',
  category: 'POLITICS',
  published_at: '2026-11-02T10:00:00+09:00',
  analysis_state: 'READY',
  is_saved: false,
}

function comparison(articleId: string, publisher: string, title: string) {
  return {
    article_id: articleId,
    publisher: { code: articleId, name: publisher, logo_url: '' },
    title,
    published_at: '2026-11-02T11:00:00+09:00',
    original_url: `https://example.com/${articleId}`,
    body: 'The other article uses measured wording.',
    evidence: [{
      scope: 'BODY' as const,
      sentence: { start: 0, end: 40, text: 'The other article uses measured wording.' },
      marker: { start_in_sentence: 23, end_in_sentence: 39, text: 'measured wording' },
      target_owner: 'REPORTER',
      target_speaker_label: '',
      comparison_summary: 'The outlets characterize the same event differently.',
    }],
  }
}

const findings: Finding[] = [
  {
    id: 'finding-1',
    categories: ['E'],
    source: {
      scope: 'BODY',
      sentence: { start: 0, end: 32, text: 'Evaluative wording appears here.' },
      marker: { start_in_sentence: 0, end_in_sentence: 18, text: 'Evaluative wording' },
    },
    explanation: 'The wording carries an evaluation.',
    comparison_articles: [
      comparison('comparison-1', 'Yonhap News', 'Yonhap comparison'),
      comparison('comparison-2', 'MBC', 'MBC comparison'),
    ],
  },
  {
    id: 'finding-2',
    categories: ['C'],
    source: {
      scope: 'BODY',
      sentence: { start: 33, end: 62, text: 'Another framing appears here.' },
      marker: { start_in_sentence: 0, end_in_sentence: 15, text: 'Another framing' },
    },
    explanation: 'The action is characterized rather than simply reported.',
    comparison_articles: [],
  },
]

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/articles/source-1']}>
        <Routes>
          <Route path="/articles/:id" element={<ArticlePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ArticlePage analysis rail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.article).mockResolvedValue(article)
    vi.mocked(api.analysis).mockResolvedValue({ state: 'READY', version: 'test', findings } satisfies Analysis)
  })

  it('separates finding navigation from named comparison sources', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Finding 1 of 2')).toBeInTheDocument()
    expect(screen.getByText('Evaluation or emotion')).toBeInTheDocument()
    expect(screen.queryByText('E')).not.toBeInTheDocument()
    expect(screen.queryByText('1 / 2')).not.toBeInTheDocument()

    const yonhap = screen.getByRole('tab', { name: 'Yonhap News' })
    const mbc = screen.getByRole('tab', { name: 'MBC' })
    expect(yonhap).toHaveAttribute('aria-selected', 'true')
    await user.click(mbc)
    expect(mbc).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('MBC comparison')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next finding' }))
    expect(screen.getByText('Finding 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('Action characterization')).toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })
})
