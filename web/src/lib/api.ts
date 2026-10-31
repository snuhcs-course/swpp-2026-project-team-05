import type {
  Analysis,
  ApiErrorBody,
  ArticleDetail,
  ArticlePage,
  NewsCategory,
  Publisher,
  Session,
  User,
} from './types'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  fields: Record<string, string>

  constructor(
    message: string,
    status: number,
    fields: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fields = fields
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiErrorBody
    throw new ApiError(
      body.error?.message ?? '요청을 처리하지 못했습니다.',
      response.status,
      body.error?.fields,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  login(identifier: string, password: string) {
    return request<Session>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ identifier, password }),
    })
  },

  register(email: string, username: string, password: string) {
    return request<Session>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, password, password_confirmation: password }),
    })
  },

  logout(token: string) {
    return request<void>('/api/v1/auth/logout', { method: 'POST' }, token)
  },

  async me(token: string) {
    const body = await request<{ user: User }>('/api/v1/auth/me', {}, token)
    return body.user
  },

  async publishers(token: string) {
    const body = await request<{ items: Publisher[] }>('/api/v1/publishers', {}, token)
    return body.items
  },

  articles(token: string, category: NewsCategory, publishers: string[], cursor?: string) {
    const query = new URLSearchParams({ category, limit: '20' })
    publishers.forEach((publisher) => query.append('publisher', publisher))
    if (cursor) query.set('cursor', cursor)
    return request<ArticlePage>(`/api/v1/articles?${query}`, {}, token)
  },

  article(token: string, id: string) {
    return request<ArticleDetail>(`/api/v1/articles/${id}`, {}, token)
  },

  analysis(token: string, id: string) {
    return request<Analysis>(`/api/v1/articles/${id}/analysis`, {}, token)
  },
}
