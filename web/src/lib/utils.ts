import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDate(value: string, withTime = false) {
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  }).format(new Date(value))
}

export function categoryLabel(category: string) {
  return category === 'SOCIETY' ? 'Society' : 'Politics'
}

export function findingCategoryLabel(category: string) {
  const labels: Record<string, string> = {
    A: 'Reporting stance',
    B: 'Speech characterization',
    C: 'Action characterization',
    D: 'Emphasis of degree',
    E: 'Evaluation or emotion',
    F: 'One-sided naming',
    G: 'Certainty or categorization',
  }
  return labels[category] ?? 'Expression pattern'
}
