"use client";

import { useId, useRef, useState } from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Check, Copy, Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, Input, Textarea } from "@/components/ui/field";
import { Checkbox } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";
import type { CreateAgentVariable } from "@/types/api";

/** Turns "Parking instructions" into "parking_instructions". */
function toKey(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9_]+/g, "_").replace(/^[^a-z]+|_+$/g, "").slice(0, 100);
}

/**
 * "Add variable" button plus the dialog that creates a prompt variable.
 * The key is suggested from the name as you type, and can be edited.
 */
export function AddVariableForm({
  onAdd,
  disabled = false,
}: {
  onAdd: (variable: CreateAgentVariable) => void | Promise<void>;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [label, setLabel] = useState("");
  const [key, setKey] = useState("");
  const [keyEdited, setKeyEdited] = useState(false);
  const [description, setDescription] = useState("");
  const [value, setValue] = useState("");
  const [required, setRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const labelRef = useRef<HTMLInputElement>(null);
  const ids = useId();

  const normalizedKey = toKey(keyEdited ? key : label);
  const keyValid = /^[a-z][a-z0-9_]{0,99}$/.test(normalizedKey);
  const valid = keyValid && label.trim().length > 0;
  const token = `{{${normalizedKey || "variable_name"}}}`;

  function reset() {
    setLabel("");
    setKey("");
    setKeyEdited(false);
    setDescription("");
    setValue("");
    setRequired(false);
    setError("");
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    setError("");
    try {
      await onAdd({ key: normalizedKey, label: label.trim(), description: description.trim(), value: value.trim(), required });
      reset();
      setOpen(false);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to add this variable.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={(next) => { setOpen(next); if (!next) reset(); }}>
      <DialogPrimitive.Trigger asChild>
        <Button variant="secondary" size="xs" disabled={disabled}><Plus /> Add variable</Button>
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        {/* Above the personalisation drawer (z 160). */}
        <DialogPrimitive.Overlay className="fixed inset-0 z-[170] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
        <DialogPrimitive.Content
          onOpenAutoFocus={(event) => { event.preventDefault(); labelRef.current?.focus(); }}
          className="fixed left-1/2 top-1/2 z-[171] flex max-h-[min(92dvh,780px)] w-[min(94vw,32rem)] -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[var(--radius-card)] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)] max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:w-full max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-b-none"
        >
          <form onSubmit={(event) => void submit(event)} className="flex min-h-0 flex-col">
            <header className="flex items-start justify-between gap-4 px-6 pb-2 pt-5">
              <div className="min-w-0">
                <DialogPrimitive.Title className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">New business detail</DialogPrimitive.Title>
                <DialogPrimitive.Description className="m-0 mt-1 text-[0.85rem] leading-relaxed text-fg-soft">
                  A fact the agent can use, like opening hours or parking. Insert it anywhere in the prompt.
                </DialogPrimitive.Description>
              </div>
              <DialogPrimitive.Close asChild>
                <Button variant="ghost" size="icon-xs" aria-label="Close" className="-mr-1.5"><X /></Button>
              </DialogPrimitive.Close>
            </header>

            <div className="grid min-h-0 gap-4 overflow-y-auto px-6 py-4">
              <div className="grid gap-1.5">
                <label htmlFor={`${ids}-label`} className="text-[0.84rem] font-medium text-ink">Name</label>
                <Input
                  ref={labelRef}
                  id={`${ids}-label`}
                  className="h-10 text-[0.88rem]"
                  value={label}
                  onChange={(event) => setLabel(event.target.value)}
                  placeholder="Parking instructions"
                  maxLength={120}
                />
              </div>

              <div className="grid gap-1.5">
                <label htmlFor={`${ids}-key`} className="text-[0.84rem] font-medium text-ink">Prompt key</label>
                <div
                  className={cn(
                    "flex h-10 items-center overflow-hidden rounded-[var(--radius-control)] border bg-tint/[0.03] font-mono text-[0.84rem] transition-[border-color,box-shadow] focus-within:ring-4",
                    normalizedKey && !keyValid
                      ? "border-danger/60 focus-within:ring-danger/10"
                      : "border-line-strong focus-within:border-signal/60 focus-within:ring-signal/10",
                  )}
                >
                  <span aria-hidden="true" className="pl-3 text-fg-muted">{"{{"}</span>
                  <input
                    id={`${ids}-key`}
                    className="h-full min-w-0 flex-1 border-0 bg-transparent px-0.5 font-mono text-[0.84rem] text-fg outline-none placeholder:text-fg-muted"
                    value={keyEdited ? key : normalizedKey}
                    onChange={(event) => { setKeyEdited(true); setKey(event.target.value.toLowerCase()); }}
                    placeholder="parking_instructions"
                    spellCheck={false}
                    autoCapitalize="off"
                  />
                  <span aria-hidden="true" className="pr-3 text-fg-muted">{"}}"}</span>
                </div>
                <p className={cn("m-0 text-[0.76rem] leading-relaxed", normalizedKey && !keyValid ? "text-danger" : "text-fg-muted")}>
                  {normalizedKey && !keyValid
                    ? "Start with a letter, then use lowercase letters, numbers or underscores."
                    : keyEdited ? "Lowercase letters, numbers and underscores." : "Filled in from the name. You can change it."}
                </p>
              </div>

              <div className="grid gap-1.5">
                <label htmlFor={`${ids}-value`} className="flex items-center justify-between text-[0.84rem] font-medium text-ink">
                  Value <span className="text-[0.74rem] font-normal text-fg-muted">Optional for now</span>
                </label>
                <Textarea
                  id={`${ids}-value`}
                  rows={3}
                  className="min-h-0 text-[0.88rem]"
                  value={value}
                  onChange={(event) => setValue(event.target.value)}
                  placeholder="Use the rear entrance after 5 PM. Free parking for patients."
                />
              </div>

              <div className="grid gap-1.5">
                <label htmlFor={`${ids}-description`} className="flex items-center justify-between text-[0.84rem] font-medium text-ink">
                  Note for your team <span className="text-[0.74rem] font-normal text-fg-muted">Optional</span>
                </label>
                <Input
                  id={`${ids}-description`}
                  className="h-10 text-[0.88rem]"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="Directions to share with callers"
                />
              </div>

              <label htmlFor={`${ids}-required`} className="flex cursor-pointer items-start gap-3 rounded-[var(--radius-control)] border border-line p-3.5 transition-colors hover:bg-tint/[0.02]">
                <Checkbox id={`${ids}-required`} checked={required} onCheckedChange={(next) => setRequired(next === true)} className="mt-px" />
                <span className="min-w-0">
                  <span className="block text-[0.86rem] font-medium text-ink">Required before going live</span>
                  <span className="mt-0.5 block text-[0.78rem] leading-relaxed text-fg-muted">The agent can’t be activated until this has a value.</span>
                </span>
              </label>

              <div className="flex items-center justify-between gap-3 rounded-[var(--radius-control)] bg-tint/[0.04] px-3.5 py-2.5">
                <p className="m-0 min-w-0 text-[0.78rem] text-fg-muted">
                  Use in the prompt as <code className="font-mono text-[0.8rem] text-ink">{token}</code>
                </p>
                <Button
                  variant="ghost"
                  size="xs"
                  className="h-7 shrink-0"
                  disabled={!keyValid}
                  onClick={() => void navigator.clipboard?.writeText(token).then(() => { setCopied(true); window.setTimeout(() => setCopied(false), 1500); })}
                >
                  {copied ? <Check /> : <Copy />} {copied ? "Copied" : "Copy"}
                </Button>
              </div>

              {error ? <Alert>{error}</Alert> : null}
            </div>

            <footer className="flex justify-end gap-2 border-t border-line px-6 py-3.5">
              <DialogPrimitive.Close asChild>
                <Button variant="secondary" size="sm">Cancel</Button>
              </DialogPrimitive.Close>
              <Button size="sm" type="submit" disabled={!valid || busy}>{busy ? "Adding…" : "Add detail"}</Button>
            </footer>
          </form>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
