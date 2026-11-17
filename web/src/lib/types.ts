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

export type EconomicTone = 'CONCERN' | 'OPTIMISM' | 'LIMITED' | 'OBSERVED'

export interface EconomicSentence extends TextRange {
  scope: 'TITLE' | 'BODY'
}

export interface EconomicEvidence {
  sentence: EconomicSentence
  channels: string[]
  role: 'EFFECT_REALIZED' | 'EFFECT_FORECAST'
  tone: EconomicTone
  voice: 'QUOTE' | 'UNATTRIBUTED' | 'REPORTER'
  source: string | null
  reasons: ('FOCUS' | 'FOCUS_TONE' | 'TONE_CONTRAST')[]
}

export interface EconomicNumber {
  indicator: string | null
  value: string
  basis: string[]
  kind: 'LEVEL' | 'CHANGE'
}

export interface EconomicSide {
  article_id: string
  publisher: Publisher
  title: string
  is_current: boolean
  channels?: string[]
  tone?: EconomicTone
  sentence?: EconomicSentence | null
  counts?: Record<string, number>
  values?: EconomicNumber[]
}

export interface EconomicComparison {
  type: 'FOCUS' | 'TONE' | 'SOURCES' | 'NUMBERS'
  channel?: string
  indicator?: string
  sides: EconomicSide[]
}

export interface EconomicLens {
  focus: string[]
  tone: Record<string, EconomicTone>
  evidence: EconomicEvidence[]
  sources: Record<string, number>
  numbers: EconomicNumber[]
  comparison: EconomicComparison[]
}

export interface Analysis {
  state: AnalysisState
  version: string
  source_content_hash: string
  findings: Finding[]
  economic?: EconomicLens | null
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
