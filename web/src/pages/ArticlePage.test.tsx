import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import type { Analysis, ArticleDetail, EconomicLens, Finding } from '../lib/types'
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
  content_hash: 'source-hash',
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
    comparison_overview: 'Source News, Yonhap News, and MBC frame the same event with different levels of force.',
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
    comparison_overview: 'Source News and KBS characterize the same action differently.',
    comparison_articles: [comparison('comparison-3', 'KBS', 'KBS comparison')],
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
    vi.mocked(api.analysis).mockResolvedValue({
      state: 'READY',
      version: 'test',
      source_content_hash: 'source-hash',
      findings,
    } satisfies Analysis)
  })

  it('separates finding navigation from named comparison sources', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Finding 1 of 2')).toBeInTheDocument()
    expect(screen.getByText('Evaluation or emotion')).toBeInTheDocument()
    expect(screen.queryByText('E')).not.toBeInTheDocument()
    expect(screen.queryByText('1 / 2')).not.toBeInTheDocument()
    expect(screen.getAllByText(findings[0].comparison_overview)).toHaveLength(1)
    expect(screen.queryByText('The outlets characterize the same event differently.')).not.toBeInTheDocument()

    const yonhap = screen.getByRole('tab', { name: 'Yonhap News' })
    const mbc = screen.getByRole('tab', { name: 'MBC' })
    expect(yonhap).toHaveAttribute('aria-selected', 'true')
    await user.click(mbc)
    expect(mbc).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('MBC comparison')).toBeInTheDocument()
    expect(screen.getAllByText('MBC')).toHaveLength(1)
    expect(screen.getAllByText(findings[0].comparison_overview)).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Next finding' }))
    expect(screen.getByText('Finding 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('Action characterization')).toBeInTheDocument()
    const singleSource = screen.getByRole('tab', { name: 'KBS' })
    expect(singleSource).toHaveAttribute('aria-selected', 'true')
    expect(screen.getAllByText('KBS')).toHaveLength(1)
    expect(screen.getByText('1 source')).toBeInTheDocument()
  })
})

describe('ArticlePage economic lens', () => {
  const body = '금리를 내렸다. 집값엔 큰 영향을 주지 않을 것이란 평가다. 소비가 살아날 것이라는 기대도 나온다.'
  const limited = '집값엔 큰 영향을 주지 않을 것이란 평가다.'
  const hopeful = '소비가 살아날 것이라는 기대도 나온다.'
  const at = (text: string) => ({ scope: 'BODY' as const, start: body.indexOf(text), end: body.indexOf(text) + text.length, text })
  const economy: ArticleDetail = { ...article, category: 'ECONOMY', title: '집값 또 오르나', body }
  const lens: EconomicLens = {
    focus: ['housing'],
    tone: { housing: 'LIMITED' },
    evidence: [
      { sentence: at(limited), channels: ['housing'], role: 'EFFECT_FORECAST', tone: 'LIMITED', voice: 'UNATTRIBUTED',
        source: 'ANONYMOUS', reasons: ['FOCUS'] },
      { sentence: at(hopeful), channels: ['macro_growth'], role: 'EFFECT_FORECAST', tone: 'OPTIMISM', voice: 'UNATTRIBUTED',
        source: 'ANONYMOUS', reasons: ['TONE_CONTRAST'] },
    ],
    sources: { ANONYMOUS: 2 },
    numbers: [],
    comparison: [{
      type: 'TONE',
      channel: 'housing',
      sides: [
        { article_id: 'source-1', publisher: article.publisher, title: economy.title, is_current: true, tone: 'LIMITED',
          sentence: at(limited) },
        { article_id: 'yna-1', publisher: { code: 'yna', name: '연합뉴스', logo_url: '' }, title: '한은 기준금리 인하',
          is_current: false, tone: 'CONCERN', sentence: { scope: 'BODY', start: 0, end: 20, text: '집값과 가계부채만 자극할 것이라는 우려도 있다.' } },
      ],
    }],
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.article).mockResolvedValue(economy)
    vi.mocked(api.analysis).mockResolvedValue({
      state: 'READY', version: 'test', source_content_hash: 'source-hash', findings: [], economic: lens,
    } satisfies Analysis)
  })

  it('summarises the lens, marks its sentences and shows the other outlet for the selected one', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Economic lens · Housing')).toBeInTheDocument()
    expect(screen.getByText('Tone · Limited effect')).toBeInTheDocument()
    const sentence = screen.getByText(limited, { selector: '[data-economic-evidence]' })
    expect(sentence).toHaveClass('decoration-teal-600')
    expect(screen.getByText('Other coverage of the same effect')).toBeInTheDocument()
    expect(screen.getAllByText('집값과 가계부채만 자극할 것이라는 우려도 있다.')[0].closest('a')).toHaveAttribute('href', '/articles/yna-1')
    expect(screen.getByText('Same effect, different tone · Housing')).toBeInTheDocument()

    await user.click(screen.getByText(hopeful, { selector: '[data-economic-evidence]' }))
    expect(screen.getByText('Sentence 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('Growth & demand · Optimism')).toBeInTheDocument()
    expect(screen.queryByText('Other coverage of the same effect')).not.toBeInTheDocument()
  })

  it('shows no economic lens for an article without one', async () => {
    vi.mocked(api.analysis).mockResolvedValue({
      state: 'READY', version: 'test', source_content_hash: 'source-hash', findings: [], economic: null,
    } satisfies Analysis)
    renderPage()
    expect(await screen.findByText('No findings are available yet.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Economic lens')).not.toBeInTheDocument()
  })
})

describe('ArticlePage comparison navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.article).mockImplementation(async (_token, id) => {
      if (id === 'comparison-2') {
        return {
          id,
          publisher: { code: 'comparison-2', name: 'MBC', logo_url: '' },
          title: 'MBC comparison',
          body: 'The other article uses measured wording.',
          original_url: 'https://example.com/comparison-2',
          content_hash: 'comparison-hash',
          category: 'POLITICS',
          published_at: '2026-11-02T11:00:00+09:00',
          analysis_state: 'READY',
          is_saved: false,
        }
      }
      return article
    })
    vi.mocked(api.analysis).mockImplementation(async (_token, id) => ({
      state: 'READY',
      version: 'test',
      source_content_hash: id === 'comparison-2' ? 'comparison-hash' : 'source-hash',
      findings: id === 'comparison-2' ? [] : findings,
    }))
  })

  it('opens a comparison target in Reader context and returns to its source', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Finding 1 of 2')
    await user.click(screen.getByRole('tab', { name: 'MBC' }))
    await user.click(screen.getByRole('link', { name: 'Read full article' }))

    expect(await screen.findByText('Opened from comparison')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Back to source article' })).toBeInTheDocument()
    expect(screen.getByText('measured wording')).toHaveClass('bg-violet-100')
    expect(screen.getByRole('button', { name: 'Save article' })).toBeInTheDocument()
    expect(screen.getByText('No standalone findings for this article.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Back to source article' }))
    expect(await screen.findByText('A reviewed headline')).toBeInTheDocument()
    expect(screen.getByText('Finding 1 of 2')).toBeInTheDocument()
  })
})
