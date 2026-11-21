"use client";

/**
 * Business details ("Personalise this agent").
 *
 * Every detail fills a {{placeholder}} in the prompt. Instead of a wall of
 * free-text boxes, each detail gets the control that fits it (presets with an
 * "Other" escape hatch, chips, lists, a weekly schedule, Q&A pairs…) so owners
 * make fewer mistakes, and the stored text stays readable for the prompt.
 */

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Check, CircleAlert, Clock3, Copy, LoaderCircle, Plus, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, Input, Textarea } from "@/components/ui/field";
import { Checkbox, RadioCard, RadioGroup, Tabs, TabsCount, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { Select } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import type { CreateAgentVariable } from "@/types/api";
import type { AgentVariableDefinition } from "@/types/api";
import type { CountryCallingCode } from "@/lib/api/phone-numbers";
import { AddVariableForm } from "../AgentVariables/AddVariableForm";
import { structuredAgentSetting } from "../domain/structured-agent-settings";
import {
  LANGUAGE_OPTIONS,
  MORE_LANGUAGE_CODES,
  TIMEZONE_OPTIONS,
  bookingDurationLabel,
  bookingDurationOptions,
  languageLabel,
} from "../domain/agent-options";
import {
  DETAIL_GROUPS,
  WEEK_DAYS,
  detailError,
  detailSpec,
  emptySchedule,
  parseFaq,
  parseList,
  parseSchedule,
  serializeFaq,
  serializeList,
  serializeSchedule,
  type DayKey,
  type FaqPair,
  type WeeklySchedule,
} from "../domain/business-details";
import { NumberField } from "./studio-kit";

const controlClass = "h-10 text-[0.88rem]";

export function PersonalisationDrawer({
  agentSaved,
  variables,
  values,
  inherited = {},
  countryName,
  defaultPhoneRegion,
  phoneCountries,
  timezone,
  bookingEnabled,
  defaultBookingDurationMinutes,
  onChange,
  onAdd,
  onClose,
  onTimezone,
  onDefaultBookingDurationMinutes,
  onSave,
}: {
  agentSaved: boolean;
  variables: AgentVariableDefinition[];
  values: Record<string, string>;
  /** Workspace business-profile values used while a detail is left empty. */
  inherited?: Record<string, string>;
  countryName: string;
  defaultPhoneRegion: string;
  phoneCountries: CountryCallingCode[];
  timezone: string;
  bookingEnabled: boolean;
  defaultBookingDurationMinutes: number;
  onChange: (key: string, value: string) => void;
  onAdd: (variable: CreateAgentVariable) => void;
  onClose: () => void;
  onTimezone: (timezone: string) => void;
  onDefaultBookingDurationMinutes: (value: number) => void;
  onSave: () => Promise<void>;
}) {
  const [view, setView] = useState<"all" | "missing">("all");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  const status = (variable: AgentVariableDefinition) => {
    const value = values[variable.key] ?? "";
    const error = detailError(variable.key, value);
    if (error) return "invalid" as const;
    if (value.trim() || inherited[variable.key]?.trim()) return "done" as const;
    return variable.required ? "missing" as const : "optional" as const;
  };

  const required = variables.filter((variable) => variable.required);
  const requiredDone = required.filter((variable) => status(variable) === "done").length;
  const needsAttention = variables.filter((variable) => ["missing", "invalid"].includes(status(variable)));
  const progress = required.length ? Math.round((requiredDone / required.length) * 100) : 100;

  const grouped = useMemo(() => {
    const shown = view === "missing" ? needsAttention : variables;
    return DETAIL_GROUPS
      .map((group) => ({ ...group, items: shown.filter((variable) => detailSpec(variable.key).group === group.id) }))
      .filter((group) => group.items.length || (view === "all" && (group.id === "hours" || (group.id === "bookings" && bookingEnabled))));
    // `needsAttention` is derived from values on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, variables, values, bookingEnabled]);

  function goToNextMissing() {
    const next = needsAttention[0];
    if (!next) return;
    if (view !== "all") setView("all");
    window.requestAnimationFrame(() => {
      const element = document.getElementById(`detail-${next.key}`);
      element?.scrollIntoView({ behavior: "smooth", block: "center" });
      element?.querySelector<HTMLElement>("input, textarea, button[role=combobox], button[role=radio]")?.focus({ preventScroll: true });
    });
  }

  async function finish() {
    setSaving(true);
    setSaveError("");
    try {
      await onSave();
    } catch (caught) {
      setSaveError(caught instanceof Error ? caught.message : "Unable to save business details.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <DialogPrimitive.Root open onOpenChange={(open) => { if (!open && !saving) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[160] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          onOpenAutoFocus={(event) => event.preventDefault()}
          className="fixed inset-y-0 right-0 z-[161] flex w-[min(100vw,38rem)] flex-col border-l border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:max-h-[94dvh] max-sm:w-full max-sm:rounded-t-[var(--radius-card)] max-sm:border-l-0 max-sm:border-t"
        >
          <header className="border-b border-line px-6 pb-4 pt-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <p className="m-0 text-[0.75rem] font-medium text-fg-muted">Business details</p>
                <DialogPrimitive.Title className="m-0 mt-0.5 text-[1.15rem] font-semibold tracking-[-0.01em] text-ink">Personalise this agent</DialogPrimitive.Title>
                <p className="m-0 mt-1 text-[0.82rem] leading-relaxed text-fg-soft">
                  Facts the agent uses on every call. Each one fills a <code className="font-mono text-[0.78rem] text-fg">{"{{placeholder}}"}</code> in the prompt.
                </p>
              </div>
              <DialogPrimitive.Close asChild>
                <Button variant="ghost" size="icon-xs" aria-label="Close" disabled={saving} className="-mr-1.5"><X /></Button>
              </DialogPrimitive.Close>
            </div>
            <div className="mt-4 grid gap-2">
              <div className="flex items-center justify-between gap-3 text-[0.78rem]">
                <span className="text-fg-soft">
                  <span className="font-semibold tabular-nums text-ink">{requiredDone} of {required.length}</span> required details done
                </span>
                <span className="tabular-nums text-fg-muted">{progress}%</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-tint/[0.08]">
                <div className={cn("h-full rounded-full transition-[width] duration-500", progress === 100 ? "bg-success" : "bg-signal")} style={{ width: `${progress}%` }} />
              </div>
            </div>
            <Tabs value={view} onValueChange={(next) => setView(next as "all" | "missing")} className="mt-4">
              <TabsList aria-label="Show">
                <TabsTrigger value="all">All details</TabsTrigger>
                <TabsTrigger value="missing">Needs attention <TabsCount>{needsAttention.length}</TabsCount></TabsTrigger>
              </TabsList>
            </Tabs>
          </header>

          <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto px-6 pb-8">
            {view === "missing" && needsAttention.length === 0 ? (
              <div className="grid place-items-center gap-2 py-16 text-center">
                <span className="grid size-10 place-items-center rounded-full bg-success/10 text-success"><Check className="size-5" /></span>
                <p className="m-0 text-[0.92rem] font-medium text-ink">Everything required is filled in</p>
                <p className="m-0 max-w-xs text-[0.8rem] text-fg-muted">Optional details make answers better, but the agent can go live without them.</p>
              </div>
            ) : null}

            {grouped.map((group) => (
              <section key={group.id} className="border-t border-line pt-6 first:border-t-0 [&:not(:first-child)]:mt-6">
                <div className="mb-4">
                  <h3 className="m-0 text-[0.92rem] font-semibold tracking-[-0.01em] text-ink">{group.title}</h3>
                  <p className="m-0 mt-0.5 text-[0.78rem] text-fg-muted">{group.description}</p>
                </div>
                <div className="grid gap-6">
                  {view === "all" && group.id === "hours" ? (
                    <DetailShell label="Timezone" state="done" hint="Used for opening hours, availability and bookings.">
                      <Select size="md" className={controlClass} ariaLabel="Business timezone" icon={<Clock3 />} searchable searchPlaceholder="Find a city or timezone" options={TIMEZONE_OPTIONS} value={timezone} onValueChange={onTimezone} />
                    </DetailShell>
                  ) : null}
                  {view === "all" && group.id === "bookings" && bookingEnabled ? (
                    <DetailShell label="Standard appointment length" state="done" hint="Used for free-slot checks unless the caller asks for a different length.">
                      <Select
                        size="md"
                        className={cn(controlClass, "sm:max-w-[16rem]")}
                        ariaLabel="Standard booking duration"
                        icon={<Clock3 />}
                        value={String(defaultBookingDurationMinutes)}
                        onValueChange={(value) => onDefaultBookingDurationMinutes(Number(value))}
                        options={bookingDurationOptions(defaultBookingDurationMinutes).map((minutes) => ({ value: String(minutes), label: bookingDurationLabel(minutes) }))}
                      />
                    </DetailShell>
                  ) : null}
                  {group.items.map((variable) => (
                    <DetailField
                      key={variable.key}
                      variable={variable}
                      value={values[variable.key] ?? ""}
                      inheritedValue={inherited[variable.key]}
                      values={values}
                      state={status(variable)}
                      countryName={countryName}
                      defaultPhoneRegion={defaultPhoneRegion}
                      phoneCountries={phoneCountries}
                      onChange={(value) => onChange(variable.key, value)}
                    />
                  ))}
                </div>
              </section>
            ))}

            {view === "all" ? (
              <div className="mt-8 flex flex-wrap items-center justify-between gap-3 rounded-[var(--radius-card)] border border-dashed border-line-strong p-4">
                <div className="min-w-0">
                  <p className="m-0 text-[0.86rem] font-medium text-ink">Need something else?</p>
                  <p className="m-0 mt-0.5 text-[0.78rem] text-fg-muted">Add your own detail and use it anywhere in the prompt.</p>
                </div>
                <AddVariableForm onAdd={onAdd} />
              </div>
            ) : null}
          </div>

          <footer className="grid gap-3 border-t border-line px-6 py-3.5">
            {saveError ? <Alert>{saveError}</Alert> : null}
            <div className="flex items-center justify-between gap-3">
              {needsAttention.length ? (
                <Button variant="ghost" size="sm" className="-ml-3" onClick={goToNextMissing}>
                  <CircleAlert className="text-warning" /> {needsAttention.length} to finish · Next
                </Button>
              ) : (
                <span className="flex items-center gap-1.5 text-[0.8rem] text-fg-soft"><Check className="size-4 text-success" /> Required details complete</span>
              )}
              <Button size="sm" disabled={saving} onClick={() => void finish()}>
                {saving ? <LoaderCircle className="spin" /> : null}
                {saving ? "Saving…" : agentSaved ? "Save details" : "Done"}
              </Button>
            </div>
          </footer>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

/* ---------- Field shell ---------- */

type FieldState = "done" | "missing" | "invalid" | "optional";

function DetailShell({
  id,
  label,
  state,
  hint,
  error,
  token,
  children,
}: {
  id?: string;
  label: string;
  state: FieldState;
  hint?: ReactNode;
  error?: string;
  token?: string;
  children: ReactNode;
}) {
  return (
    <div id={id} className="grid min-w-0 scroll-mt-6 gap-2">
      <div className="flex items-center justify-between gap-3">
        <span className="text-[0.86rem] font-medium text-ink">{label}</span>
        {state === "invalid" ? (
          <span className="inline-flex items-center gap-1 text-[0.72rem] font-medium text-danger"><CircleAlert className="size-3.5" /> Check this</span>
        ) : state === "missing" ? (
          <span className="rounded-[4px] bg-warning/12 px-1.5 py-[3px] text-[0.68rem] font-medium leading-none text-warning">Required</span>
        ) : state === "done" ? (
          <Check aria-label="Done" className="size-4 text-success" />
        ) : (
          <span className="text-[0.72rem] text-fg-muted">Optional</span>
        )}
      </div>
      {children}
      {error || hint || token ? (
        <div className="flex items-start justify-between gap-3">
          <p className={cn("m-0 min-w-0 text-[0.76rem] leading-relaxed", error ? "text-danger" : "text-fg-muted")}>{error || hint}</p>
          {token ? <code className="shrink-0 pt-px font-mono text-[0.7rem] text-fg-muted">{`{{${token}}}`}</code> : null}
        </div>
      ) : null}
    </div>
  );
}

function DetailField({
  variable,
  value,
  inheritedValue,
  values,
  state,
  countryName,
  defaultPhoneRegion,
  phoneCountries,
  onChange,
}: {
  variable: AgentVariableDefinition;
  value: string;
  inheritedValue?: string;
  values: Record<string, string>;
  state: FieldState;
  countryName: string;
  defaultPhoneRegion: string;
  phoneCountries: CountryCallingCode[];
  onChange: (value: string) => void;
}) {
  const spec = detailSpec(variable.key);
  const structured = spec.kind === "structured" ? structuredAgentSetting(variable.key) : null;
  const error = detailError(variable.key, value);
  const inputId = `input-${variable.key}`;
  const example = variable.example?.trim();

  let control: ReactNode;
  if (structured) {
    control = (
      <RadioGroup aria-label={variable.label} value={value} onValueChange={onChange}>
        {structured.options.map((option) => <RadioCard key={option.value} value={option.value} title={option.label} description={option.description} className="p-3.5" />)}
      </RadioGroup>
    );
  } else if (spec.kind === "hours") {
    control = <HoursEditor value={value} onChange={onChange} />;
  } else if (spec.kind === "languages") {
    control = <LanguagesInput value={value} onChange={onChange} />;
  } else if (spec.kind === "language") {
    control = <LanguageSelect value={value} supported={values.supported_languages ?? ""} onChange={onChange} />;
  } else if (spec.kind === "choice") {
    control = <ChoiceInput label={variable.label} options={spec.options ?? []} allowOther={spec.allowOther} value={value} onChange={onChange} />;
  } else if (spec.kind === "multi") {
    control = <MultiInput label={variable.label} options={spec.options ?? []} allowOther={spec.allowOther} value={value} onChange={onChange} />;
  } else if (spec.kind === "list") {
    control = <ListInput label={variable.label} placeholder={spec.itemPlaceholder ?? "Add an item"} value={value} onChange={onChange} />;
  } else if (spec.kind === "faq") {
    control = <FaqInput value={value} onChange={onChange} />;
  } else if (spec.kind === "phone") {
    control = <PhoneInput countries={phoneCountries} defaultRegion={defaultPhoneRegion} value={value} onChange={onChange} invalid={Boolean(error)} />;
  } else if (spec.kind === "number") {
    control = (
      <div className="sm:max-w-[14rem]">
        <NumberField ariaLabel={variable.label} value={Number(value) || 0} min={0} max={500} unit={spec.unit} onChange={(next) => onChange(next ? String(next) : "")} />
      </div>
    );
  } else if (spec.kind === "longtext") {
    control = (
      <Textarea id={inputId} rows={3} className="min-h-0 text-[0.88rem]" value={value} onChange={(event) => onChange(event.target.value)}
        placeholder={example ? `e.g. ${example}` : `Describe ${variable.label.toLowerCase()}`} aria-invalid={Boolean(error)} />
    );
  } else {
    control = (
      <Input
        id={inputId}
        className={cn(controlClass, spec.kind === "url" && "font-mono text-[0.84rem]", error && "border-danger/60")}
        type={spec.kind === "url" ? "url" : "text"}
        inputMode={spec.kind === "url" ? "url" : undefined}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={spec.kind === "url" && value.trim() && !/^https?:\/\//i.test(value.trim()) ? () => onChange(`https://${value.trim()}`) : undefined}
        placeholder={spec.kind === "url" ? "https://" : spec.kind === "address" ? `Street, area, city in ${countryName}` : example ? `e.g. ${example}` : `Enter ${variable.label.toLowerCase()}`}
        aria-invalid={Boolean(error)}
      />
    );
  }

  return (
    <DetailShell
      id={`detail-${variable.key}`}
      label={variable.label}
      state={state}
      error={error}
      hint={!value.trim() && inheritedValue?.trim()
        ? <>From your <a href="/settings?tab=business" className="text-fg-soft underline underline-offset-2">business profile</a>: {inheritedValue.length > 120 ? `${inheritedValue.slice(0, 120)}…` : inheritedValue}. Fill this in only to use something else for this agent.</>
        : variable.description}
      token={variable.key}
    >
      {control}
    </DetailShell>
  );
}

/* ---------- Choice with presets ---------- */

function Pill({ selected, onClick, children, role = "radio" }: { selected: boolean; onClick: () => void; children: ReactNode; role?: "radio" | "checkbox" }) {
  return (
    <button
      type="button"
      role={role}
      aria-checked={selected}
      onClick={onClick}
      className={cn(
        "inline-flex min-h-8 cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-left font-sans text-[0.8rem] leading-snug transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal [&_svg]:size-3.5 [&_svg]:shrink-0",
        selected ? "border-signal/45 bg-signal/[0.1] font-medium text-ink" : "border-line-strong bg-transparent text-fg-soft hover:border-fg-muted/50 hover:text-ink",
      )}
    >
      {selected ? <Check className="text-signal" /> : null}
      {children}
    </button>
  );
}

function ChoiceInput({ label, options, allowOther, value, onChange }: { label: string; options: string[]; allowOther?: boolean; value: string; onChange: (value: string) => void }) {
  const isPreset = options.includes(value);
  const [otherOpen, setOtherOpen] = useState(Boolean(value) && !isPreset);
  const otherRef = useRef<HTMLInputElement>(null);
  useEffect(() => { if (value && !options.includes(value)) setOtherOpen(true); }, [options, value]);

  return (
    <div className="grid gap-2.5">
      <div role="radiogroup" aria-label={label} className="flex flex-wrap gap-2">
        {options.map((option) => (
          <Pill key={option} selected={value === option} onClick={() => { setOtherOpen(false); onChange(value === option ? "" : option); }}>{option}</Pill>
        ))}
        {allowOther ? (
          <Pill selected={otherOpen} onClick={() => { setOtherOpen(true); if (isPreset) onChange(""); window.requestAnimationFrame(() => otherRef.current?.focus()); }}>Other…</Pill>
        ) : null}
      </div>
      {allowOther && otherOpen ? (
        <Input ref={otherRef} aria-label={`${label}, your own answer`} className={controlClass} value={isPreset ? "" : value} onChange={(event) => onChange(event.target.value)} placeholder="Type your own answer" />
      ) : null}
    </div>
  );
}

function MultiInput({ label, options, allowOther, value, onChange }: { label: string; options: string[]; allowOther?: boolean; value: string; onChange: (value: string) => void }) {
  const selected = parseList(value);
  const custom = selected.filter((item) => !options.some((option) => option.toLowerCase() === item.toLowerCase()));
  const [draft, setDraft] = useState("");

  function toggle(item: string) {
    const has = selected.some((current) => current.toLowerCase() === item.toLowerCase());
    onChange(serializeList(has ? selected.filter((current) => current.toLowerCase() !== item.toLowerCase()) : [...selected, item]));
  }

  function addCustom() {
    const item = draft.trim().replace(/[,;]+/g, " ");
    if (!item) return;
    if (!selected.some((current) => current.toLowerCase() === item.toLowerCase())) onChange(serializeList([...selected, item]));
    setDraft("");
  }

  return (
    <div className="grid gap-2.5">
      <div role="group" aria-label={label} className="flex flex-wrap gap-2">
        {options.map((option) => (
          <Pill key={option} role="checkbox" selected={selected.some((item) => item.toLowerCase() === option.toLowerCase())} onClick={() => toggle(option)}>{option}</Pill>
        ))}
        {custom.map((item) => (
          <Pill key={item} role="checkbox" selected onClick={() => toggle(item)}>{item}<X className="text-fg-muted" aria-label={`Remove ${item}`} /></Pill>
        ))}
      </div>
      {allowOther ? (
        <AddRow value={draft} onChange={setDraft} onAdd={addCustom} placeholder="Add your own" label={`Add to ${label.toLowerCase()}`} />
      ) : null}
    </div>
  );
}

function AddRow({ value, onChange, onAdd, placeholder, label }: { value: string; onChange: (value: string) => void; onAdd: () => void; placeholder: string; label: string }) {
  return (
    <div className="flex gap-2">
      <Input
        aria-label={label}
        className={controlClass}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); onAdd(); } }}
        onPaste={(event) => {
          const text = event.clipboardData.getData("text");
          if (/\r?\n/.test(text)) {
            event.preventDefault();
            onChange(text);
            window.setTimeout(onAdd, 0);
          }
        }}
        placeholder={placeholder}
      />
      <Button variant="secondary" size="sm" className="h-10 shrink-0" disabled={!value.trim()} onClick={onAdd}><Plus /> Add</Button>
    </div>
  );
}

/* ---------- Lists (services, staff…) ---------- */

function ListInput({ label, placeholder, value, onChange }: { label: string; placeholder: string; value: string; onChange: (value: string) => void }) {
  const items = parseList(value);
  const [draft, setDraft] = useState("");

  function add() {
    const incoming = draft.split(/\r?\n/).map((item) => item.trim().replace(/;+/g, " ")).filter(Boolean);
    const next = [...items];
    for (const item of incoming) if (!next.some((current) => current.toLowerCase() === item.toLowerCase())) next.push(item);
    onChange(serializeList(next));
    setDraft("");
  }

  function remove(index: number) {
    onChange(serializeList(items.filter((_, current) => current !== index)));
  }

  return (
    <div className="grid gap-2">
      {items.length ? (
        <ul aria-label={label} className="m-0 grid list-none divide-y divide-line overflow-hidden rounded-[var(--radius-control)] border border-line p-0">
          {items.map((item, index) => (
            <li key={`${item}-${index}`} className="flex items-center gap-2 py-1.5 pl-3.5 pr-1.5 text-[0.86rem] text-fg">
              <span className="min-w-0 flex-1 truncate">{item}</span>
              <Button variant="ghost" size="icon-xs" aria-label={`Remove ${item}`} className="text-fg-muted hover:bg-danger/[0.08] hover:text-danger" onClick={() => remove(index)}><X /></Button>
            </li>
          ))}
        </ul>
      ) : null}
      <AddRow value={draft} onChange={setDraft} onAdd={add} placeholder={placeholder} label={`Add to ${label.toLowerCase()}`} />
      <p className="m-0 text-[0.72rem] text-fg-muted">Press Enter to add. Paste several lines to add them all at once.</p>
    </div>
  );
}

/* ---------- FAQ ---------- */

function FaqInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [pairs, setPairs] = useState<FaqPair[]>(() => parseFaq(value));
  const lastWritten = useRef(value);
  useEffect(() => {
    if (value !== lastWritten.current) {
      setPairs(parseFaq(value));
      lastWritten.current = value;
    }
  }, [value]);

  function update(next: FaqPair[]) {
    setPairs(next);
    const serialized = serializeFaq(next);
    lastWritten.current = serialized;
    onChange(serialized);
  }

  return (
    <div className="grid gap-2.5">
      {pairs.map((pair, index) => (
        <div key={index} className="grid gap-2 rounded-[var(--radius-control)] border border-line p-3">
          <div className="flex items-center gap-2">
            <Input aria-label={`Question ${index + 1}`} className="h-9 text-[0.86rem] font-medium" value={pair.question} placeholder="Question, e.g. Is there parking?"
              onChange={(event) => update(pairs.map((current, i) => (i === index ? { ...current, question: event.target.value } : current)))} />
            <Button variant="ghost" size="icon-xs" aria-label={`Remove question ${index + 1}`} className="shrink-0 text-fg-muted hover:bg-danger/[0.08] hover:text-danger"
              onClick={() => update(pairs.filter((_, i) => i !== index))}><Trash2 /></Button>
          </div>
          <Textarea aria-label={`Answer ${index + 1}`} rows={2} className="min-h-0 text-[0.86rem]" value={pair.answer} placeholder="Approved answer the agent may give"
            onChange={(event) => update(pairs.map((current, i) => (i === index ? { ...current, answer: event.target.value } : current)))} />
        </div>
      ))}
      <Button variant="secondary" size="xs" className="w-fit" onClick={() => update([...pairs, { question: "", answer: "" }])}><Plus /> Add question</Button>
    </div>
  );
}

/* ---------- Languages ---------- */

function LanguagesInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const selected = parseList(value);
  const common = LANGUAGE_OPTIONS.map(([, label]) => label as string);
  const custom = selected.filter((item) => !common.some((label) => label.toLowerCase() === item.toLowerCase()));
  const more = MORE_LANGUAGE_CODES.map((code) => languageLabel(code))
    .filter((label) => !selected.some((item) => item.toLowerCase() === label.toLowerCase()))
    .sort();

  function toggle(label: string) {
    const has = selected.some((item) => item.toLowerCase() === label.toLowerCase());
    onChange(serializeList(has ? selected.filter((item) => item.toLowerCase() !== label.toLowerCase()) : [...selected, label]));
  }

  return (
    <div className="grid gap-2.5">
      <div role="group" aria-label="Supported languages" className="flex flex-wrap gap-2">
        {common.map((label) => (
          <Pill key={label} role="checkbox" selected={selected.some((item) => item.toLowerCase() === label.toLowerCase())} onClick={() => toggle(label)}>{label}</Pill>
        ))}
        {custom.map((label) => (
          <Pill key={label} role="checkbox" selected onClick={() => toggle(label)}>{label}<X className="text-fg-muted" aria-label={`Remove ${label}`} /></Pill>
        ))}
      </div>
      <div className="sm:max-w-[16rem]">
        <Select
          size="sm"
          ariaLabel="Add another language"
          placeholder="Add another language…"
          searchable
          searchPlaceholder="Find a language"
          value=""
          onValueChange={(label) => toggle(label)}
          options={more.map((label) => ({ value: label, label }))}
        />
      </div>
    </div>
  );
}

function LanguageSelect({ value, supported, onChange }: { value: string; supported: string; onChange: (value: string) => void }) {
  const supportedLabels = parseList(supported);
  const others = [...LANGUAGE_OPTIONS.map(([, label]) => label as string), ...MORE_LANGUAGE_CODES.map((code) => languageLabel(code))]
    .filter((label) => !supportedLabels.some((item) => item.toLowerCase() === label.toLowerCase()));
  const options = Array.from(new Set([...supportedLabels, ...(value && !supportedLabels.includes(value) ? [value] : []), ...others]));
  return (
    <div className="sm:max-w-[16rem]">
      <Select
        size="md"
        className={controlClass}
        ariaLabel="Fallback language"
        placeholder="Choose a language"
        searchable
        searchPlaceholder="Find a language"
        value={value}
        onValueChange={onChange}
        options={options.map((label) => ({ value: label, label, description: supportedLabels.includes(label) ? "Supported by this agent" : undefined }))}
      />
    </div>
  );
}

/* ---------- Phone ---------- */

function PhoneInput({ countries, defaultRegion, value, onChange, invalid }: {
  countries: CountryCallingCode[];
  defaultRegion: string;
  value: string;
  onChange: (value: string) => void;
  invalid: boolean;
}) {
  const [region, setRegion] = useState(defaultRegion);

  useEffect(() => {
    if (!countries.length) return;
    const digits = value.replace(/\D/g, "");
    const preferred = countries.find((country) => country.region === (region || defaultRegion));
    const matched = preferred && digits.startsWith(preferred.dialingCode.slice(1))
      ? preferred
      : [...countries].sort((left, right) => right.dialingCode.length - left.dialingCode.length)
        .find((country) => digits && digits.startsWith(country.dialingCode.slice(1)));
    setRegion(matched?.region || preferred?.region || countries[0].region);
  }, [countries, defaultRegion, region, value]);

  const selected = countries.find((country) => country.region === region)
    || countries.find((country) => country.region === defaultRegion)
    || countries[0];
  const digits = value.replace(/\D/g, "");
  const prefix = selected?.dialingCode.slice(1) || "";
  const national = prefix && digits.startsWith(prefix) ? digits.slice(prefix.length) : digits;

  // Country list unavailable: accept the full international number directly.
  if (!countries.length) {
    return (
      <Input
        aria-label="Phone number"
        inputMode="tel"
        type="tel"
        className={cn(controlClass, "font-mono tabular-nums", invalid && "border-danger/60")}
        value={value}
        placeholder="+254 712 345 678"
        onChange={(event) => onChange(event.target.value.replace(/[^\d+\s()-]/g, ""))}
      />
    );
  }

  return (
    <div className="grid grid-cols-[minmax(0,11rem)_minmax(0,1fr)] gap-2 max-sm:grid-cols-[minmax(0,8rem)_minmax(0,1fr)]">
      <Select
        size="md"
        className={controlClass}
        ariaLabel="Country calling code"
        disabled={!countries.length}
        value={selected?.region || ""}
        onValueChange={(nextRegion) => {
          const country = countries.find((item) => item.region === nextRegion);
          setRegion(nextRegion);
          onChange(national && country ? `${country.dialingCode} ${national}` : "");
        }}
        searchable
        searchPlaceholder="Search country or code…"
        options={countries.map((country) => ({ value: country.region, label: `${country.dialingCode} ${country.name}`, keywords: [country.region, country.dialingCode] }))}
      />
      <Input
        aria-label="Phone number"
        inputMode="tel"
        type="tel"
        className={cn(controlClass, "font-mono tabular-nums", invalid && "border-danger/60")}
        value={national}
        placeholder="712 345 678"
        onChange={(event) => {
          const next = event.target.value.replace(/\D/g, "");
          onChange(next && selected ? `${selected.dialingCode} ${next}` : "");
        }}
      />
    </div>
  );
}

/* ---------- Weekly hours ---------- */

const TIME_OPTIONS = Array.from({ length: 96 }, (_, index) => {
  const value = `${String(Math.floor(index / 4)).padStart(2, "0")}:${String((index % 4) * 15).padStart(2, "0")}`;
  return { value, label: formatTime(value) };
});

function formatTime(value: string) {
  const [hourText, minute = "00"] = value.split(":");
  const hour = Number(hourText);
  return `${hour % 12 || 12}:${minute} ${hour >= 12 ? "PM" : "AM"}`;
}

function timeOptions(value: string) {
  return TIME_OPTIONS.some((option) => option.value === value)
    ? TIME_OPTIONS
    : [...TIME_OPTIONS, { value, label: formatTime(value) }].sort((left, right) => left.value.localeCompare(right.value));
}

const HOUR_PRESETS: Array<{ label: string; build: () => WeeklySchedule }> = [
  { label: "Weekdays 9–5", build: () => withDays(["Mon", "Tue", "Wed", "Thu", "Fri"], "09:00", "17:00") },
  { label: "Mon–Sat 8–6", build: () => withDays(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat"], "08:00", "18:00") },
  { label: "Every day 9–9", build: () => withDays(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"], "09:00", "21:00") },
];

function withDays(days: DayKey[], open: string, close: string) {
  const schedule = emptySchedule();
  for (const day of days) schedule[day] = { enabled: true, open, close };
  return schedule;
}

function HoursEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const schedule = parseSchedule(value);
  const firstOpen = WEEK_DAYS.find(([day]) => schedule[day].enabled)?.[0];

  function update(day: DayKey, patch: Partial<WeeklySchedule[DayKey]>) {
    onChange(serializeSchedule({ ...schedule, [day]: { ...schedule[day], ...patch } }));
  }

  function copyToOpenDays() {
    if (!firstOpen) return;
    const source = schedule[firstOpen];
    const next = { ...schedule };
    for (const [day] of WEEK_DAYS) if (next[day].enabled) next[day] = { ...next[day], open: source.open, close: source.close };
    onChange(serializeSchedule(next));
  }

  return (
    <div className="grid gap-2.5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-[0.76rem] text-fg-muted">Quick set:</span>
        {HOUR_PRESETS.map((preset) => (
          <Pill key={preset.label} selected={serializeSchedule(preset.build()) === serializeSchedule(schedule)} onClick={() => onChange(serializeSchedule(preset.build()))}>{preset.label}</Pill>
        ))}
      </div>
      <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-control)] border border-line">
        {WEEK_DAYS.map(([day, label]) => {
          const current = schedule[day];
          const overnight = current.enabled && current.close < current.open;
          return (
            <div key={day} className="grid min-h-12 grid-cols-[7.5rem_minmax(0,1fr)] items-center gap-3 px-3 py-1.5 max-sm:grid-cols-1 max-sm:gap-1.5 max-sm:py-2.5">
              <label className="flex cursor-pointer items-center gap-2.5 text-[0.84rem] font-medium text-ink">
                <Checkbox checked={current.enabled} onCheckedChange={(checked) => update(day, { enabled: checked === true })} aria-label={`Open on ${label}`} />
                {label}
              </label>
              {current.enabled ? (
                <div className="flex flex-wrap items-center gap-2">
                  <div className="w-[7.25rem]"><Select size="sm" ariaLabel={`${label} opening time`} value={current.open} onValueChange={(open) => update(day, { open })} options={timeOptions(current.open)} searchPlaceholder="Find a time" /></div>
                  <span className="text-[0.78rem] text-fg-muted">to</span>
                  <div className="w-[7.25rem]"><Select size="sm" ariaLabel={`${label} closing time`} value={current.close} onValueChange={(close) => update(day, { close })} options={timeOptions(current.close)} searchPlaceholder="Find a time" /></div>
                  {overnight ? <span className="text-[0.72rem] text-fg-muted">next day</span> : null}
                </div>
              ) : (
                <span className="text-[0.82rem] text-fg-muted">Closed</span>
              )}
            </div>
          );
        })}
      </div>
      {firstOpen ? (
        <Button variant="ghost" size="xs" className="-ml-3 w-fit" onClick={copyToOpenDays}>
          <Copy /> Use {WEEK_DAYS.find(([day]) => day === firstOpen)?.[1]}’s hours for every open day
        </Button>
      ) : null}
    </div>
  );
}
