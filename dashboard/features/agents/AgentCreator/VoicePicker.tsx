"use client";

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { CircleAlert, Languages, LoaderCircle, Mic2, Pause, Play, Search, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { listVoices } from "@/lib/api/voices";
import type { VoiceOption } from "@/types/api";

type VoicePickerProps = {
  value: string;
  primaryLanguage: string;
  supportedLanguages: string[];
  onChange: (voiceId: string) => void;
};

export function VoicePicker({ value, primaryLanguage, supportedLanguages, onChange }: VoicePickerProps) {
  const [open, setOpen] = useState(false);
  const [voices, setVoices] = useState<VoiceOption[]>([]);
  const [enabledProviders, setEnabledProviders] = useState<string[]>([]);
  const [providerEnabled, setProviderEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [pendingVoiceId, setPendingVoiceId] = useState(value);
  const [playingId, setPlayingId] = useState("");
  const [bufferingId, setBufferingId] = useState("");
  const [languageFilter, setLanguageFilter] = useState("recommended");
  const [accentFilter, setAccentFilter] = useState("all");
  const [visibleLimit, setVisibleLimit] = useState(40);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const searchRef = useRef<HTMLInputElement | null>(null);
  const initialSelectionRef = useRef({ value, primaryLanguage, onChange });

  useEffect(() => {
    const initialSelection = initialSelectionRef.current;
    listVoices()
      .then((catalog) => {
        setVoices(catalog.voices);
        setEnabledProviders(catalog.enabledProviders);
        setProviderEnabled(catalog.enabledProviders.length > 0);
        const configuredVoice = catalog.voices.find((voice) => voice.id === initialSelection.value);
        if (!configuredVoice
          || !configuredVoice.languages.includes(initialSelection.primaryLanguage)) {
          const telnyxDefault = catalog.voices.find((voice) => voice.languages.includes(initialSelection.primaryLanguage))
            ?? catalog.voices[0];
          if (telnyxDefault) initialSelection.onChange(telnyxDefault.id);
        }
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load voices."))
      .finally(() => setLoading(false));
    return () => audioRef.current?.pause();
  }, []);

  useEffect(() => {
    if (open) setPendingVoiceId(value);
  }, [open, value]);

  const configuredLanguages = useMemo(
    () => Array.from(new Set([primaryLanguage, ...supportedLanguages])),
    [primaryLanguage, supportedLanguages],
  );
  const selectedVoice = voices.find((voice) => voice.id === value);
  const pendingVoice = voices.find((voice) => voice.id === pendingVoiceId);
  const visibleLanguages = useMemo(
    () => Array.from(new Set([
      ...configuredLanguages,
      ...voices.flatMap((voice) => voice.languages),
    ])).sort((left, right) => languageName(left).localeCompare(languageName(right))),
    [configuredLanguages, voices],
  );
  const availableLanguageCount = useMemo(
    () => visibleLanguages.filter((language) => voices.some((voice) => voice.languages.includes(language))).length,
    [visibleLanguages, voices],
  );
  const accentBaseVoices = useMemo(
    () => voices.filter((voice) => languageFilter === "recommended"
      ? coverage(voice, configuredLanguages) > 0
      : voice.languages.includes(languageFilter)),
    [configuredLanguages, languageFilter, voices],
  );
  const accents = useMemo(
    () => Array.from(new Set(
      accentBaseVoices
        .map((voice) => voice.traits.accent?.trim().toLowerCase())
        .filter((accent): accent is string => Boolean(accent)),
    )).sort(),
    [accentBaseVoices],
  );
  useEffect(() => {
    if (accentFilter !== "all" && !accents.includes(accentFilter)) {
      setAccentFilter("all");
    }
  }, [accentFilter, accents]);
  const filteredVoices = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return voices.filter((voice) => {
      const matchesAccent = accentFilter === "all"
        || voice.traits.accent?.trim().toLowerCase() === accentFilter;
      const matchesQuery = !normalizedQuery
        || `${voice.name} ${voice.description ?? ""} ${Object.values(voice.traits).join(" ")}`
          .toLowerCase()
          .includes(normalizedQuery);
      return matchesAccent && matchesQuery;
    });
  }, [accentFilter, query, voices]);
  const voiceCoverage = useMemo(
    () => Object.fromEntries(visibleLanguages.map((language) => [
      language,
      voices.filter((voice) => voice.languages.includes(language)).length,
    ])) as Record<string, number>,
    [visibleLanguages, voices],
  );
  const recommendedVoices = useMemo(
    () => [...filteredVoices]
      .sort((left, right) => compareVoiceQuality(left, right, configuredLanguages))
      .filter((voice) => coverage(voice, configuredLanguages) > 0),
    [configuredLanguages, filteredVoices],
  );
  const visibleLanguageVoices = languageFilter === "recommended"
    ? recommendedVoices
    : [...filteredVoices]
      .filter((voice) => voice.languages.includes(languageFilter))
      .sort((left, right) => compareVoiceQuality(left, right, [languageFilter]));
  const displayedVoices = visibleLanguageVoices.slice(0, visibleLimit);
  const unsupportedLanguage = languageFilter !== "recommended" && voiceCoverage[languageFilter] === 0;

  useEffect(() => {
    setVisibleLimit(40);
  }, [accentFilter, languageFilter, query]);

  async function preview(voice: VoiceOption) {
    const previewLanguage = previewLanguageFor(voice);
    if (!previewLanguage) return;
    if (playingId === voice.id) {
      audioRef.current?.pause();
      setPlayingId("");
      setBufferingId("");
      return;
    }
    audioRef.current?.pause();
    const audio = new Audio(
      `/api/v1/voices/${encodeURIComponent(voice.id)}/preview?language=${encodeURIComponent(previewLanguage)}`,
    );
    audio.preload = "auto";
    audioRef.current = audio;
    setBufferingId(voice.id);
    audio.addEventListener("playing", () => {
      setBufferingId("");
      setPlayingId(voice.id);
    });
    audio.addEventListener("waiting", () => setBufferingId(voice.id));
    audio.addEventListener("ended", () => {
      setPlayingId("");
      setBufferingId("");
    });
    audio.addEventListener("error", () => {
      setPlayingId("");
      setBufferingId("");
      toast.error(`Couldn’t play ${displayVoiceName(voice.name)}`, { description: "The preview is unavailable right now. Try another voice." });
    });
    try {
      await audio.play();
    } catch (caught) {
      setBufferingId("");
      if (caught instanceof DOMException && caught.name === "NotAllowedError") {
        toast.error("Your browser blocked audio", { description: "Allow sound for this site and try again." });
      } else {
        toast.error(`Couldn’t play ${displayVoiceName(voice.name)}`, { description: "The preview is unavailable right now. Try another voice." });
      }
    }
  }

  function close() {
    audioRef.current?.pause();
    setPlayingId("");
    setBufferingId("");
    setOpen(false);
  }

  function save() {
    onChange(pendingVoiceId);
    close();
  }

  const languageSelectOptions = [
    { value: "recommended", label: "Best match", description: "Ranked for this agent's languages" },
    ...visibleLanguages.map((language) => ({
      value: language,
      label: languageName(language),
      description: `${voiceCoverage[language] || 0} ${voiceCoverage[language] === 1 ? "voice" : "voices"}`,
    })),
  ];
  const accentSelectOptions = [
    { value: "all", label: "All accents" },
    ...accents.map((accent) => ({ value: accent, label: titleCase(accent) })),
  ];
  const fieldName = selectedVoice ? displayVoiceName(selectedVoice.name) : value ? "Custom provider voice" : "Choose a voice";
  const fieldDetail = selectedVoice
    ? [
        selectedVoice.languages.length > 1 ? "Multilingual" : languageName(selectedVoice.languages[0] ?? primaryLanguage),
        selectedVoice.traits.accent,
        selectedVoice.traits.gender,
      ].filter(Boolean).map((part) => titleCase(String(part))).join(" · ")
    : "Preview and pick a voice for your callers";

  return (
    <>
      <button
        className="voice-field group flex w-full cursor-pointer items-center gap-3 rounded-[var(--radius-control)] border border-line-strong bg-surface px-3 py-2.5 text-left shadow-[0_1px_2px_rgb(0_0_0/0.04)] transition-colors hover:border-fg-muted/45 hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal"
        type="button"
        onClick={() => setOpen(true)}
      >
        <VoiceAvatar name={fieldName} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[0.9rem] font-medium leading-snug text-ink">{fieldName}</span>
          <span className="block truncate text-[0.78rem] leading-snug text-fg-muted">{fieldDetail}</span>
        </span>
        <span className="text-[0.8rem] font-medium text-fg-soft transition-colors group-hover:text-ink">Change</span>
      </button>

      <DialogPrimitive.Root open={open} onOpenChange={(next) => (next ? setOpen(true) : close())}>
        <DialogPrimitive.Portal>
          <DialogPrimitive.Overlay className="fixed inset-0 z-[150] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
          <DialogPrimitive.Content
            aria-describedby={undefined}
            onOpenAutoFocus={(event) => { event.preventDefault(); searchRef.current?.focus(); }}
            className="fixed left-1/2 top-1/2 z-[151] flex max-h-[min(88dvh,760px)] w-[min(94vw,720px)] -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[var(--radius-card)] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)] max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:max-h-[90dvh] max-sm:w-full max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-b-none"
          >
            <header className="flex items-start justify-between gap-4 px-6 pb-4 pt-5">
              <div className="min-w-0">
                <DialogPrimitive.Title className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">Choose a voice</DialogPrimitive.Title>
                <p className="m-0 mt-1 text-[0.85rem] text-fg-soft">Listen first, then pick the voice your callers will hear.</p>
              </div>
              <DialogPrimitive.Close asChild>
                <Button variant="ghost" size="icon-xs" aria-label="Close voice picker" className="-mr-1.5"><X /></Button>
              </DialogPrimitive.Close>
            </header>

            <div className="grid grid-cols-[minmax(0,1fr)_11rem_10rem] gap-2 px-6 pb-4 max-sm:grid-cols-2">
              <label className="relative max-sm:col-span-2">
                <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
                <input
                  ref={searchRef}
                  aria-label="Search voices"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search by name, accent or style"
                  className="h-9 w-full rounded-[var(--radius-control)] border border-tint/[0.14] bg-tint/[0.03] pl-9 pr-3 font-sans text-[0.85rem] text-fg outline-none transition-[border-color,box-shadow] placeholder:text-fg-muted hover:border-tint/25 focus:border-signal/60 focus:ring-4 focus:ring-signal/10"
                />
              </label>
              <Select
                size="sm"
                ariaLabel="Language"
                icon={<Languages />}
                searchable
                searchPlaceholder="Find a language"
                value={languageFilter}
                onValueChange={setLanguageFilter}
                options={languageSelectOptions}
              />
              <Select
                size="sm"
                ariaLabel="Accent"
                value={accentFilter}
                onValueChange={setAccentFilter}
                options={accentSelectOptions}
                disabled={accents.length === 0}
              />
            </div>

            <div className="flex items-center justify-between gap-3 border-y border-line bg-tint/[0.02] px-6 py-2 text-[0.75rem] text-fg-muted">
              <span className="font-medium text-fg-soft">
                {languageFilter === "recommended" ? "Recommended for this agent" : `${languageName(languageFilter)} voices`}
              </span>
              <span className="tabular-nums">
                {loading ? "Loading…" : `${visibleLanguageVoices.length} of ${voices.length} voices · ${availableLanguageCount} languages`}
              </span>
            </div>

            <div className="min-h-[220px] flex-1 overflow-y-auto px-3 py-2">
              {loading && (
                <div className="grid gap-2 p-3">{[0, 1, 2, 3].map((item) => <div key={item} className="h-[60px] animate-pulse rounded-lg bg-tint/[0.05]" />)}</div>
              )}
              {!loading && error && (
                <PickerState icon={<CircleAlert />} title={error} detail="Check the configured voice provider credentials." />
              )}
              {!loading && !error && !providerEnabled && (
                <PickerState icon={<Mic2 />} title="The voice library is unavailable" detail="Ask a workspace administrator to check the voice service configuration." />
              )}
              {!loading && !error && providerEnabled && unsupportedLanguage && (
                <PickerState
                  icon={<CircleAlert />}
                  title={`No ${languageName(languageFilter)} voice yet`}
                  detail={unsupportedLanguageMessage(languageFilter, enabledProviders)}
                  action={<Button variant="secondary" size="xs" onClick={() => setLanguageFilter("recommended")}>Show best matches</Button>}
                />
              )}
              {!loading && !error && providerEnabled && !unsupportedLanguage && visibleLanguageVoices.length === 0 && (
                <PickerState
                  icon={<Search />}
                  title="No voices match these filters"
                  action={<Button variant="secondary" size="xs" onClick={() => { setQuery(""); setAccentFilter("all"); }}>Clear filters</Button>}
                />
              )}
              {!loading && !unsupportedLanguage && visibleLanguageVoices.length > 0 && (
                <div role="radiogroup" aria-label="Voices" className="grid gap-0.5">
                  {displayedVoices.map((voice, index) => {
                    const selected = pendingVoiceId === voice.id;
                    const name = displayVoiceName(voice.name);
                    const covered = coverage(voice, configuredLanguages);
                    const previewLanguage = previewLanguageFor(voice);
                    const playing = playingId === voice.id;
                    const buffering = bufferingId === voice.id;
                    const traits = [
                      languageFilter === "recommended" ? `${covered}/${configuredLanguages.length} languages` : null,
                      voice.traits.accent,
                      voice.traits.gender,
                      voice.traits.age,
                    ].filter(Boolean).map((trait) => titleCase(String(trait)));
                    return (
                      <div
                        key={voice.id}
                        role="radio"
                        aria-checked={selected}
                        tabIndex={0}
                        onClick={() => setPendingVoiceId(voice.id)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            setPendingVoiceId(voice.id);
                          }
                        }}
                        className={cn(
                          "group grid cursor-pointer grid-cols-[18px_36px_minmax(0,1fr)_auto] items-center gap-3 rounded-lg border px-3 py-2.5 outline-none transition-colors focus-visible:ring-2 focus-visible:ring-signal/40",
                          selected ? "border-signal/35 bg-signal/[0.07]" : "border-transparent hover:bg-tint/[0.04]",
                        )}
                      >
                        <span
                          aria-hidden="true"
                          className={cn(
                            "grid size-[18px] place-items-center rounded-full border transition-colors",
                            selected ? "border-signal bg-signal" : "border-line-strong bg-surface group-hover:border-fg-muted",
                          )}
                        >
                          {selected ? <span className="size-1.5 rounded-full bg-signal-fg" /> : null}
                        </span>
                        <VoiceAvatar name={name} playing={playing} />
                        <span className="min-w-0">
                          <span className="flex min-w-0 items-center gap-2">
                            <span className="truncate text-[0.9rem] font-medium text-ink">{name}</span>
                            {languageFilter === "recommended" && index < 3 ? (
                              <span className="shrink-0 rounded-[4px] bg-signal/10 px-1.5 py-[3px] text-[0.68rem] font-medium leading-none text-signal">Top pick</span>
                            ) : null}
                          </span>
                          <span className="mt-0.5 block truncate text-[0.8rem] text-fg-soft">{voice.description || titleCase(voice.category)}</span>
                          {traits.length ? (
                            <span className="mt-1 block truncate text-[0.72rem] text-fg-muted">{traits.slice(0, 4).join(" · ")}</span>
                          ) : null}
                        </span>
                        <Button
                          variant={playing ? "soft" : "secondary"}
                          size="xs"
                          className="min-w-[5.5rem] rounded-full"
                          disabled={!previewLanguage}
                          aria-label={`${playing ? "Pause" : "Listen to"} ${name}${previewLanguage ? ` in ${languageName(previewLanguage)}` : ""}`}
                          title={previewLanguage ? `Listen in ${languageName(previewLanguage)}` : "No compatible preview language"}
                          onClick={(event) => { event.stopPropagation(); void preview(voice); }}
                        >
                          {buffering ? <LoaderCircle className="spin" /> : playing ? <Pause /> : <Play />}
                          {buffering ? "Loading" : playing ? "Pause" : "Listen"}
                        </Button>
                      </div>
                    );
                  })}
                  {displayedVoices.length < visibleLanguageVoices.length && (
                    <Button variant="ghost" size="xs" className="mx-auto my-2" onClick={() => setVisibleLimit((current) => current + 40)}>
                      Show more <span className="text-fg-muted">{visibleLanguageVoices.length - displayedVoices.length} left</span>
                    </Button>
                  )}
                </div>
              )}
            </div>

            <footer className="flex items-center justify-between gap-4 border-t border-line px-6 py-3.5 max-sm:flex-col max-sm:items-stretch">
              <p className="m-0 text-[0.78rem] leading-snug text-fg-muted">
                {pendingVoice ? <>Selected: <span className="font-medium text-ink">{displayVoiceName(pendingVoice.name)}</span></> : "Pick a voice to continue."}
              </p>
              <div className="flex gap-2 max-sm:justify-end">
                <Button variant="secondary" size="sm" onClick={close}>Cancel</Button>
                <Button size="sm" disabled={!pendingVoiceId} onClick={save}>Use this voice</Button>
              </div>
            </footer>
          </DialogPrimitive.Content>
        </DialogPrimitive.Portal>
      </DialogPrimitive.Root>
    </>
  );

  function previewLanguageFor(voice: VoiceOption) {
    if (languageFilter !== "recommended") {
      return voice.languages.includes(languageFilter) ? languageFilter : null;
    }
    return [primaryLanguage, ...configuredLanguages, "en"]
      .find((language) => voice.languages.includes(language)) ?? null;
  }
}

