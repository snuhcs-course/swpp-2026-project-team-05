import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  },
}))

function LocationProbe() {
  return <output aria-label="location-search">{useLocation().search}</output>
}

function renderFeed() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/?category=POLITICS']}>
        <FeedPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('FeedPage search', () => {
  beforeEach(() => {
    vi.mocked(api.publishers).mockResolvedValue([])
    vi.mocked(api.articles).mockResolvedValue({ items: [], next_cursor: null })
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
})
