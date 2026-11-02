import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/useAuth'
import { api } from '../lib/api'
import type { ArticlePage, ArticleSummary } from '../lib/types'

interface ToggleSavedInput {
  articleId: string
  isSaved: boolean
}

function patchPage(page: ArticlePage, articleId: string, isSaved: boolean, remove: boolean) {
  return {
    ...page,
    items: page.items
      .filter((article) => !(remove && article.id === articleId))
      .map((article) => article.id === articleId ? { ...article, is_saved: isSaved } : article),
  }
}

function isArticle(value: unknown): value is ArticleSummary {
  return Boolean(value && typeof value === 'object' && 'id' in value && 'is_saved' in value)
}

export function useSavedArticle() {
  const { session } = useAuth()
  const queryClient = useQueryClient()
  const userId = session!.user.id
  const relevant = (query: { queryKey: readonly unknown[] }) => (
    query.queryKey[1] === userId
    && ['articles', 'article', 'saved-articles'].includes(String(query.queryKey[0]))
  )

  return useMutation({
    mutationFn: async ({ articleId, isSaved }: ToggleSavedInput) => {
      if (isSaved) {
        await api.removeSavedArticle(session!.token, articleId)
        return false
      }
      await api.saveArticle(session!.token, articleId)
      return true
    },
    onMutate: async ({ articleId, isSaved }) => {
      await queryClient.cancelQueries({ predicate: relevant })
      const snapshots = queryClient.getQueriesData({ predicate: relevant })
      const next = !isSaved
      for (const [key, value] of snapshots) {
        const remove = key[0] === 'saved-articles' && !next
        if (value && typeof value === 'object' && 'pages' in value) {
          const infinite = value as { pages: ArticlePage[]; pageParams: unknown[] }
          queryClient.setQueryData(key, {
            ...infinite,
            pages: infinite.pages.map((page) => patchPage(page, articleId, next, remove)),
          })
        } else if (isArticle(value) && value.id === articleId) {
          queryClient.setQueryData(key, { ...value, is_saved: next })
        }
      }
      return { snapshots }
    },
    onError: (_error, _variables, context) => {
      context?.snapshots.forEach(([key, value]) => queryClient.setQueryData(key, value))
    },
    onSettled: () => queryClient.invalidateQueries({ predicate: relevant }),
  })
}
