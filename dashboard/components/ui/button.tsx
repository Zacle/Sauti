import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

/**
 * The one button for Sauti. Every clickable control that looks like a button
 * should use this (or `buttonVariants` for links), so weight, height, focus and
 * disabled states match everywhere.
 *
 *  primary    – the single main action in a view (filled ink)
 *  secondary  – everyday actions next to a primary (outlined surface)
 *  soft       – quiet filled action, toolbars and inline actions
 *  ghost      – lowest emphasis; text that becomes a button on hover
 *  accent     – brand-coloured call to action (use sparingly)
 *  danger     – destructive confirmation
 *  danger-soft– destructive action that is not the final confirmation
 *  link       – inline text action
 */
const buttonVariants = cva(
  [
    "relative inline-flex shrink-0 cursor-pointer select-none items-center justify-center gap-1.5 whitespace-nowrap",
    "rounded-[var(--radius-button)] border border-transparent font-medium leading-none tracking-[-0.005em] no-underline",
    "transition-[background-color,color,border-color,box-shadow,transform] duration-150 ease-out",
    "active:translate-y-px",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal",
    "disabled:pointer-events-none disabled:opacity-45 aria-disabled:pointer-events-none aria-disabled:opacity-45",
    "[&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  ].join(" "),
  {
    variants: {
      variant: {
        primary:
          "bg-primary text-primary-fg shadow-[inset_0_1px_0_rgb(255_255_255/0.12),0_1px_2px_rgb(0_0_0/0.14)] hover:bg-primary/88",
        secondary:
          "border-line-strong bg-surface text-ink shadow-[0_1px_2px_rgb(0_0_0/0.05)] hover:border-fg-muted/45 hover:bg-surface-2",
        soft: "bg-tint/[0.06] text-ink hover:bg-tint/[0.1]",
        ghost: "bg-transparent text-fg-soft hover:bg-tint/[0.06] hover:text-ink",
        accent:
          "bg-signal text-signal-fg shadow-[inset_0_1px_0_rgb(255_255_255/0.16),0_1px_2px_rgb(0_0_0/0.14)] hover:bg-signal/90",
        danger:
          "bg-danger text-danger-fg shadow-[inset_0_1px_0_rgb(255_255_255/0.14),0_1px_2px_rgb(0_0_0/0.14)] hover:bg-danger/90",
        "danger-soft": "bg-danger/8 text-danger hover:bg-danger/14",
        link: "h-auto rounded-sm px-0 text-signal underline-offset-4 hover:underline active:translate-y-0",
      },
      size: {
        xs: "h-8 px-3 text-[0.82rem] [&_svg]:size-3.5",
        sm: "h-9 px-3.5 text-sm",
        md: "h-11 px-5 text-sm",
        lg: "h-12 px-6 text-[0.95rem]",
        "icon-xs": "size-8 p-0 [&_svg]:size-3.5",
        "icon-sm": "size-9 p-0",
        icon: "size-10 p-0",
      },
    },
    compoundVariants: [{ variant: "link", className: "h-auto px-0" }],
    defaultVariants: { variant: "primary", size: "md" },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export function Button({ className, variant, size, asChild = false, type, ...props }: ButtonProps) {
  const Comp = asChild ? Slot : "button";
  return (
    <Comp
      className={cn(buttonVariants({ variant, size }), className)}
      {...(asChild ? {} : { type: type ?? "button" })}
      {...props}
    />
  );
}

export { buttonVariants };
