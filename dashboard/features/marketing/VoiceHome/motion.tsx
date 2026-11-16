"use client";

import type { ReactNode } from "react";
import { motion, useReducedMotion } from "motion/react";

const ease = [0.16, 1, 0.3, 1] as const;

/**
 * Small, fast entrance: content is only offset 14px and starts revealing
 * before it reaches the viewport, so scrolling never shows an empty screen.
 */
export function Reveal({
  children,
  delay = 0,
  className,
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className={className}
      initial={reduce ? false : { opacity: 0, y: 14 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "0px 0px 120px 0px" }}
      transition={{ duration: 0.5, delay, ease }}
    >
      {children}
    </motion.div>
  );
}

export function Eyebrow({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`inline-flex items-center gap-2 font-mono text-[0.7rem] uppercase tracking-[0.18em] text-fg-muted ${className}`}
    >
      <span aria-hidden="true" className="size-1 rounded-full bg-signal" />
      {children}
    </span>
  );
}
