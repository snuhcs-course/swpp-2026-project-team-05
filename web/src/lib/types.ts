export type NewsCategory = 'POLITICS' | 'SOCIETY'
export type AnalysisState = 'NOT_STARTED' | 'PROCESSING' | 'READY' | 'PARTIAL' | 'FAILED'

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
}

export interface ArticleSummary {
  id: string
  publisher: Publisher
  title: string
  category: NewsCategory
  published_at: string
  analysis_state: AnalysisState
}

export interface ArticleDetail extends ArticleSummary {
  body: string
  original_url: string
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
  comparison_articles: ComparisonArticle[]
}

export interface Analysis {
  state: AnalysisState
  version: string
  findings: Finding[]
}

export interface ArticlePage {
  items: ArticleSummary[]
  next_cursor: string | null
}

export interface ApiErrorBody {
  error?: {
    code?: string
    message?: string
    fields?: Record<string, string>
  }
}
