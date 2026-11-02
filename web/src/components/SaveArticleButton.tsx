import { Bookmark } from 'lucide-react'
import { useSavedArticle } from '../hooks/useSavedArticle'
import { cn } from '../lib/utils'

export function SaveArticleButton({ articleId, isSaved, className }: {
  articleId: string
  isSaved: boolean
  className?: string
}) {
  const mutation = useSavedArticle()
  const label = isSaved ? 'Remove from saved articles' : 'Save article'

  return (
    <span className={cn('relative inline-flex', className)}>
      <button
        type="button"
        aria-label={label}
        title={label}
        disabled={mutation.isPending}
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          mutation.mutate({ articleId, isSaved })
        }}
        className="grid size-9 place-items-center border border-zinc-300 bg-[var(--paper)] text-black transition-colors hover:border-black disabled:cursor-wait disabled:opacity-50"
      >
        <Bookmark size={17} fill={isSaved ? 'currentColor' : 'none'} aria-hidden="true" />
      </button>
      {mutation.isError && (
        <span className="sr-only" role="alert">The saved state could not be updated.</span>
      )}
    </span>
  )
}
