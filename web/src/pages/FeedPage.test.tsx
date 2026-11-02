import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
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
      expect.objectContaining({ query: undefined }),
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

  it('searches the full corpus even when Following is selected', async () => {
    renderFeed('/?category=POLITICS&scope=following')
    const input = screen.getByRole('searchbox', { name: 'Search articles' })

    fireEvent.change(input, { target: { value: '중수청' } })

    await waitFor(() => expect(api.articles).toHaveBeenLastCalledWith(
      'test-token',
      expect.objectContaining({ scope: 'ALL', query: '중수청' }),
    ), { timeout: 1000 })
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
