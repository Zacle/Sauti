import * as React from "react";
import { cn } from "@/lib/utils";

const control =
  "w-full rounded-[var(--radius-control)] border border-line-strong bg-tint/[0.03] px-3.5 text-[0.95rem] text-fg placeholder:text-fg-muted transition-[border-color,background-color,box-shadow] duration-200 hover:border-tint/25 focus:border-signal/60 focus:bg-tint/[0.05] focus:outline-none focus:ring-4 focus:ring-signal/10 disabled:cursor-not-allowed disabled:opacity-60 read-only:text-fg-soft";

/** Wraps a control with its label text; put the control inside as a child. */
export function Label({ className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn("grid gap-2 text-sm font-medium text-fg", className)} {...props} />;
}

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return <input ref={ref} className={cn(control, "h-11", className)} {...props} />;
  },
);

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...props }, ref) {
    return <textarea ref={ref} className={cn(control, "min-h-24 resize-y py-3 leading-relaxed", className)} {...props} />;
  },
);

export function Alert({ tone = "error", children, className }: { tone?: "error" | "success" | "info"; children: React.ReactNode; className?: string }) {
  const tones = {
    error: "border-danger/25 bg-danger/[0.07] text-danger",
    success: "border-signal/25 bg-signal/[0.07] text-signal",
    info: "border-line-strong bg-tint/[0.04] text-fg-soft",
  } as const;
  return (
    <div role={tone === "error" ? "alert" : "status"} className={cn("rounded-[var(--radius-control)] border px-3.5 py-2.5 text-sm leading-relaxed", tones[tone], className)}>
      {children}
    </div>
  );
}
