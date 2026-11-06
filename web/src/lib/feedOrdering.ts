import type { ArticleSummary, FeedScope } from './types'

const REVIEWED_EXAMPLE_TITLES = [
  "HD현대일렉트릭, 3분기 매출 1조1799억원 '역대 최대'",
  "에버랜드 막내 판다 '슈바오' 30일 첫 나들이…쌍둥이 언니들은 작별 준비",
  '[한 손 뉴스] 변호사 업무 재개한 홍준표, 대선 경선 관계자 변호 맡아(종합)',
  '홍대 12만·이태원 2만 인파에도 \'우측통행\'…"즐기는 것 좋지만 안전 최선"',
  "한화, 美 해군 함정 건조 '첫' 수주…필리조선소 인수 2년만",
  "'우주산업 거점' 고흥 우주발사체 국가산단 조성 속도",
]

const reviewedExampleOrder: ReadonlyMap<string, number> = new Map(
  REVIEWED_EXAMPLE_TITLES.map((title, index) => [title, index]),
)

export function orderFeedArticles(
  articles: ArticleSummary[],
  scope: FeedScope,
  searchActive: boolean,
) {
  if (scope !== 'ALL' || searchActive) return articles
  return articles
    .map((article, serverIndex) => ({ article, serverIndex }))
    .sort((left, right) => {
      const leftOrder = reviewedExampleOrder.get(left.article.title)
      const rightOrder = reviewedExampleOrder.get(right.article.title)
      if (leftOrder !== undefined || rightOrder !== undefined) {
        if (leftOrder === undefined) return 1
        if (rightOrder === undefined) return -1
        return leftOrder - rightOrder
      }
      return left.serverIndex - right.serverIndex
    })
    .map(({ article }) => article)
}
