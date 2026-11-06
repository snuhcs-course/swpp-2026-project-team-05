import type { InputHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        'h-12 w-full border border-zinc-300 bg-white px-4 text-[15px] text-black outline-none transition-colors placeholder:text-zinc-400 focus:border-black focus:ring-1 focus:ring-black',
        className,
      )}
      {...props}
    />
  )
}
