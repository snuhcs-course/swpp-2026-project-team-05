import type { Finding } from './types'

export interface HighlightRange {
  id: string
  start: number
  end: number
}

export interface TextSegment {
  text: string
  findingIds: string[]
}

export function sourceRange(finding: Finding): HighlightRange {
  const sentence = finding.source.sentence
  const marker = finding.source.marker
  return {
    id: finding.id,
    start: sentence.start + marker.start_in_sentence,
    end: sentence.start + marker.end_in_sentence,
  }
}

export function segmentText(text: string, ranges: HighlightRange[]): TextSegment[] {
  const codePoints = Array.from(text)
  const valid = ranges.filter(
    ({ start, end }) => Number.isInteger(start) && Number.isInteger(end) && 0 <= start && start < end && end <= codePoints.length,
  )
  if (!valid.length) return text ? [{ text, findingIds: [] }] : []

  const boundaries = [...new Set([0, codePoints.length, ...valid.flatMap(({ start, end }) => [start, end])])]
    .sort((a, b) => a - b)

  return boundaries.slice(0, -1).flatMap((start, index) => {
    const end = boundaries[index + 1]
    if (start === end) return []
    return [{
      text: codePoints.slice(start, end).join(''),
      findingIds: valid.filter((range) => range.start <= start && end <= range.end).map((range) => range.id),
    }]
  })
}
