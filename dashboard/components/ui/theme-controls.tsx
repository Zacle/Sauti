"use client";

/** Console appearance controls: a compact header popover and the full Settings panel. */

import * as React from "react";
import * as PopoverPrimitive from "@radix-ui/react-popover";
import { Check, Monitor, Moon, Sun } from "lucide-react";
import { THEME_ACCENTS, type ThemeAccent, type ThemeMode, useConsoleTheme } from "@/lib/console-theme";
import { cn } from "@/lib/utils";

const MODES: Array<{ value: ThemeMode; label: string; icon: typeof Sun }> = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
];

function ModeSegments({ mode, onChange }: { mode: ThemeMode; onChange: (mode: ThemeMode) => void }) {
  return (
    <div role="radiogroup" aria-label="Colour mode" className="grid grid-cols-3 gap-0.5 rounded-[8px] border border-line bg-surface-2 p-0.5">
      {MODES.map(({ value, label, icon: Icon }) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={mode === value}
          onClick={() => onChange(value)}
          className={cn(
            "flex h-7 cursor-pointer items-center justify-center gap-1.5 rounded-[6px] border-0 font-sans text-[0.78rem] transition-colors",
            mode === value ? "bg-selected text-selected-fg shadow-[var(--shadow-card)]" : "bg-transparent text-fg-soft hover:text-fg",
          )}
        >
          <Icon className="size-3.5" />
          {label}
        </button>
      ))}
    </div>
  );
}

function AccentSwatches({ accent, dark, onChange, size = "sm" }: { accent: ThemeAccent; dark: boolean; onChange: (accent: ThemeAccent) => void; size?: "sm" | "lg" }) {
  return (
    <div role="radiogroup" aria-label="Accent colour" className={cn("flex flex-wrap", size === "lg" ? "gap-3" : "gap-2")}>
      {THEME_ACCENTS.map((item) => {
        const selected = accent === item.value;
        return (
          <button
            key={item.value}
            type="button"
            role="radio"
            aria-checked={selected}
            aria-label={item.label}
            title={item.label}
            onClick={() => onChange(item.value)}
            className={cn(
              "group grid cursor-pointer place-items-center rounded-full border-0 p-0 outline-offset-2 transition-transform active:scale-95",
              size === "lg" ? "size-9" : "size-6",
              selected ? "ring-2 ring-ink ring-offset-2 ring-offset-[var(--color-popover)]" : "hover:scale-110",
            )}
            style={{ background: dark ? item.dark : item.light }}
          >
            {selected ? <Check className={cn("text-white mix-blend-difference", size === "lg" ? "size-4" : "size-3")} strokeWidth={3} /> : null}
          </button>
        );
      })}
    </div>
  );
}

