import { LoaderCircle, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { Publisher } from '../lib/types'
import { Button } from './ui/Button'

export function ManageSourcesDialog({
  open,
  publishers,
  isSaving,
  errorMessage,
  onClose,
  onSave,
}: {
  open: boolean
  publishers: Publisher[]
  isSaving: boolean
  errorMessage?: string
  onClose: () => void
  onSave: (codes: string[]) => void
}) {
  const [draft, setDraft] = useState<string[]>(() => (
    publishers.filter((publisher) => publisher.is_followed).map((publisher) => publisher.code)
  ))

  useEffect(() => {
    if (!open) return
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !isSaving) onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [isSaving, onClose, open])

  function toggle(code: string) {
    setDraft((current) => current.includes(code)
      ? current.filter((item) => item !== code)
      : [...current, code])
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 p-0 sm:items-center sm:p-6" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isSaving) onClose()
    }}>
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="manage-sources-title"
        className="max-h-[88vh] w-full overflow-y-auto border-t border-black bg-[var(--paper)] p-6 shadow-2xl sm:max-w-lg sm:border"
      >
        <div className="flex items-start justify-between gap-6 border-b border-black pb-5">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-zinc-500">Your feed</p>
            <h2 id="manage-sources-title" className="mt-2 font-serif text-3xl font-black tracking-[-0.04em]">Manage sources</h2>
          </div>
          <button type="button" aria-label="Close source manager" className="grid size-10 place-items-center" disabled={isSaving} onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <p className="mt-5 text-sm leading-6 text-zinc-600">
          Choose the publishers shown in Following. Your selections do not affect article analysis or comparisons.
        </p>

        <div className="mt-5 divide-y divide-zinc-300 border-y border-zinc-300">
          {publishers.map((publisher) => (
            <label key={publisher.code} className="flex cursor-pointer items-center justify-between gap-4 py-4 text-sm font-semibold">
              <span>{publisher.name}</span>
              <input
                type="checkbox"
                className="size-5 accent-black"
                checked={draft.includes(publisher.code)}
                disabled={isSaving}
                onChange={() => toggle(publisher.code)}
              />
            </label>
          ))}
        </div>

        {errorMessage && <p role="alert" className="mt-4 text-sm font-semibold text-red-700">{errorMessage}</p>}
        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="ghost" disabled={isSaving} onClick={onClose}>Cancel</Button>
          <Button type="button" disabled={isSaving} onClick={() => onSave(draft)}>
            {isSaving && <LoaderCircle className="animate-spin" size={16} />}
            Save sources
          </Button>
        </div>
      </section>
    </div>
  )
}