function coverage(voice: VoiceOption, languages: string[]) {
  return languages.filter((language) => voice.languages.includes(language)).length;
}

function languageName(code: string) {
  if (code === "multilingual") return "Multilingual";
  try {
    return new Intl.DisplayNames(["en"], { type: "language" }).of(code) ?? code.toUpperCase();
  } catch {
    return code.toUpperCase();
  }
}

function titleCase(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (character) => character.toUpperCase());
}

function displayVoiceName(value: string) {
  return value.replace(/^\[[^\]]+\]\s*/, "").trim() || "Untitled voice";
}

function unsupportedLanguageMessage(language: string, enabledProviders: string[]) {
  if (!enabledProviders.includes("telnyx")) {
    return "The voice service is not enabled. Ask a workspace administrator to check the provider configuration.";
  }
  return `No ${languageName(language)} voice is available yet. Choose a compatible language or voice.`;
}

function compareVoiceQuality(left: VoiceOption, right: VoiceOption, languages: string[]) {
  return providerRank(left.provider) - providerRank(right.provider)
    || coverage(right, languages) - coverage(left, languages)
    || modelRank(left.traits.model) - modelRank(right.traits.model)
    || nameRank(left.name) - nameRank(right.name)
    || categoryRank(left.category) - categoryRank(right.category)
    || left.name.localeCompare(right.name);
}

