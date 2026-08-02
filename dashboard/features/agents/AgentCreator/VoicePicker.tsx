"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, CircleAlert, Languages, LoaderCircle, Mic2, Pause, Play, Search, X } from "lucide-react";
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
  const [previewError, setPreviewError] = useState("");
  const [languageFilter, setLanguageFilter] = useState("recommended");
  const [languageOpen, setLanguageOpen] = useState(false);
  const [languageQuery, setLanguageQuery] = useState("");
  const [accentFilter, setAccentFilter] = useState("all");
  const [accentOpen, setAccentOpen] = useState(false);
  const [visibleLimit, setVisibleLimit] = useState(40);
  const audioRef = useRef<HTMLAudioElement | null>(null);
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
  const languageOptions = visibleLanguages.filter((language) =>
    languageName(language).toLowerCase().includes(languageQuery.trim().toLowerCase())
  );

  useEffect(() => {
    setVisibleLimit(40);
  }, [accentFilter, languageFilter, query]);

  async function preview(voice: VoiceOption) {
    const previewLanguage = previewLanguageFor(voice);
    if (!previewLanguage) return;
    setPreviewError("");
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
      setPreviewError(`The preview for ${voice.name} could not be played.`);
    });
    try {
      await audio.play();
    } catch (caught) {
      setBufferingId("");
      setPreviewError(caught instanceof DOMException && caught.name === "NotAllowedError"
        ? "Your browser blocked audio playback. Allow sound for this site and try again."
        : `The preview for ${displayVoiceName(voice.name)} is unavailable. Try another voice.`);
    }
  }

  function close() {
    audioRef.current?.pause();
    setPlayingId("");
    setBufferingId("");
    setPreviewError("");
    setLanguageOpen(false);
    setAccentOpen(false);
    setOpen(false);
  }

  function save() {
    onChange(pendingVoiceId);
    close();
  }

  return (
    <>
      <button className="voice-picker-field" type="button" onClick={() => setOpen(true)}>
        <span><Mic2 size={17} /></span>
        <div>
          <strong>{selectedVoice?.name ?? (value ? "Custom provider voice" : "Choose a voice")}</strong>
          <small>{selectedVoice ? `${providerName(selectedVoice.provider)} · ${selectedVoice.languages.length > 1 ? "Multilingual" : languageName(selectedVoice.languages[0] ?? primaryLanguage)} · ${selectedVoice.traits.description ?? selectedVoice.traits.accent ?? selectedVoice.category}` : "Preview and select a voice for the caller's language"}</small>
        </div>
        <ChevronDown size={16} />
      </button>

      {open && (
        <div className="voice-picker-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && close()}>
          <section className="voice-picker-modal" role="dialog" aria-modal="true" aria-label="Select voice">
            <header>
              <div>
                <span><Mic2 size={19} /></span>
                <div>
                  <h2>Select a voice <i>Available voices</i></h2>
                  <p>Listen and choose a voice that fits your callers.</p>
                </div>
              </div>
              <button type="button" onClick={close} aria-label="Close voice picker"><X size={19} /></button>
            </header>

            <div className="voice-picker-filters">
              <label className="voice-search">
                <Search aria-hidden="true" size={18} />
                <input
                  aria-label="Search voices"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search voices, accents, or styles..."
                />
              </label>
              <div className={`voice-language-filter ${languageOpen ? "open" : ""}`}>
                <button
                  className="voice-filter-trigger"
                  type="button"
                  aria-expanded={languageOpen}
                  aria-haspopup="menu"
                  onClick={() => {
                    setLanguageOpen((current) => !current);
                    setAccentOpen(false);
                  }}
                >
                  <Languages aria-hidden="true" size={17} />
                  <span>
                    <small>Language</small>
                    <strong>{languageFilter === "recommended" ? "Best match" : languageName(languageFilter)}</strong>
                  </span>
                  <ChevronDown aria-hidden="true" size={16} />
                </button>
                {languageOpen && (
                  <div className="voice-language-menu">
                    <label>
                      <Search aria-hidden="true" size={15} />
                      <input
                        autoFocus
                        aria-label="Search languages"
                        placeholder="Find a language..."
                        value={languageQuery}
                        onChange={(event) => setLanguageQuery(event.target.value)}
                      />
                    </label>
                    <div>
                      {!languageQuery && (
                        <button
                          className={languageFilter === "recommended" ? "selected" : ""}
                          type="button"
                          onClick={() => {
                            setLanguageFilter("recommended");
                            setLanguageOpen(false);
                          }}
                        >
                          <span><strong>Best match</strong><small>Agent languages</small></span>
                          {languageFilter === "recommended" && <Check size={14} />}
                        </button>
                      )}
                      {languageOptions.map((language) => (
                        <button
                          className={languageFilter === language ? "selected" : ""}
                          key={language}
                          type="button"
                          onClick={() => {
                            setLanguageFilter(language);
                            setLanguageOpen(false);
                            setLanguageQuery("");
                          }}
                        >
                          <span><strong>{languageName(language)}</strong><small>{voiceCoverage[language] || 0} voices</small></span>
                          {languageFilter === language && <Check size={14} />}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
              <div className={`voice-accent-filter ${accentOpen ? "open" : ""}`}>
                <button
                  className="voice-accent-trigger"
                  type="button"
                  aria-expanded={accentOpen}
                  aria-haspopup="menu"
                  onClick={() => {
                    setAccentOpen((current) => !current);
                    setLanguageOpen(false);
                  }}
                >
                  <span>
                    <small>Accent</small>
                    <strong>{accentFilter === "all" ? "All accents" : titleCase(accentFilter)}</strong>
                  </span>
                  <ChevronDown aria-hidden="true" size={16} />
                </button>
                {accentOpen && (
                  <div className="voice-accent-menu" role="menu">
                    {[["all", "All accents"], ...accents.map((accent) => [accent, titleCase(accent)] as [string, string])].map(([value, label]) => (
                      <button
                        className={accentFilter === value ? "selected" : ""}
                        key={value}
                        role="menuitemradio"
                        aria-checked={accentFilter === value}
                        type="button"
                        onClick={() => {
                          setAccentFilter(value);
                          setAccentOpen(false);
                        }}
                      >
                        {label}
                        {accentFilter === value && <Check size={14} />}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="voice-engine-summary">
              <span><Mic2 size={17} /></span>
              <div><strong>Voice library</strong><small>Choose a language, refine by accent, then listen before saving.</small></div>
              <i>{voices.length} voices · {availableLanguageCount} languages</i>
            </div>

            <div className="voice-picker-results">
              {previewError && <div className="voice-preview-error">{previewError}</div>}
              {loading && <div className="voice-picker-state"><LoaderCircle className="spin" size={22} /> Loading available voices...</div>}
              {!loading && error && <div className="voice-picker-state error">{error}<small>Check the configured TTS provider credentials.</small></div>}
              {!loading && !error && !providerEnabled && <div className="voice-picker-state">The voice library is unavailable.<small>Ask a workspace administrator to check the voice service configuration.</small></div>}
              {!loading && !error && providerEnabled && unsupportedLanguage && (
                <div className="voice-language-unavailable">
                  <CircleAlert aria-hidden="true" size={20} />
                  <div>
                    <strong>No compatible {languageName(languageFilter)} voice is available</strong>
                    <p>{unsupportedLanguageMessage(languageFilter, enabledProviders)}</p>
                  </div>
                </div>
              )}
              {!loading && !error && providerEnabled && !unsupportedLanguage && visibleLanguageVoices.length === 0 && (
                <div className="voice-picker-state">
                  No voices match these filters.
                  <button type="button" onClick={() => { setQuery(""); setAccentFilter("all"); }}>Clear filters</button>
                </div>
              )}
              {!unsupportedLanguage && visibleLanguageVoices.length > 0 && <section className="voice-language-group">
                <h3>
                  <span>{languageFilter === "recommended" ? "Recommended voices" : languageName(languageFilter)}</span>
                  <i>{languageFilter === "recommended" ? `Ranked across ${configuredLanguages.length} languages` : `${visibleLanguageVoices.length} compatible voices`}</i>
                </h3>
                <div>{displayedVoices.map((voice, index) => {
                const selected = pendingVoiceId === voice.id;
                const origin = voice.traits.language
                  ? `${languageName(voice.traits.language)} origin`
                  : null;
                const catalogSource = voice.name.match(/^\[([^\]]+)\]/)?.[1];
                const traits = [catalogSource, origin, voice.traits.accent, voice.traits.gender, voice.traits.age].filter(Boolean);
                const covered = coverage(voice, configuredLanguages);
                const previewLanguage = previewLanguageFor(voice);
                return (
                  <div
                    className={`voice-option-card ${selected ? "selected" : ""}`}
                    key={voice.id}
                    onClick={() => setPendingVoiceId(voice.id)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        setPendingVoiceId(voice.id);
                      }
                    }}
                    role="button"
                    tabIndex={0}
                  >
                    <span className="voice-option-radio">{selected && <Check size={13} />}</span>
                    <span className="voice-option-avatar">{displayVoiceName(voice.name).slice(0, 1).toUpperCase()}</span>
                    <span className="voice-option-copy">
                      <strong>{displayVoiceName(voice.name)}<em className={`provider-${voice.provider}`}>{providerName(voice.provider)}</em>{languageFilter === "recommended" && index < 3 && <em>Recommended</em>}</strong>
                      <small>{voice.description || voice.category}</small>
                      <span>
                        {languageFilter === "recommended" && <i>{covered}/{configuredLanguages.length} languages</i>}
                        {traits.slice(0, 3).map((trait) => <i key={trait}>{trait}</i>)}
                      </span>
                    </span>
                    <button
                      className={`voice-preview-button ${playingId === voice.id ? "playing" : ""} ${!previewLanguage ? "disabled" : ""}`}
                      disabled={!previewLanguage}
                      type="button"
                      aria-label={`Preview ${voice.name} in ${previewLanguage ? languageName(previewLanguage) : "the selected language"}`}
                      title={previewLanguage ? `Listen in ${languageName(previewLanguage)}` : "No compatible preview language"}
                      onClick={(event) => { event.stopPropagation(); void preview(voice); }}
                    >
                      {bufferingId === voice.id
                        ? <LoaderCircle className="spin" size={15} />
                        : playingId === voice.id ? <Pause size={15} /> : <Play size={15} />}
                      <span>{bufferingId === voice.id ? "Loading" : playingId === voice.id ? "Pause" : "Listen"}</span>
                    </button>
                  </div>
                );
              })}</div>
                {displayedVoices.length < visibleLanguageVoices.length && (
                  <button className="voice-results-more" type="button" onClick={() => setVisibleLimit((current) => current + 40)}>
                    Show 40 more <span>{visibleLanguageVoices.length - displayedVoices.length} remaining</span>
                  </button>
                )}
              </section>}
            </div>

            <footer>
              <p>Compatibility comes from the speech model. Listen in the selected language before choosing.</p>
              <div><button type="button" onClick={close}>Cancel</button><button className="save" disabled={!pendingVoiceId} type="button" onClick={save}>Use voice</button></div>
            </footer>
          </section>
        </div>
      )}
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

function providerName(provider: string) {
  return provider === "telnyx" ? "Sauti Voice" : titleCase(provider);
}

function categoryRank(category: string) {
  return category === "professional" ? 0 : category === "generated" ? 1 : category === "native" ? 2 : 3;
}
