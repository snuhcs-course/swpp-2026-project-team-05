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
  if (category === 'ECONOMY') return 'Economy'
  return category === 'SOCIETY' ? 'Society' : 'Politics'
}

export function categoryFromParam(value: string | null) {
  return value === 'SOCIETY' || value === 'ECONOMY' ? value : 'POLITICS'
}

const CHANNEL_LABELS: Record<string, string> = {
  fx: 'Exchange rate',
  capital_market: 'Financial markets',
  household_credit: 'Household credit',
  housing: 'Housing',
  macro_growth: 'Growth & demand',
  prices: 'Prices',
  trade: 'Trade',
  corporate: 'Companies',
  fiscal: 'Fiscal policy',
  labor: 'Jobs',
}

export function channelLabel(channel: string) {
  return CHANNEL_LABELS[channel] ?? channel
}

export function economicStanceLabel(stance: string, direction: string | null = null) {
  if (stance === 'OBSERVED') {
    const moves: Record<string, string> = { UP: 'Reports a rise', DOWN: 'Reports a fall', SWING: 'Reports a swing' }
    return (direction && moves[direction]) ?? 'Reports without evaluation'
  }
  const labels: Record<string, string> = { CONCERN: 'Concern', OPTIMISM: 'Optimism', LIMITED: 'Limited effect' }
  return labels[stance] ?? stance
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
