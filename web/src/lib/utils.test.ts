import { describe, expect, it } from 'vitest'
import { findingCategoryLabel } from './utils'

describe('findingCategoryLabel', () => {
  it('turns internal rubric codes into reader-facing labels', () => {
    expect(findingCategoryLabel('A')).toBe('Reporting stance')
    expect(findingCategoryLabel('E')).toBe('Evaluation or emotion')
    expect(findingCategoryLabel('G')).toBe('Certainty or categorization')
  })

  it('does not leak an unknown internal code', () => {
    expect(findingCategoryLabel('A1')).toBe('Expression pattern')
  })
})