/** Header button: quick light/dark switch on click, full options in the popover. */
export function AppearanceMenu() {
  const theme = useConsoleTheme();
  const dark = theme.resolved === "dark";
  return (
    <PopoverPrimitive.Root>
      <PopoverPrimitive.Trigger asChild>
        <button
          type="button"
          aria-label="Appearance"
          title="Appearance"
          className="grid size-8 cursor-pointer place-items-center rounded-[7px] border-0 bg-transparent p-0 text-fg-soft transition-colors hover:bg-tint/[0.06] hover:text-fg data-[state=open]:bg-tint/[0.06] data-[state=open]:text-fg"
        >
          {dark ? <Moon className="size-4" /> : <Sun className="size-4" />}
        </button>
      </PopoverPrimitive.Trigger>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="end"
          sideOffset={8}
          className="z-[200] w-[17rem] rounded-[var(--radius-popover)] border border-line-strong bg-popover p-3 font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[select-in_140ms_cubic-bezier(0.16,1,0.3,1)]"
        >
          <p className="m-0 text-[0.8rem] font-medium text-ink">Appearance</p>
          <div className="mt-2.5"><ModeSegments mode={theme.mode} onChange={theme.setMode} /></div>
          <p className="m-0 mt-4 text-[0.75rem] text-fg-muted">Accent</p>
          <div className="mt-2"><AccentSwatches accent={theme.accent} dark={dark} onChange={theme.setAccent} /></div>
          <p className="m-0 mt-3.5 border-t border-line pt-2.5 text-[0.72rem] leading-snug text-fg-muted">Saved to your account. Only you see it.</p>
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}

/** Mini mock of the console used as a theme preview card. */
function ModePreview({ mode }: { mode: ThemeMode }) {
  const palette = (dark: boolean) => dark
    ? { canvas: "#121211", surface: "#1a1a19", line: "#2a2a28", text: "#e6e4df", soft: "#3a3936" }
    : { canvas: "#f7f6f3", surface: "#ffffff", line: "#e7e5df", text: "#23221f", soft: "#d6d3cb" };
  const half = (dark: boolean) => {
    const p = palette(dark);
    return (
      <div className="flex h-full" style={{ background: p.canvas }}>
        <div className="flex w-[30%] flex-col gap-1 p-1.5">
          <span className="h-1.5 w-3/4 rounded-full" style={{ background: p.text, opacity: 0.8 }} />
          <span className="mt-1 h-1 w-full rounded-full" style={{ background: p.soft }} />
          <span className="h-1 w-2/3 rounded-full" style={{ background: p.soft }} />
          <span className="h-1 w-3/4 rounded-full" style={{ background: p.soft }} />
        </div>
        <div className="m-1 ml-0 flex flex-1 flex-col gap-1 rounded-[3px] border p-1.5" style={{ background: p.surface, borderColor: p.line }}>
          <span className="h-1.5 w-1/2 rounded-full" style={{ background: p.text, opacity: 0.85 }} />
          <span className="mt-auto h-5 rounded-[2px] bg-signal opacity-80" />
        </div>
      </div>
    );
  };
  if (mode === "system") {
    return (
      <div className="relative h-full">
        {half(false)}
        <div className="absolute inset-0 [clip-path:polygon(100%_0,100%_100%,0_100%)]">{half(true)}</div>
      </div>
    );
  }
  return half(mode === "dark");
}

/** Full appearance editor for the Settings page. */
export function AppearanceSettings() {
  const theme = useConsoleTheme();
  const dark = theme.resolved === "dark";
  return (
    <div className="grid gap-8">
      <section>
        <h3 className="m-0 text-[0.9rem] font-medium text-ink">Colour mode</h3>
        <p className="m-0 mt-1 text-[0.82rem] text-fg-muted">System follows your device and switches automatically.</p>
        <div role="radiogroup" aria-label="Colour mode" className="mt-4 grid max-w-xl grid-cols-1 gap-3 sm:grid-cols-3">
          {MODES.map(({ value, label, icon: Icon }) => {
            const selected = theme.mode === value;
            return (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => theme.setMode(value)}
                className={cn(
                  "cursor-pointer overflow-hidden rounded-[10px] border bg-surface p-0 text-left font-sans transition-colors",
                  selected ? "border-signal ring-2 ring-signal/25" : "border-line hover:border-line-strong",
                )}
              >
                <div className="h-20 border-b border-line"><ModePreview mode={value} /></div>
                <span className="flex items-center gap-2 px-3 py-2.5 text-[0.84rem] text-ink">
                  <Icon className="size-3.5 text-fg-muted" />
                  {label}
                  {selected ? <Check className="ml-auto size-3.5 text-signal" /> : null}
                </span>
              </button>
            );
          })}
        </div>
      </section>
      <section>
        <h3 className="m-0 text-[0.9rem] font-medium text-ink">Accent colour</h3>
        <p className="m-0 mt-1 text-[0.82rem] text-fg-muted">Used for highlights, charts, switches and progress bars.</p>
        <div className="mt-4"><AccentSwatches accent={theme.accent} dark={dark} onChange={theme.setAccent} size="lg" /></div>
        <p className="m-0 mt-3 text-[0.8rem] text-fg-soft">{THEME_ACCENTS.find((item) => item.value === theme.accent)?.label}</p>
      </section>
      <section className="max-w-xl rounded-[10px] border border-line bg-surface-2 p-4">
        <p className="m-0 text-[0.8rem] text-fg-muted">Preview</p>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span className="inline-flex h-8 items-center rounded-[7px] bg-primary px-3 text-[0.8rem] font-medium text-primary-fg">Primary action</span>
          <span className="inline-flex h-8 items-center rounded-[7px] bg-signal px-3 text-[0.8rem] font-medium text-signal-fg">Accent</span>
          <span className="inline-flex items-center gap-2 text-[0.82rem] text-fg"><span className="size-1.5 rounded-full bg-success" />Answered</span>
          <span className="h-1.5 w-28 overflow-hidden rounded-full bg-surface-3"><span className="block h-full w-2/3 rounded-full bg-signal" /></span>
        </div>
      </section>
      <p className="m-0 text-[0.78rem] text-fg-muted">Appearance is saved to your account, so it follows you to any browser. It doesn&apos;t change what your teammates see.</p>
    </div>
  );
}
