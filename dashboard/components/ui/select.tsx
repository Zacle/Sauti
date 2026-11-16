"use client";

/**
 * The one dropdown used across Sauti (marketing, auth and console).
 *
 * - Short lists render a Radix Select (full keyboard + typeahead support).
 * - Long lists (more than SEARCH_THRESHOLD options, or `searchable`) render a
 *   Radix Popover with a cmdk command list, so countries, timezones and agents
 *   can be filtered by typing.
 *
 * Both modes share the same trigger, panel and item styles, and both work in
 * plain <form> submissions via `name` / `required`.
 */

import * as React from "react";
import * as SelectPrimitive from "@radix-ui/react-select";
import * as Popover from "@radix-ui/react-popover";
import { Command } from "cmdk";
import { Check, ChevronDown, ChevronUp, Search } from "lucide-react";
import { cn } from "@/lib/utils";

export type SelectOption = {
  value: string;
  label: string;
  /** Secondary line shown under the label in the list (not in the trigger). */
  description?: string;
  /** Extra words matched by search (e.g. country code, aliases). */
  keywords?: string[];
  disabled?: boolean;
};

export type SelectProps = {
  value: string;
  onValueChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  /** Accessible name when there is no visible <label> wrapping the control. */
  ariaLabel?: string;
  /** Leading icon inside the trigger. */
  icon?: React.ReactNode;
  name?: string;
  required?: boolean;
  disabled?: boolean;
  /** Force search on/off. Defaults to on when there are many options. */
  searchable?: boolean;
  searchPlaceholder?: string;
  size?: "sm" | "md";
  align?: "start" | "end";
  className?: string;
  id?: string;
};

const SEARCH_THRESHOLD = 12;

const triggerBase =
  "group/select relative flex w-full min-w-0 items-center gap-2.5 rounded-[var(--radius-control)] border border-tint/[0.14] bg-tint/[0.03] text-left font-sans text-fg transition-[border-color,background-color,box-shadow] duration-200 outline-none hover:border-tint/25 hover:bg-tint/[0.05] focus-visible:border-signal/60 focus-visible:ring-4 focus-visible:ring-signal/10 data-[state=open]:border-signal/50 data-[state=open]:bg-tint/[0.05] data-[state=open]:ring-4 data-[state=open]:ring-signal/10 disabled:cursor-not-allowed disabled:opacity-55";

const triggerSize = {
  sm: "h-9 px-3 text-[0.82rem]",
  md: "h-11 px-3.5 text-[0.95rem]",
} as const;

const panel =
  "z-[200] overflow-hidden rounded-[var(--radius-popover)] border border-tint/[0.12] bg-popover font-sans text-fg shadow-[var(--shadow-pop)] backdrop-blur-xl data-[state=open]:animate-[select-in_160ms_cubic-bezier(0.16,1,0.3,1)] data-[state=closed]:animate-[select-out_100ms_ease-in]";

const itemBase =
  "relative flex w-full cursor-pointer select-none items-center gap-2.5 rounded-lg py-2 pl-3 pr-9 text-[0.9rem] text-fg-soft outline-none transition-colors";
// Radix marks disabled items with an empty data-disabled attribute; cmdk writes
// data-disabled="true" | "false" on every item, so it needs the value match.
const radixDisabled = "data-[disabled]:pointer-events-none data-[disabled]:opacity-40";
const cmdkDisabled = "data-[disabled=true]:pointer-events-none data-[disabled=true]:opacity-40";

function Chevron() {
  return (
    <ChevronDown
      aria-hidden="true"
      className="ml-auto size-4 shrink-0 text-fg-muted transition-transform duration-200 group-data-[state=open]/select:rotate-180 group-data-[state=open]/select:text-fg"
    />
  );
}

function TriggerContent({ icon, label, placeholder }: { icon?: React.ReactNode; label?: string; placeholder?: string }) {
  return (
    <>
      {icon ? <span className="grid size-4 shrink-0 place-items-center text-signal [&_svg]:size-4">{icon}</span> : null}
      <span className={cn("min-w-0 flex-1 truncate", !label && "text-fg-muted")}>{label ?? placeholder ?? "Select…"}</span>
      <Chevron />
    </>
  );
}

function ItemBody({ option, selected }: { option: SelectOption; selected: boolean }) {
  return (
    <>
      <span className="min-w-0 flex-1">
        <span className={cn("block truncate", selected && "font-medium text-ink")}>{option.label}</span>
        {option.description ? <span className="mt-0.5 block truncate text-xs text-fg-muted">{option.description}</span> : null}
      </span>
      {selected ? <Check aria-hidden="true" className="absolute right-3 size-4 text-signal" /> : null}
    </>
  );
}

function itemValue(option: SelectOption) {
  return `${option.label} ${option.value}`;
}

export function Select(props: SelectProps) {
  const searchable = props.searchable ?? props.options.length > SEARCH_THRESHOLD;
  return searchable ? <SearchableSelect {...props} /> : <ListSelect {...props} />;
}

/* ---------- short lists: Radix Select ---------- */

