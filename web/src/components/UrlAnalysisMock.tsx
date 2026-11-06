import { LoaderCircle, Link2 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { isValidArticleUrl } from '../lib/urlAnalysisMock'
import { Button } from './ui/Button'

type MockStage = 'prompt' | 'entry' | 'preview'

export function UrlAnalysisMock({ query }: { query: string }) {
  const [stage, setStage] = useState<MockStage>('prompt')
  const [url, setUrl] = useState('')
  const [error, setError] = useState<string | null>(null)

  function reset() {
    setStage('prompt')
    setUrl('')
    setError(null)
  }

  function continueToPreview(event: FormEvent) {
    event.preventDefault()
    if (!isValidArticleUrl(url)) {
      setError('Enter a complete public article URL beginning with http:// or https://.')
      return
    }
    setError(null)
    setStage('preview')
  }

  if (stage === 'preview') {
    return (
      <div className="mx-auto max-w-xl py-4 text-center">
        <div role="status">
          <LoaderCircle className="mx-auto animate-spin" size={24} aria-hidden="true" />
          <h3 className="mt-5 font-serif text-2xl font-black text-zinc-950">Article analysis is coming soon.</h3>
          <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-zinc-600">
            This preview shows where analysis will begin once the pipeline is ready. Your link was not uploaded or saved.
          </p>
        </div>
        <Button variant="outline" className="mt-6" onClick={reset}>Back to search</Button>
      </div>
    )
  }

  if (stage === 'entry') {
    return (
      <form className="mx-auto max-w-xl py-4 text-left" onSubmit={continueToPreview} noValidate>
        <div className="flex items-center gap-3">
          <Link2 size={20} aria-hidden="true" />
          <h3 className="font-serif text-2xl font-black text-zinc-950">Analyze an article link</h3>
        </div>
        <p className="mt-3 text-sm leading-6 text-zinc-600">
          Paste a public news URL to preview the upcoming flow. This mock does not upload the link or start analysis.
        </p>
        <label htmlFor="mock-article-url" className="mt-6 block text-xs font-black uppercase tracking-[0.12em] text-zinc-950">
          Article URL
        </label>
        <input
          id="mock-article-url"
          type="url"
          inputMode="url"
          autoComplete="url"
          value={url}
          onChange={(event) => {
            setUrl(event.target.value)
            if (error) setError(null)
          }}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? 'mock-article-url-error' : 'mock-article-url-help'}
          placeholder="https://news.example.com/article"
          className="mt-2 h-12 w-full border border-zinc-400 bg-transparent px-4 text-sm text-zinc-950 outline-none focus:border-black focus:ring-1 focus:ring-black"
        />
        {error ? (
          <p id="mock-article-url-error" role="alert" className="mt-2 text-sm text-red-700">{error}</p>
        ) : (
          <p id="mock-article-url-help" className="mt-2 text-xs text-zinc-500">HTTP and HTTPS links only.</p>
        )}
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
          <Button type="submit">Continue</Button>
          <Button type="button" variant="ghost" onClick={reset}>Back to search</Button>
        </div>
      </form>
    )
  }

  return (
    <div className="mx-auto max-w-xl text-center">
      <h3 className="font-serif text-2xl font-black text-zinc-950">No stories found for “{query}”.</h3>
      <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-zinc-600">
        Have a specific article in mind? You can preview how link-based analysis will work.
      </p>
      <Button className="mt-6" onClick={() => setStage('entry')}>
        <Link2 size={16} aria-hidden="true" /> Analyze an article link
      </Button>
    </div>
  )
}
