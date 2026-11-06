import { describe, expect, it } from 'vitest'
import { segmentText } from './text'

describe('segmentText', () => {
  it('uses Unicode code-point offsets for Korean text and emoji', () => {
    expect(segmentText('가😀나다', [{ id: 'f1', start: 1, end: 3 }])).toEqual([
      { text: '가', findingIds: [] },
      { text: '😀나', findingIds: ['f1'] },
      { text: '다', findingIds: [] },
    ])
  })

  it('ignores invalid ranges', () => {
    expect(segmentText('기사', [{ id: 'bad', start: -1, end: 5 }])).toEqual([
      { text: '기사', findingIds: [] },
    ])
  })
})
