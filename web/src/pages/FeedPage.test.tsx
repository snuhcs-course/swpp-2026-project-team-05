import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import type { ArticleSummary } from '../lib/types'
import { orderFeedArticles } from '../lib/feedOrdering'
import { FeedPage } from './FeedPage'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    session: {
      token: 'test-token',
      user: {
        id: 'user-1',
        username: 'admin',
        email: 'admin@example.com',
        is_developer: true,
      },
    },
    logout: vi.fn(),
  }),
}))

vi.mock('../lib/api', () => ({
  api: {
    publishers: vi.fn(),
    articles: vi.fn(),
    replaceFollowedPublishers: vi.fn(),
  },
}))

function LocationProbe() {
  return <output aria-label="location-search">{useLocation().search}</output>
}

function renderFeed(initialEntry = '/?category=POLITICS') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <FeedPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('FeedPage search', () => {
  beforeEach(() => {
    vi.mocked(api.publishers).mockResolvedValue([
      { code: 'yna', name: 'Yonhap News', logo_url: '', is_followed: false },
      { code: 'mbc', name: 'MBC', logo_url: '', is_followed: true },
    ])
    vi.mocked(api.articles).mockResolvedValue({ items: [], next_cursor: null })
    vi.mocked(api.replaceFollowedPublishers).mockResolvedValue(['yna', 'mbc'])
  })

  it('keeps IME input local and searches only after composition ends', async () => {
    renderFeed()
    const input = screen.getByRole('searchbox', { name: 'Search articles' })

    await waitFor(() => expect(api.articles).toHaveBeenCalledWith(
      'test-token',
      expect.objectContaining({ category: 'POLITICS', scope: 'ALL' }),
    ))
    vi.mocked(api.articles).mockClear()

    fireEvent.compositionStart(input)
    fireEvent.change(input, { target: { value: 'ㅈ' } })
    fireEvent.change(input, { target: { value: '중수청' } })

    await new Promise((resolve) => window.setTimeout(resolve, 350))
    expect(api.articles).not.toHaveBeenCalled()
    expect(screen.getByLabelText('location-search')).toHaveTextContent('?category=POLITICS')
    expect(screen.getByLabelText('location-search')).not.toHaveTextContent('q=')

    fireEvent.compositionEnd(input, { data: '중수청' })

    await waitFor(() => expect(api.articles).toHaveBeenCalledWith(
      'test-token',
      expect.objectContaining({ query: '중수청' }),
    ), { timeout: 1000 })
    expect(screen.getByLabelText('location-search')).toHaveTextContent('?category=POLITICS')
    expect(screen.getByLabelText('location-search')).not.toHaveTextContent('q=')
  })

  it('keeps Latest complete and requests Following only when selected', async () => {
    const user = userEvent.setup()
    renderFeed()

    await waitFor(() => expect(api.articles).toHaveBeenCalledWith(
      'test-token',
      expect.objectContaining({ scope: 'ALL' }),
    ))
    vi.mocked(api.articles).mockClear()

    await user.click(screen.getByRole('button', { name: 'Following' }))

    await waitFor(() => expect(api.articles).toHaveBeenCalledWith(
      'test-token',
      expect.objectContaining({ scope: 'FOLLOWING' }),
    ))
    expect(screen.getByLabelText('location-search')).toHaveTextContent('scope=following')
  })

  it('keeps section navigation in the global header only', async () => {
    renderFeed()
    await waitFor(() => expect(api.articles).toHaveBeenCalled())

    expect(screen.getAllByRole('link', { name: 'Politics' })).toHaveLength(1)
    expect(screen.getAllByRole('link', { name: 'Society' })).toHaveLength(1)
    expect(screen.queryByRole('heading', { name: 'Section' })).not.toBeInTheDocument()
  })

  it('quietly moves reviewed examples first only in Latest', () => {
    const row = (id: string, title: string): ArticleSummary => ({
      id,
      title,
      publisher: { code: 'source', name: 'Source', logo_url: '' },
      category: 'SOCIETY',
      published_at: '2026-11-01T12:00:00+09:00',
      analysis_state: 'READY',
      is_saved: false,
    })
    const newest = row('newest', '가장 최근 일반 기사')
    const panda = row('panda', "에버랜드 막내 판다 '슈바오' 30일 첫 나들이…쌍둥이 언니들은 작별 준비")
    const hd = row('hd', "HD현대일렉트릭, 3분기 매출 1조1799억원 '역대 최대'")
    const ordinary = row('ordinary', '두 번째 일반 기사')
    const serverOrder = [newest, panda, ordinary, hd]

    expect(orderFeedArticles(serverOrder, 'ALL', false).map((article) => article.id))
      .toEqual(['hd', 'panda', 'newest', 'ordinary'])
    expect(orderFeedArticles(serverOrder, 'ALL', true)).toEqual(serverOrder)
    expect(orderFeedArticles(serverOrder, 'FOLLOWING', false)).toEqual(serverOrder)
  })

  it('keeps feed rows free of analysis status and duplicate open actions', async () => {
    const article = (id: string, title: string): ArticleSummary => ({
      id,
      title,
      publisher: { code: 'yna', name: 'Yonhap News', logo_url: '' },
      category: 'POLITICS',
      published_at: '2026-11-01T12:00:00+09:00',
      analysis_state: 'READY',
      is_saved: false,
    })
    vi.mocked(api.articles).mockResolvedValueOnce({
      items: [article('hero', '첫 번째 기사'), article('row', '두 번째 기사')],
      next_cursor: null,
    })

    renderFeed()
    expect(await screen.findByText('첫 번째 기사')).toBeInTheDocument()
    expect(screen.getByText('두 번째 기사')).toBeInTheDocument()
    expect(screen.queryByText('Analysis ready')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^Open/ })).not.toBeInTheDocument()
  })

  it('searches the full corpus even when Following is selected', async () => {
    renderFeed('/?category=POLITICS&scope=following')
    const input = screen.getByRole('searchbox', { name: 'Search articles' })

    fireEvent.change(input, { target: { value: '중수청' } })

    await waitFor(() => expect(api.articles).toHaveBeenLastCalledWith(
      'test-token',
      expect.objectContaining({ scope: 'ALL', query: '중수청' }),
    ), { timeout: 1000 })
  })

  it('searches both categories, keeps category navigation, and shows exact counts', async () => {
    const article = (id: string, title: string, category: 'POLITICS' | 'SOCIETY'): ArticleSummary => ({
      id,
      title,
      category,
      publisher: { code: 'yna', name: 'Yonhap News', logo_url: '' },
      published_at: '2026-11-05T12:00:00+09:00',
      analysis_state: 'READY',
      is_saved: false,
    })
    vi.mocked(api.articles).mockImplementation(async (_token, options) => {
      if (!options.query) return { items: [], next_cursor: null }
      if (options.category === 'POLITICS') {
        return {
          items: [article('politics-1', '정치 검색 결과', 'POLITICS')],
          next_cursor: null,
          total_count: 7,
        }
      }
      return {
        items: [article('society-1', '사회 검색 결과', 'SOCIETY')],
        next_cursor: null,
        total_count: 3,
      }
    })

    renderFeed('/?category=POLITICS&scope=following')
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search articles' }), {
      target: { value: '공통 검색어' },
    })

    expect(await screen.findByText('정치 검색 결과')).toBeInTheDocument()
    expect(screen.queryByText('사회 검색 결과')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Politics 7 results')).toHaveTextContent('7')
    expect(screen.getByLabelText('Society 3 results')).toHaveTextContent('3')

    fireEvent.click(screen.getByLabelText('Society 3 results'))
    expect(screen.getByText('사회 검색 결과')).toBeInTheDocument()
    expect(screen.queryByText('정치 검색 결과')).not.toBeInTheDocument()
    await waitFor(() => {
      expect(api.articles).toHaveBeenCalledWith('test-token', expect.objectContaining({
        category: 'POLITICS', scope: 'ALL', query: '공통 검색어',
      }))
      expect(api.articles).toHaveBeenCalledWith('test-token', expect.objectContaining({
        category: 'SOCIETY', scope: 'ALL', query: '공통 검색어',
      }))
    })
    expect(screen.getByLabelText('location-search')).toHaveTextContent('category=POLITICS')
    expect(screen.getByLabelText('location-search')).toHaveTextContent('scope=following')
  })

  it('keeps a successful category visible while retrying the failed category', async () => {
    const politics: ArticleSummary = {
      id: 'politics-retried',
      title: '재시도한 정치 결과',
      category: 'POLITICS',
      publisher: { code: 'yna', name: 'Yonhap News', logo_url: '' },
      published_at: '2026-11-05T12:00:00+09:00',
      analysis_state: 'READY',
      is_saved: false,
    }
    const society: ArticleSummary = { ...politics, id: 'society-ok', title: '유지된 사회 결과', category: 'SOCIETY' }
    let politicsAttempts = 0
    vi.mocked(api.articles).mockImplementation(async (_token, options) => {
      if (!options.query) return { items: [], next_cursor: null }
      if (options.category === 'POLITICS' && politicsAttempts++ === 0) throw new Error('offline')
      if (options.category === 'POLITICS') {
        return { items: [politics], next_cursor: null, total_count: 1 }
      }
      return { items: [society], next_cursor: null, total_count: 1 }
    })

    const user = userEvent.setup()
    renderFeed()
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search articles' }), {
      target: { value: '분야별 오류' },
    })

    expect(await screen.findByText('We could not search this section.')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Society 1 results'))
    expect(screen.getByText('유지된 사회 결과')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Politics 0 results'))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('재시도한 정치 결과')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Society 1 results'))
    expect(screen.getByText('유지된 사회 결과')).toBeInTheDocument()
  })

  it('previews URL analysis only after a zero-result search without sending the URL', async () => {
    const user = userEvent.setup()
    renderFeed()
    const search = screen.getByRole('searchbox', { name: 'Search articles' })

    await waitFor(() => expect(api.articles).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Analyze an article link' })).not.toBeInTheDocument()
    await user.type(search, '없는 기사')
    const entry = await screen.findByRole('button', { name: 'Analyze an article link' })
    const searchRequestCount = vi.mocked(api.articles).mock.calls.length

    await user.click(entry)
    const urlInput = screen.getByRole('textbox', { name: 'Article URL' })
    await user.type(urlInput, 'not-a-url')
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    expect(screen.getByRole('alert')).toHaveTextContent('complete public article URL')

    await user.clear(urlInput)
    await user.type(urlInput, ' https://news.example.com/story ')
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    const preview = screen.getByText('Article analysis is coming soon.').closest('[role="status"]')
    expect(preview).toHaveTextContent('not uploaded or saved')
    expect(api.articles).toHaveBeenCalledTimes(searchRequestCount)

    await user.click(screen.getByRole('button', { name: 'Back to search' }))
    expect(screen.getByText('No stories found for “없는 기사”.')).toBeInTheDocument()
  })

  it('saves source preferences explicitly from the manager', async () => {
    const user = userEvent.setup()
    renderFeed()

    await user.click(screen.getByRole('button', { name: /Manage sources/i }))
    await user.click(screen.getByRole('checkbox', { name: 'Yonhap News' }))
    await user.click(screen.getByRole('button', { name: 'Save sources' }))

    await waitFor(() => expect(api.replaceFollowedPublishers).toHaveBeenCalledWith(
      'test-token',
      ['mbc', 'yna'],
    ))
  })

  it('discards a cancelled draft and keeps the editor open after a failed save', async () => {
    const user = userEvent.setup()
    renderFeed()

    await user.click(screen.getByRole('button', { name: /Manage sources/i }))
    await user.click(screen.getByRole('checkbox', { name: 'Yonhap News' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Manage sources/i }))
    expect(screen.getByRole('checkbox', { name: 'Yonhap News' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'MBC' })).toBeChecked()

    vi.mocked(api.replaceFollowedPublishers).mockRejectedValueOnce(new Error('offline'))
    await user.click(screen.getByRole('checkbox', { name: 'Yonhap News' }))
    await user.click(screen.getByRole('button', { name: 'Save sources' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'MBC' })).toBeChecked()
  })

  it('distinguishes an empty follow set from a followed category with no stories', async () => {
    const user = userEvent.setup()
    vi.mocked(api.publishers).mockResolvedValueOnce([
      { code: 'yna', name: 'Yonhap News', logo_url: '', is_followed: false },
    ])
    const first = renderFeed()
    await user.click(screen.getByRole('button', { name: 'Following' }))
    expect(await screen.findByText('Choose a few trusted sources to build your Following feed.'))
      .toBeInTheDocument()
    first.unmount()

    renderFeed()
    await user.click(screen.getByRole('button', { name: 'Following' }))
    expect(await screen.findByText(/have no politics stories right now/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Browse Latest' })).toBeInTheDocument()
  })
})
