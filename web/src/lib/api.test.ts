import { describe, expect, it } from 'vitest'
import { articleQueryString } from './api'

describe('articleQueryString', () => {
  it('encodes search, filters, and cursor without losing repeated publishers', () => {
    const query = new URLSearchParams(articleQueryString({
      category: 'POLITICS',
      publishers: ['yna', 'mbc'],
      query: '추미애 발언',
      cursor: 'opaque+cursor',
    }))

    expect(query.get('category')).toBe('POLITICS')
    expect(query.get('limit')).toBe('20')
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
  })
})
