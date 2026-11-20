export type NewsCategory = 'POLITICS' | 'SOCIETY' | 'ECONOMY'
export const NEWS_CATEGORIES: readonly NewsCategory[] = ['POLITICS', 'SOCIETY', 'ECONOMY']
export type AnalysisState = 'NOT_STARTED' | 'PROCESSING' | 'READY' | 'PARTIAL' | 'FAILED'
export type FeedScope = 'ALL' | 'FOLLOWING'

export interface User {
  id: string
  email: string
  username: string
  is_developer: boolean
}

export interface Session {
  token: string
  expires_at: string
  user: User
}

export interface Publisher {
  code: string
  name: string
  logo_url: string
  is_followed?: boolean
}

export interface ArticleSummary {
  id: string
  publisher: Publisher
  title: string
  category: NewsCategory
  published_at: string
  analysis_state: AnalysisState
  is_saved: boolean
  saved_at?: string
  search_excerpt?: string | null
}

export interface ArticleDetail extends ArticleSummary {
  body: string
  original_url: string
  content_hash: string
}

export interface TextRange {
  start: number
  end: number
  text: string
}

export interface MarkerRange {
  start_in_sentence: number
  end_in_sentence: number
  text: string
}

export interface Evidence {
  scope: 'TITLE' | 'BODY'
  sentence: TextRange
  marker: MarkerRange
  target_owner: string
  target_speaker_label: string
  comparison_summary: string
}

export interface ComparisonArticle {
  article_id: string
  publisher: Publisher
  title: string
  published_at: string
  original_url: string
  body: string
  evidence: Evidence[]
}

export interface Finding {
  id: string
  categories: string[]
  source: {
    scope: 'TITLE' | 'BODY'
    sentence: TextRange
    marker: MarkerRange
  }
  explanation: string
  comparison_overview: string
  comparison_articles: ComparisonArticle[]
}

export type EconomicStance = 'CONCERN' | 'OPTIMISM' | 'LIMITED' | 'OBSERVED'

export interface EconomicView {
  channels: string[]
  stance: EconomicStance
  direction: 'UP' | 'DOWN' | 'SWING' | null
  phrase: string
  sentence: TextRange & { scope: 'TITLE' | 'BODY' }
}

export interface EconomicOutlet {
  article_id: string
  publisher: Publisher
  title: string
  views: EconomicView[]
}

export interface EconomicLens {
  event: string
  views: EconomicView[]
  others: EconomicOutlet[]
}

/** A Media Frames Corpus frame (Card et al., 2015) and one sentence of the article's own text that shows it. */
export interface FrameOutlet {
  article_id: string
  publisher: Publisher
  title: string
  frame: string
  sentence: string
  /** The outlet's positions on the event's issues (from the issue table); empty when it only reports facts. */
  positions: { issue: string; position: string }[]
}

export interface OutletRef {
  article_id: string
  publisher: Publisher
}

/** One pairwise A-G item inside an issue or fact group: both outlets' phrases, stored once for both articles. */
export interface IssueWording {
  left: OutletRef & { phrase: string }
  right: OutletRef & { phrase: string }
  code: string
  explanation: string
}

/** One issue of the event, seen from the article being read. */
export interface IssueView {
  question: string
  kind: 'contested' | 'one_side' | 'common_fact'
  own: { covered: boolean; fact_only: boolean; position: string | null }
  /** Positions taken, each with the other outlets taking it; `is_own` marks this article's position. */
  positions: { label: string; is_own: boolean; outlets: OutletRef[] }[]
  fact_only_outlets: OutletRef[]
  wordings: IssueWording[]
}

export interface FrameLens {
  event: string
  frame: string
  sentence: TextRange & { scope: 'TITLE' | 'BODY' }
  others: FrameOutlet[]
  issues: IssueView[]
  /** What the outlets report alike (the event's facts), with the wording differences found inside them. */
  facts: { label: string; outlets: number; wordings: IssueWording[] }[]
}

export interface Analysis {
  state: AnalysisState
  version: string
  source_content_hash: string
  findings: Finding[]
  economic?: EconomicLens | null
  frame?: FrameLens | null
}

export interface ArticlePage {
  items: ArticleSummary[]
  next_cursor: string | null
  total_count?: number
}

export interface SavedArticleMutation {
  article_id: string
  is_saved: true
  saved_at: string
}

export interface ApiErrorBody {
  error?: {
    code?: string
    message?: string
    fields?: Record<string, string>
  }
}
