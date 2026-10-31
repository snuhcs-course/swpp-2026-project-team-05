import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 border text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-black focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'border-black bg-black text-white hover:bg-zinc-800',
        outline: 'border-zinc-300 bg-transparent text-zinc-900 hover:border-black hover:bg-white',
        ghost: 'border-transparent bg-transparent text-zinc-700 hover:text-black',
      },
      size: {
        default: 'h-11 px-5',
        small: 'h-9 px-3',
        icon: 'size-10',
      },
    },
    defaultVariants: { variant: 'primary', size: 'default' },
  },
)

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {}

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