function ListSelect({
  value,
  onValueChange,
  options,
  placeholder,
  ariaLabel,
  icon,
  name,
  required,
  disabled,
  size = "md",
  align = "start",
  className,
  id,
}: SelectProps) {
  const selected = options.find((option) => option.value === value);
  return (
    <SelectPrimitive.Root
      value={value}
      onValueChange={onValueChange}
      name={name}
      required={required}
      disabled={disabled}
    >
      <SelectPrimitive.Trigger id={id} aria-label={ariaLabel} className={cn(triggerBase, triggerSize[size], className)}>
        <TriggerContent icon={icon} label={selected?.label} placeholder={placeholder} />
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          position="popper"
          sideOffset={6}
          align={align}
          collisionPadding={12}
          className={cn(panel, "max-h-[min(22rem,var(--radix-select-content-available-height))] min-w-[var(--radix-select-trigger-width)]")}
        >
          <SelectPrimitive.ScrollUpButton className="flex h-7 items-center justify-center text-fg-muted">
            <ChevronUp className="size-4" aria-hidden="true" />
          </SelectPrimitive.ScrollUpButton>
          <SelectPrimitive.Viewport className="p-1.5">
            {options.map((option) => (
              <SelectPrimitive.Item
                key={option.value}
                value={option.value}
                disabled={option.disabled}
                className={cn(itemBase, radixDisabled, "data-[highlighted]:bg-tint/[0.07] data-[highlighted]:text-ink")}
              >
                <SelectPrimitive.ItemText asChild>
                  <span className="sr-only">{option.label}</span>
                </SelectPrimitive.ItemText>
                <ItemBody option={option} selected={option.value === value} />
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
          <SelectPrimitive.ScrollDownButton className="flex h-7 items-center justify-center text-fg-muted">
            <ChevronDown className="size-4" aria-hidden="true" />
          </SelectPrimitive.ScrollDownButton>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}

/* ---------- long lists: Popover + cmdk ---------- */

function SearchableSelect({
  value,
  onValueChange,
  options,
  placeholder,
  ariaLabel,
  icon,
  name,
  required,
  disabled,
  searchPlaceholder = "Search…",
  size = "md",
  align = "start",
  className,
  id,
}: SelectProps) {
  const [open, setOpen] = React.useState(false);
  const [highlighted, setHighlighted] = React.useState("");
  const selected = options.find((option) => option.value === value);
  const listId = React.useId();
  const triggerRef = React.useRef<HTMLButtonElement>(null);

  function choose(next: string) {
    onValueChange(next);
    setOpen(false);
  }

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <span className="relative block min-w-0">
        <Popover.Trigger
          ref={triggerRef}
          id={id}
          type="button"
          disabled={disabled}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-label={ariaLabel}
          data-state={open ? "open" : "closed"}
          className={cn(triggerBase, triggerSize[size], className)}
        >
          <TriggerContent icon={icon} label={selected?.label} placeholder={placeholder} />
        </Popover.Trigger>
        {name ? (
          // Keeps native form submission + `required` validation working.
          <input
            tabIndex={-1}
            aria-hidden="true"
            name={name}
            required={required}
            value={value}
            onChange={() => undefined}
            className="pointer-events-none absolute inset-x-0 bottom-0 h-px w-full opacity-0"
          />
        ) : null}
      </span>
      <Popover.Portal>
        <Popover.Content
          align={align}
          sideOffset={6}
          collisionPadding={12}
          className={cn(panel, "w-[max(var(--radix-popover-trigger-width),16rem)]")}
          onCloseAutoFocus={(event) => {
            // Return focus a frame later: if it lands on the trigger during the
            // same Enter keypress, the browser "clicks" it and the list reopens.
            event.preventDefault();
            requestAnimationFrame(() => triggerRef.current?.focus({ preventScroll: true }));
          }}
        >
          <Command
            id={listId}
            loop
            value={highlighted}
            onValueChange={setHighlighted}
            onKeyDown={(event) => {
              // Handle Enter here (and prevent its default) so the keypress can't
              // also "click" the trigger once focus returns to it and reopen the list.
              if (event.key !== "Enter") return;
              event.preventDefault();
              const match = options.find((option) => itemValue(option).toLowerCase() === highlighted.toLowerCase());
              if (match && !match.disabled) choose(match.value);
            }}
            filter={(itemValue, search, keywords) => {
              const haystack = `${itemValue} ${(keywords ?? []).join(" ")}`.toLowerCase();
              return search
                .toLowerCase()
                .split(/\s+/)
                .filter(Boolean)
                .every((term) => haystack.includes(term))
                ? 1
                : 0;
            }}
          >
            <div className="flex items-center gap-2 border-b border-tint/[0.08] px-3.5">
              <Search className="size-4 shrink-0 text-fg-muted" aria-hidden="true" />
              <Command.Input
                autoFocus
                placeholder={searchPlaceholder}
                className="h-11 w-full appearance-none border-0 bg-transparent p-0 font-sans text-[0.9rem] text-fg shadow-none outline-none ring-0 placeholder:text-fg-muted focus:outline-none focus:ring-0"
              />
            </div>
            <Command.List className="max-h-[min(20rem,var(--radix-popover-content-available-height))] overflow-y-auto overscroll-contain p-1.5">
              <Command.Empty className="px-3 py-6 text-center text-sm text-fg-muted">No matches</Command.Empty>
              {options.map((option) => (
                <Command.Item
                  key={option.value}
                  value={itemValue(option)}
                  keywords={option.keywords}
                  disabled={option.disabled}
                  onSelect={() => choose(option.value)}
                  className={cn(itemBase, cmdkDisabled, "data-[selected=true]:bg-tint/[0.07] data-[selected=true]:text-ink")}
                >
                  <ItemBody option={option} selected={option.value === value} />
                </Command.Item>
              ))}
            </Command.List>
          </Command>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
