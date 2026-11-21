"use client";


import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";
import {
ArrowLeft,
ArrowRight,
Check,
LoaderCircle,
Plus,
Sparkles
} from "lucide-react";
import { useEffect, useState } from "react";

import { BLANK_TEMPLATE, Template } from "./studio-model";

export const BRIEF_EXAMPLES = [
  "Answer calls for our dental clinic, book check-ups and send urgent cases to the front desk.",
  "Take bookings for a hair salon, quote prices from our menu and confirm by SMS.",
  "Handle after-hours calls for a property agency and take detailed messages.",
];

export const GENERATION_STEPS = ["Reading your description", "Writing the prompt", "Choosing tools and safety rules", "Setting languages and details"];

export function TemplateSelection({
  brief,
  category,
  categories,
  templates: visibleTemplates,
  templatesStatus,
  onRetryTemplates,
  onBriefChange,
  onCategoryChange,
  onGenerate,
  generating,
  error,
  onSelect,
  onBack,
}: {
  brief: string;
  category: string;
  categories: string[];
  templates: Template[];
  templatesStatus: "loading" | "ready" | "failed";
  onRetryTemplates: () => void;
  onBriefChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onGenerate: () => void;
  generating: boolean;
  error: string;
  onSelect: (template: Template) => void;
  onBack: () => void;
}) {
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (!generating) { setStep(0); return; }
    const timer = window.setInterval(() => setStep((current) => Math.min(current + 1, GENERATION_STEPS.length - 1)), 1800);
    return () => window.clearInterval(timer);
  }, [generating]);

  const blank = BLANK_TEMPLATE;

  return (
    <section className="studio-kit mx-auto grid w-full max-w-[64rem] grid-cols-[minmax(0,1fr)] gap-10 px-1 pb-16 pt-2 font-sans text-fg">
      <header className="grid grid-cols-[minmax(0,1fr)] gap-4">
        <Button variant="ghost" size="xs" className="-ml-3 w-fit" onClick={onBack}><ArrowLeft /> Agents</Button>
        <div>
          <p className="m-0 text-[0.78rem] font-medium text-fg-muted">New agent</p>
          <h1 className="m-0 mt-1 text-[1.6rem] font-semibold leading-tight tracking-[-0.02em] text-ink">How should your agent help callers?</h1>
          <p className="m-0 mt-2 max-w-2xl text-[0.92rem] leading-relaxed text-fg-soft">
            Describe the job in your own words, or start from a template. You can change everything afterwards.
          </p>
        </div>
      </header>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-3">
        <div
          className={cn(
            "rounded-[var(--radius-card)] border bg-surface shadow-[var(--shadow-card)] transition-[border-color,box-shadow] focus-within:border-signal/50 focus-within:ring-4 focus-within:ring-signal/10",
            generating ? "border-signal/40" : "border-line-strong",
          )}
        >
          <label htmlFor="agent-brief" className="sr-only">Describe what the agent should do</label>
          <textarea
            id="agent-brief"
            rows={4}
            maxLength={2000}
            disabled={generating}
            value={brief}
            onChange={(event) => onBriefChange(event.target.value)}
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === "Enter" && brief.trim() && !generating) onGenerate();
            }}
            placeholder="For example: answer calls for our clinic, book appointments, and transfer urgent requests to the front desk."
            className="block min-h-28 w-full resize-none border-0 bg-transparent px-5 pt-4 font-sans text-[0.98rem] leading-relaxed text-fg outline-none placeholder:text-fg-muted disabled:opacity-60"
          />
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3">
            <span className="text-[0.76rem] tabular-nums text-fg-muted">
              {brief.length} / 2000 <span className="max-sm:hidden">· Ctrl + Enter to generate</span>
            </span>
            <Button size="sm" disabled={!brief.trim() || generating} onClick={onGenerate}>
              {generating ? <LoaderCircle className="spin" /> : <Sparkles />}
              {generating ? "Setting up…" : "Set up my agent"}
            </Button>
          </div>
        </div>

        {!brief.trim() && !generating ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[0.76rem] text-fg-muted">Try:</span>
            {BRIEF_EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => onBriefChange(example)}
                className="max-w-full cursor-pointer truncate rounded-full border border-line bg-transparent px-3 py-1.5 text-left font-sans text-[0.78rem] text-fg-soft transition-colors hover:border-line-strong hover:text-ink"
              >
                {example}
              </button>
            ))}
          </div>
        ) : null}

        {generating ? (
          <div role="status" className="rounded-[var(--radius-card)] border border-line bg-tint/[0.025] p-4">
            <div className="h-1 overflow-hidden rounded-full bg-tint/[0.08]">
              <div className="h-full rounded-full bg-signal transition-[width] duration-700" style={{ width: `${((step + 1) / GENERATION_STEPS.length) * 100}%` }} />
            </div>
            <ol className="m-0 mt-3 grid list-none gap-1.5 p-0 sm:grid-cols-4">
              {GENERATION_STEPS.map((label, index) => (
                <li key={label} className={cn("flex items-center gap-2 text-[0.8rem]", index <= step ? "text-ink" : "text-fg-muted")}>
                  {index < step ? <Check className="size-3.5 text-signal" /> : index === step ? <LoaderCircle className="spin size-3.5 text-signal" /> : <span className="size-3.5 rounded-full border border-line-strong" />}
                  {label}
                </li>
              ))}
            </ol>
          </div>
        ) : null}

        {error ? <Alert>{error}</Alert> : null}
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5">
        <div className="flex flex-wrap items-end justify-between gap-4 border-t border-line pt-8">
          <div>
            <h2 className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">Or start from a template</h2>
            <p className="m-0 mt-1 text-[0.84rem] text-fg-muted">Ready-made prompts and tools for common call jobs.</p>
          </div>
          <Tabs value={category} onValueChange={onCategoryChange}>
            <TabsList aria-label="Template category">
              {categories.map((item) => <TabsTrigger key={item} value={item} disabled={generating}>{item}</TabsTrigger>)}
            </TabsList>
          </Tabs>
        </div>

        {templatesStatus === "failed" && (
          <Alert tone="error" className="flex flex-wrap items-center justify-between gap-3">
            <span>Templates couldn’t be loaded. You can still describe the agent above or start blank.</span>
            <Button type="button" size="xs" variant="secondary" onClick={onRetryTemplates}>Try again</Button>
          </Alert>
        )}
        <div className="grid grid-cols-[minmax(0,1fr)] gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-busy={templatesStatus === "loading"}>
          {templatesStatus === "loading"
            ? [0, 1, 2].map((index) => (
              <div key={index} aria-hidden="true" className="min-h-[11.5rem] animate-pulse rounded-[var(--radius-card)] border border-line bg-tint/[0.03]" />
            ))
            : visibleTemplates.map((template) => (
              <TemplateCard key={template.id} template={template} disabled={generating} onSelect={() => onSelect(template)} />
            ))}
          <TemplateCard template={blank} disabled={generating} blank onSelect={() => onSelect(blank)} />
        </div>
      </div>
    </section>
  );
}

