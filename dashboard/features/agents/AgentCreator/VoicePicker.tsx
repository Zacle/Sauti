"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, CircleAlert, LoaderCircle, Mic2, Pause, Play, Search, X } from "lucide-react";
import { listVoices } from "@/lib/api/voices";
import type { VoiceOption } from "@/types/api";

const SUPPORTED_VOICE_LANGUAGES = ["sw", "en", "fr", "ar"];

type VoicePickerProps = {
  value: string;
  primaryLanguage: string;
  supportedLanguages: string[];
  onChange: (voiceId: string) => void;
};

export function VoicePicker({ value, primaryLanguage, supportedLanguages, onChange }: VoicePickerProps) {
  const [open, setOpen] = useState(false);
  const [voices, setVoices] = useState<VoiceOption[]>([]);
  const [providerEnabled, setProviderEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [pendingVoiceId, setPendingVoiceId] = useState(value);
  const [playingId, setPlayingId] = useState("");
  const [bufferingId, setBufferingId] = useState("");
  const [previewError, setPreviewError] = useState("");
  const [languageFilter, setLanguageFilter] = useState("recommended");
  const [accentFilter, setAccentFilter] = useState("all");
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    listVoices()
      .then((catalog) => {
        setVoices(catalog.voices);
        setProviderEnabled(catalog.enabledProviders.length > 0);
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load voices."))
      .finally(() => setLoading(false));
    return () => audioRef.current?.pause();
  }, []);

  useEffect(() => {
    if (open) setPendingVoiceId(value);
  }, [open, value]);

  const selectedVoice = voices.find((voice) => voice.id === value);
  const filteredVoices = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return voices.filter((voice) => {
      const matchesAccent = accentFilter === "all"
        || voice.traits.accent?.toLowerCase() === accentFilter;
      const matchesQuery = !normalizedQuery
        || `${voice.name} ${voice.description ?? ""} ${Object.values(voice.traits).join(" ")}`
          .toLowerCase()
          .includes(normalizedQuery);
      return matchesAccent && matchesQuery;
    });
  }, [accentFilter, query, voices]);
  const configuredLanguages = useMemo(
    () => Array.from(new Set([primaryLanguage, ...supportedLanguages])),
    [primaryLanguage, supportedLanguages],
  );
  const visibleLanguages = useMemo(
    () => Array.from(new Set([...SUPPORTED_VOICE_LANGUAGES, ...configuredLanguages])),
    [configuredLanguages],
  );
  const accents = useMemo(
    () => Array.from(new Set(
      voices
        .map((voice) => voice.traits.accent?.trim().toLowerCase())
        .filter((accent): accent is string => Boolean(accent)),
    )).sort(),
    [voices],
  );
  const voiceCoverage = useMemo(
    () => Object.fromEntries(visibleLanguages.map((language) => [
      language,
      voices.filter((voice) => voice.languages.includes(language)).length,
    ])) as Record<string, number>,
    [visibleLanguages, voices],
  );
  const recommendedVoices = useMemo(
    () => [...filteredVoices]
      .sort((left, right) => coverage(right, configuredLanguages) - coverage(left, configuredLanguages))
      .filter((voice) => coverage(voice, configuredLanguages) > 0),
    [configuredLanguages, filteredVoices],
  );
  const visibleLanguageVoices = languageFilter === "recommended"
    ? recommendedVoices
    : filteredVoices.filter((voice) => voice.languages.includes(languageFilter));
  const unsupportedLanguage = languageFilter !== "recommended" && voiceCoverage[languageFilter] === 0;

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
    } catch {
      setBufferingId("");
      setPreviewError(`Your browser blocked the preview for ${voice.name}. Try again.`);
    }
  }

  function close() {
    audioRef.current?.pause();
    setPlayingId("");
    setBufferingId("");
    setPreviewError("");
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
          <small>{selectedVoice ? `${selectedVoice.languages.length > 1 ? "Multilingual" : languageName(selectedVoice.languages[0] ?? primaryLanguage)} · ${selectedVoice.traits.description ?? selectedVoice.traits.accent ?? selectedVoice.category}` : "Preview and select a voice for the caller's language"}</small>
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
              <label className="voice-search"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search voices, accents, or styles..." /></label>
              <label className="voice-accent-filter">
                <span>Accent</span>
                <select value={accentFilter} onChange={(event) => setAccentFilter(event.target.value)}>
                  <option value="all">All accents</option>
                  {accents.map((accent) => <option key={accent} value={accent}>{titleCase(accent)}</option>)}
                </select>
                <ChevronDown aria-hidden="true" size={15} />
              </label>
            </div>

            <div className="voice-language-tabs" aria-label="Voice language">
              <button className={languageFilter === "recommended" ? "active" : ""} onClick={() => setLanguageFilter("recommended")} type="button">
                Best match
              </button>
              {visibleLanguages.map((language) => (
                <button
                  className={`${languageFilter === language ? "active" : ""} ${voiceCoverage[language] === 0 ? "unsupported" : ""}`}
                  key={language}
                  onClick={() => setLanguageFilter(language)}
                  type="button"
                >
                  {languageName(language)}
                  <span>{voiceCoverage[language] || "—"}</span>
                </button>
              ))}
            </div>

            <div className="voice-picker-results">
              {previewError && <div className="voice-preview-error">{previewError}</div>}
              {loading && <div className="voice-picker-state"><LoaderCircle className="spin" size={22} /> Loading available voices...</div>}
              {!loading && error && <div className="voice-picker-state error">{error}<small>Check the configured TTS provider credentials.</small></div>}
              {!loading && !error && !providerEnabled && <div className="voice-picker-state">No voice provider is enabled.<small>Configure ElevenLabs or Azure Speech in the backend environment.</small></div>}
              {!loading && !error && providerEnabled && unsupportedLanguage && (
                <div className="voice-language-unavailable">
                  <CircleAlert aria-hidden="true" size={20} />
                  <div>
                    <strong>No compatible {languageName(languageFilter)} voice is available</strong>
                    <p>{unsupportedLanguageMessage(languageFilter)}</p>
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
                <div>{visibleLanguageVoices.map((voice, index) => {
                const selected = pendingVoiceId === voice.id;
                const origin = voice.traits.language
                  ? `${languageName(voice.traits.language)} origin`
                  : null;
                const traits = [origin, voice.traits.accent, voice.traits.gender, voice.traits.age].filter(Boolean);
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
                    <span className="voice-option-avatar">{voice.name.slice(0, 1).toUpperCase()}</span>
                    <span className="voice-option-copy">
                      <strong>{voice.name}{languageFilter === "recommended" && index < 3 && <em>Recommended</em>}</strong>
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
              })}</div></section>}
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
  return ({ sw: "Swahili", fr: "French", en: "English", ar: "Arabic", multilingual: "Multilingual" } as Record<string, string>)[code] ?? code.toUpperCase();
}

function titleCase(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (character) => character.toUpperCase());
}

function unsupportedLanguageMessage(language: string) {
  if (language === "sw") {
    return "Configure Azure Speech for native Kenyan or Tanzanian Swahili voices, or enable ElevenLabs v3 for Swahili previews.";
  }
  if (language === "fr" || language === "ar") {
    return `English-origin voices are hidden because their ${languageName(language)} accent is not production quality. Use native Azure voices or ElevenLabs multilingual/v3 voices for better delivery.`;
  }
  return "The current speech model returned no compatible voice for this language.";
}