function modelRank(model = "") {
  const normalized = model.toLowerCase();
  if (normalized === "ultra") return 0;
  if (normalized === "naturalhd") return 1;
  if (normalized === "grok") return 2;
  if (normalized === "natural") return 3;
  if (normalized === "bayan") return 4;
  if (normalized.includes("libri")) return 9;
  return 5;
}

function nameRank(name: string) {
  return /^[\p{L}][\p{L}\p{M}\s'.-]+$/u.test(displayVoiceName(name)) ? 0 : 1;
}

function providerRank(provider: string) {
  return provider === "telnyx" ? 0 : 1;
}


function categoryRank(category: string) {
  return category === "professional" ? 0 : category === "generated" ? 1 : category === "native" ? 2 : 3;
}

function VoiceAvatar({ name, playing = false }: { name: string; playing?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className="relative grid size-9 shrink-0 place-items-center overflow-hidden rounded-full bg-signal/12 text-[0.85rem] font-semibold leading-none text-signal"
    >
      {playing ? (
        <span className="flex h-3.5 items-end gap-[2px]">
          {[0, 1, 2, 3].map((bar) => (
            <span
              key={bar}
              className="h-full w-[3px] origin-bottom rounded-full bg-signal"
              style={{ animation: `voice-eq 0.9s ease-in-out ${bar * 0.14}s infinite` }}
            />
          ))}
        </span>
      ) : (
        name.slice(0, 1).toUpperCase()
      )}
    </span>
  );
}

function PickerState({ icon, title, detail, action }: { icon: ReactNode; title: string; detail?: string; action?: ReactNode }) {
  return (
    <div className="grid place-items-center gap-1.5 px-6 py-12 text-center">
      <span className="mb-1 grid size-10 place-items-center rounded-full bg-tint/[0.06] text-fg-muted [&_svg]:size-[18px]">{icon}</span>
      <strong className="text-[0.9rem] font-medium text-ink">{title}</strong>
      {detail ? <p className="m-0 max-w-sm text-[0.82rem] leading-relaxed text-fg-soft">{detail}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