export function TemplateCard({ template, disabled, blank = false, onSelect }: { template: Template; disabled: boolean; blank?: boolean; onSelect: () => void }) {
  const Icon = blank ? Plus : template.icon;
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onSelect}
      className={cn(
        "group flex min-h-[11.5rem] cursor-pointer flex-col rounded-[var(--radius-card)] border p-5 text-left font-sans transition-[border-color,background-color,box-shadow,transform] duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal disabled:cursor-not-allowed disabled:opacity-50",
        blank
          ? "border-dashed border-line-strong bg-transparent hover:border-fg-muted/50 hover:bg-tint/[0.02]"
          : "border-line bg-surface shadow-[var(--shadow-card)] hover:-translate-y-px hover:border-line-strong hover:shadow-[var(--shadow-pop)]",
      )}
    >
      <span className="flex items-start justify-between gap-3">
        <span className={cn("grid size-9 place-items-center rounded-[var(--radius-control)] [&_svg]:size-[18px]", blank ? "bg-tint/[0.05] text-fg-soft" : "bg-signal/10 text-signal")}>
          <Icon />
        </span>
        <span className="text-[0.72rem] font-medium text-fg-muted">{blank ? "Custom" : template.group}</span>
      </span>
      <span className="mt-4 block text-[0.95rem] font-semibold tracking-[-0.01em] text-ink">{template.name}</span>
      <span className="mt-1 line-clamp-3 block text-[0.82rem] leading-relaxed text-fg-soft">
        {blank ? "Write the prompt and choose every setting yourself." : template.description}
      </span>
      <span className="mt-auto flex items-center gap-1.5 pt-4 text-[0.8rem] font-medium text-fg-muted transition-colors group-hover:text-ink">
        {blank ? "Start blank" : "Use template"}
        <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
      </span>
    </button>
  );
}
