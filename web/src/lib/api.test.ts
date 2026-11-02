import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, articleQueryString } from './api'

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('articleQueryString', () => {
  it('encodes search, filters, and cursor without losing repeated publishers', () => {
    const query = new URLSearchParams(articleQueryString({
      category: 'POLITICS',
      scope: 'FOLLOWING',
      publishers: ['yna', 'mbc'],
      query: '추미애 발언',
      cursor: 'opaque+cursor',
    }))

    expect(query.get('category')).toBe('POLITICS')
    expect(query.get('limit')).toBe('20')
    expect(query.get('scope')).toBe('FOLLOWING')
    expect(query.getAll('publisher')).toEqual(['yna', 'mbc'])
    expect(query.get('q')).toBe('추미애 발언')
    expect(query.get('cursor')).toBe('opaque+cursor')
  })

  it('keeps the ordinary feed request free of search-only parameters', () => {
    const query = new URLSearchParams(articleQueryString({
      category: 'SOCIETY',
      publishers: [],
    }))

    expect(query.has('q')).toBe(false)
    expect(query.has('cursor')).toBe(false)
    expect(query.get('scope')).toBe('ALL')
  })
})

describe('publisher follows API', () => {
  it('replaces the complete followed-publisher set', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ publisher_codes: ['yna', 'mbc'] }), { status: 200 }),
    )

    await expect(api.replaceFollowedPublishers('token', ['yna', 'mbc']))
      .resolves.toEqual(['yna', 'mbc'])
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/me/followed-publishers',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ publisher_codes: ['yna', 'mbc'] }),
        headers: expect.objectContaining({ Authorization: 'Bearer token' }),
      }),
    )
  })
})

describe('saved articles API', () => {
  it('lists, saves, and removes through the authenticated contract', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [], next_cursor: null }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        article_id: 'article-1',
        is_saved: true,
        saved_at: '2026-11-02T17:00:00+09:00',
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await api.savedArticles('token', 'saved cursor')
    await api.saveArticle('token', 'article-1')
    await api.removeSavedArticle('token', 'article-1')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/saved-articles?limit=20&cursor=saved+cursor')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      headers: expect.objectContaining({ Authorization: 'Bearer token' }),
    })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/saved-articles/article-1')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'PUT' })
    expect(fetchMock.mock.calls[2][1]).toMatchObject({ method: 'DELETE' })
  })
})
