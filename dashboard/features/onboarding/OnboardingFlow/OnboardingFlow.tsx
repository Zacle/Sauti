"use client";

import "./OnboardingFlow.css";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import type { LucideIcon } from "lucide-react";
import {
  AudioLines,
  ArrowRight,
  Bot,
  Building2,
  CalendarCheck,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  GraduationCap,
  Globe2,
  Landmark,
  LoaderCircle,
  PhoneCall,
  Pause,
  Play,
  Rocket,
  Scissors,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  Workflow,
} from "lucide-react";
import { completeOnboarding } from "@/lib/api/onboarding";
import { authorizeGoogleCalendar } from "@/lib/api/integrations";
import { listVoices } from "@/lib/api/voices";
import { formatTimezone, TIMEZONE_GROUPS } from "@/lib/timezones";
import { useAuth } from "@/hooks/useAuth";
import type { VoiceOption } from "@/types/api";

const businessTypes: Array<[string, string, LucideIcon]> = [
  ["Clinics & healthcare", "Appointments, patient questions, urgent escalations", Stethoscope],
  ["Salons & beauty", "Bookings, reschedules, service questions", Scissors],
  ["Real estate", "Buyer qualification, property interest, viewings", Building2],
  ["Professional services", "Consultations, intake details, call routing", Landmark],
  ["Education", "Admissions questions, callbacks, department routing", GraduationCap],
  ["Local services", "After-hours calls, new leads, appointment requests", Rocket],
];

const calendarOptions: Array<[string, string, LucideIcon]> = [
  ["Google Calendar", "After creating the draft, Sauti will redirect you to Google to authorize calendar access.", CalendarCheck],
  ["Set up later", "Keep bookings in draft mode until a calendar is connected from the agent studio.", Globe2],
];

const languageOptions = [
  ["sw", "Swahili"],
  ["en", "English"],
  ["fr", "French"],
  ["ar", "Arabic"],
] as const;

type SupportedLanguage = typeof languageOptions[number][0];

const stepCopy = [
  {
    eyebrow: "Agent identity",
    title: "Name your first Sauti agent.",
    description: "Choose the name, language behavior, and voice profile before Sauti shows a generated agent preview.",
  },
  {
    eyebrow: "Business profile",
    title: "Tell Sauti what kind of calls to handle.",
    description: "We will use this to draft booking rules, tools, and test scenarios. You can edit everything later.",
  },
  {
    eyebrow: "Booking setup",
    title: "Choose the booking path for this draft.",
    description: "Onboarding creates the agent draft first. If you choose Google Calendar, Sauti redirects you to Google immediately after setup.",
  },
  {
    eyebrow: "Review setup",
    title: "Your first agent is ready to generate.",
    description: "Review the configuration below. Finishing setup creates the draft without activating a live phone number.",
  },
] as const;

const actionCopy = [
  "Next: tell Sauti what calls to handle.",
  "Next: choose the booking setup path.",
  "Next: review the generated setup.",
  "Finish: create the draft agent in your workspace.",
];

export function OnboardingFlow() {
  const router = useRouter();
  const { session } = useAuth();
  const [step, setStep] = useState(1);
  const [direction, setDirection] = useState<"forward" | "backward">("forward");
  const [businessType, setBusinessType] = useState("Clinics & healthcare");
  const [useCase, setUseCase] = useState("Appointment booking");
  const [businessWebsite, setBusinessWebsite] = useState("");
  const [selectedServices, setSelectedServices] = useState(["Consultation", "Follow-up visit", "Product demo", "Callback request"]);
  const [timezone, setTimezone] = useState("Africa/Nairobi");
  const [calendar, setCalendar] = useState("Set up later");
  const [routing, setRouting] = useState("Fixed calendar");
  const [agentName, setAgentName] = useState("");
  const [language, setLanguage] = useState<SupportedLanguage>("sw");
  const [autoDetectLanguages, setAutoDetectLanguages] = useState(true);
  const [voiceId, setVoiceId] = useState("");
  const [voices, setVoices] = useState<VoiceOption[]>([]);
  const [playingVoiceId, setPlayingVoiceId] = useState("");
  const [bufferingVoiceId, setBufferingVoiceId] = useState("");
  const [voicePreviewError, setVoicePreviewError] = useState("");
  const [finishing, setFinishing] = useState(false);
  const [finishError, setFinishError] = useState("");
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const useCases = ["Appointment booking", "Customer support", "Lead qualification", "Call routing", "Reminders"];
  const services = ["Consultation", "Follow-up visit", "Product demo", "Callback request"];
  const currentCopy = stepCopy[step - 1];
  const selectedVoice = voices.find((item) => item.id === voiceId);
  const languageVoices = voices.filter((item) => item.languages.includes(language));
  const voiceName = selectedVoice?.name ?? "Provider default";
  const languageName = languageOptions.find(([code]) => code === language)?.[1] ?? language;
  const trimmedAgentName = agentName.trim();
  const greetingDirection = greetingDirectionFor(useCase, trimmedAgentName);
  const voicePreviewText = voicePreviewTextFor(language);
  const agentPreviewTitle = trimmedAgentName ? `${trimmedAgentName}, ${useCase.toLowerCase()} agent` : `Your ${useCase.toLowerCase()} agent`;
  const agentAvatarLabel = trimmedAgentName ? trimmedAgentName.slice(0, 1).toUpperCase() : "AI";
  const canContinue = step !== 1 || trimmedAgentName.length > 0;

  useEffect(() => {
    listVoices()
      .then((catalog) => setVoices(catalog.voices))
      .catch(() => setVoices([]));

    return () => audioRef.current?.pause();
  }, []);

  useEffect(() => {
    audioRef.current?.pause();
    setPlayingVoiceId("");
    setBufferingVoiceId("");
    setVoicePreviewError("");
  }, [language, voiceId]);

  useEffect(() => {
    if (voiceId && !selectedVoice?.languages.includes(language)) {
      setVoiceId("");
    }
  }, [language, selectedVoice, voiceId]);

  useEffect(() => {
    setRouting(calendar === "Google Calendar" ? "Fixed calendar" : "Set up later");
  }, [calendar]);

  function toggleService(service: string) {
    setSelectedServices((current) =>
      current.includes(service) ? current.filter((item) => item !== service) : [...current, service],
    );
  }

  function changeStep(nextStep: number) {
    setDirection(nextStep >= step ? "forward" : "backward");
    setStep(Math.min(4, Math.max(1, nextStep)));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function finishSetup() {
    setFinishing(true);
    setFinishError("");
    try {
      if (!session) {
        router.replace("/login");
        return;
      }
      if (!selectedServices.length) {
        throw new Error("Select at least one service before finishing setup.");
      }
      if (!trimmedAgentName) {
        throw new Error("Choose an agent name before finishing setup.");
      }
      const supportedLanguages: SupportedLanguage[] = autoDetectLanguages
        ? languageOptions.map(([code]) => code)
        : [language];
      const agent = await completeOnboarding({
        businessType,
        primaryUseCase: useCase,
        businessWebsite,
        bookableServices: selectedServices,
        timezone,
        calendarProvider: calendar,
        routingPolicy: routing,
        agentName: trimmedAgentName,
        defaultLanguage: language,
        supportedLanguages,
        ttsVoiceId: voiceId || null,
        voiceProfile: voiceName,
      });
      if (calendar === "Google Calendar") {
        const { authorizationUrl } = await authorizeGoogleCalendar(agent.id);
        window.location.assign(authorizationUrl);
        return;
      }
      router.replace(`/agents/${agent.id}`);
    } catch (caught) {
      setFinishError(caught instanceof Error ? caught.message : "Unable to create the draft agent.");
      setFinishing(false);
    }
  }

  async function previewSelectedVoice() {
    if (!selectedVoice) return;
    const previewLanguage = previewLanguageFor(selectedVoice, language);
    if (!previewLanguage) {
      setVoicePreviewError(`${selectedVoice.name} is not available for ${languageName}.`);
      return;
    }
    setVoicePreviewError("");
    if (playingVoiceId === selectedVoice.id) {
      audioRef.current?.pause();
      setPlayingVoiceId("");
      setBufferingVoiceId("");
      return;
    }
    audioRef.current?.pause();
    const audio = new Audio(
      `/api/v1/voices/${encodeURIComponent(selectedVoice.id)}/preview?language=${encodeURIComponent(previewLanguage)}&text=${encodeURIComponent(voicePreviewText)}`,
    );
    audio.preload = "auto";
    audioRef.current = audio;
    setBufferingVoiceId(selectedVoice.id);
    audio.addEventListener("playing", () => {
      setBufferingVoiceId("");
      setPlayingVoiceId(selectedVoice.id);
    });
    audio.addEventListener("waiting", () => setBufferingVoiceId(selectedVoice.id));
    audio.addEventListener("ended", () => {
      setPlayingVoiceId("");
      setBufferingVoiceId("");
    });
    audio.addEventListener("error", () => {
      setPlayingVoiceId("");
      setBufferingVoiceId("");
      setVoicePreviewError(`The preview for ${selectedVoice.name} could not be played.`);
    });
    try {
      await audio.play();
    } catch {
      setBufferingVoiceId("");
      setVoicePreviewError(`Your browser blocked the preview for ${selectedVoice.name}. Try again.`);
    }
  }

  return (
    <main className="onboarding-clean-page">
      <header className="onboarding-topbar">
        <Link className="onboarding-brand" href="/">
          <span className="brand-mark">S</span>
          <strong>Sauti</strong>
        </Link>
        <div className="onboarding-step">
          <span>Step {step} of 4</span>
          <div className="onboarding-step-segments" role="progressbar" aria-label="Onboarding progress" aria-valuemin={1} aria-valuemax={4} aria-valuenow={step}>
            {[1, 2, 3, 4].map((item) => <i className={item <= step ? "active" : ""} key={item} />)}
          </div>
        </div>
        <Link className="onboarding-skip" href="/dashboard">Skip setup <ChevronRight size={17} /></Link>
      </header>

      <section className="onboarding-clean-shell">
        <section className="onboarding-panel">
          {step > 1 && (
            <button className="onboarding-back" type="button" onClick={() => changeStep(step - 1)}>
              <ChevronLeft size={18} /> Back
            </button>
          )}

          <div className={`onboarding-heading step-motion step-${direction}`} key={`heading-${step}`}>
            <span>{currentCopy.eyebrow}</span>
            <h1>{step === 2 ? <>Tell <em>Sauti</em> what kind of calls to handle.</> : currentCopy.title}</h1>
            <p>{currentCopy.description}</p>
          </div>

          <div className={`onboarding-step-content step-${direction}`} key={step} aria-live="polite">
            {step === 1 && (
              <div className="onboarding-section two-column">
                <label className="onboarding-field-wide">
                  Agent name
                  <input value={agentName} onChange={(event) => setAgentName(event.target.value)} placeholder="Choose a name for your agent" />
                </label>
                <label>
                  Primary language
                  <select value={language} onChange={(event) => setLanguage(event.target.value as SupportedLanguage)}>
                    {languageOptions.map(([code, label]) => (
                      <option value={code} key={code}>{label}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Voice
                  <span className="onboarding-voice-control">
                    <select value={voiceId} onChange={(event) => setVoiceId(event.target.value)}>
                      <option value="">Provider default</option>
                      {languageVoices.map((item) => (
                        <option value={item.id} key={item.id}>{item.name}</option>
                      ))}
                    </select>
                    <button
                      className={playingVoiceId === voiceId ? "playing" : ""}
                      disabled={!selectedVoice || !previewLanguageFor(selectedVoice, language)}
                      type="button"
                      onClick={() => void previewSelectedVoice()}
                      aria-label={selectedVoice ? `Preview ${selectedVoice.name}` : "Choose a voice to preview"}
                      title={selectedVoice ? "Listen to this voice" : "Choose a voice to preview"}
                    >
                      {bufferingVoiceId === voiceId
                        ? <LoaderCircle className="spin" size={17} />
                        : playingVoiceId === voiceId ? <Pause size={17} /> : <Play size={17} />}
                      <span>{bufferingVoiceId === voiceId ? "Loading" : playingVoiceId === voiceId ? "Pause" : "Listen"}</span>
                    </button>
                  </span>
                  {voicePreviewError && <small className="onboarding-voice-error">{voicePreviewError}</small>}
                </label>
                <label className="onboarding-field-wide onboarding-detect-field">
                  <input
                    checked={autoDetectLanguages}
                    onChange={(event) => setAutoDetectLanguages(event.target.checked)}
                    type="checkbox"
                  />
                  <span>
                    Detect supported caller languages automatically
                    <small>Supports Swahili, English, French, and Arabic. The primary language is used as fallback.</small>
                  </span>
                </label>
              </div>
            )}

            {step === 2 && (
              <>
                <ChoiceSection title="Business type" note="Choose one">
                  <div className="business-choice-grid">
                    {businessTypes.map(([title, detail, Icon]) => (
                      <button
                        className={businessType === title ? "selected" : ""}
                        type="button"
                        key={title}
                        onClick={() => setBusinessType(title)}
                      >
                        <span className="choice-icon"><Icon size={21} /></span>
                        <strong>{title}</strong>
                        <small>{detail}</small>
                        {businessType === title && <span className="choice-check"><Check size={14} /></span>}
                      </button>
                    ))}
                  </div>
                </ChoiceSection>

                <ChoiceSection title="Primary use case" note="Multiple allowed later">
                  <div className="onboarding-pills">
                    {useCases.map((item) => (
                      <button
                        className={useCase === item ? "selected" : ""}
                        type="button"
                        key={item}
                        onClick={() => setUseCase(item)}
                      >
                        {useCase === item && <Check size={14} />}
                        {item}
                      </button>
                    ))}
                  </div>
                </ChoiceSection>

                <div className="onboarding-section two-column">
                  <label>
                    Business website
                    <span className="onboarding-input-shell">
                      <Globe2 size={17} />
                      <input
                        inputMode="url"
                        placeholder="https://yourcompany.com"
                        value={businessWebsite}
                        onChange={(event) => setBusinessWebsite(event.target.value)}
                      />
                    </span>
                  </label>
                  <label>
                    Default timezone
                    <span className="onboarding-input-shell">
                      <Clock3 size={17} />
                      <select value={timezone} onChange={(event) => setTimezone(event.target.value)}>
                        {TIMEZONE_GROUPS.map((group) => (
                          <optgroup label={group.label} key={group.label}>
                            {group.zones.map((zone) => (
                              <option value={zone.value} key={zone.value}>{zone.label}</option>
                            ))}
                          </optgroup>
                        ))}
                      </select>
                    </span>
                  </label>
                </div>

                <ChoiceSection title="Bookable services" note="Select all that apply">
                  <div className="service-chip-grid">
                    {services.map((item) => {
                      const selected = selectedServices.includes(item);
                      return (
                        <button
                          className={selected ? "selected" : ""}
                          type="button"
                          key={item}
                          onClick={() => toggleService(item)}
                        >
                          {selected && <Check size={15} />}
                          {item}
                        </button>
                      );
                    })}
                  </div>
                </ChoiceSection>
              </>
            )}

            {step === 3 && (
              <>
                <ChoiceSection title="Booking calendar" note="Choose one">
                  <div className="business-choice-grid">
                    {calendarOptions.map(([title, detail, Icon]) => (
                      <button
                        className={calendar === title ? "selected" : ""}
                        type="button"
                        key={title}
                        onClick={() => setCalendar(title)}
                      >
                        <span className="choice-icon"><Icon size={21} /></span>
                        <strong>{title}</strong>
                        <small>{detail}</small>
                        {calendar === title && <span className="choice-check"><Check size={14} /></span>}
                      </button>
                    ))}
                  </div>
                </ChoiceSection>
                <div className="onboarding-calendar-note">
                  <Workflow size={18} />
                  <span>Custom webhooks are developer integrations, so they are configured from the studio after the agent exists.</span>
                </div>
              </>
            )}

            {step === 4 && (
              <>
                <div className="onboarding-review-grid">
                  <ReviewItem icon={Building2} label="Business" value={businessType} />
                  <ReviewItem icon={CalendarCheck} label="Use case" value={useCase} />
                  <ReviewItem icon={Clock3} label="Timezone" value={formatTimezone(timezone)} />
                  <ReviewItem icon={Sparkles} label="Services" value={selectedServices.length ? selectedServices.join(", ") : "None selected"} />
                  <ReviewItem icon={CalendarCheck} label="Calendar" value={calendar} />
                  <ReviewItem icon={Workflow} label="Setup status" value={calendar === "Google Calendar" ? "Redirect to Google after draft creation" : "Calendar setup skipped"} />
                  <ReviewItem icon={Bot} label="Agent" value={trimmedAgentName || "Not named yet"} />
                  <ReviewItem
                    icon={AudioLines}
                    label="Language and voice"
                    value={`${languageName}${autoDetectLanguages ? " + auto detect" : ""} / ${voiceName}`}
                  />
                </div>
                <div className="onboarding-ready-card">
                  <CheckCircle2 size={22} />
                  <div>
                    <strong>Ready to create the draft</strong>
                    <span>You can connect credentials and test calls from the dashboard before activation.</span>
                  </div>
                </div>
              </>
            )}
          </div>

          <div className="onboarding-actions">
            <span><Sparkles size={17} /> {finishError || actionCopy[step - 1]}</span>
            {step < 4 ? (
              <button className="app-primary-button" disabled={!canContinue} type="button" onClick={() => changeStep(step + 1)}>
                Continue <ArrowRight size={17} />
              </button>
            ) : (
              <button className="app-primary-button" disabled={finishing} type="button" onClick={() => void finishSetup()}>
                {finishing ? "Creating agent..." : "Finish setup"} <ArrowRight size={17} />
              </button>
            )}
          </div>
        </section>

        <aside className="onboarding-preview-panel">
          <div className={`setup-preview-card step-${direction}`} key={`preview-${step}`}>
            <div className="setup-preview-top">
              <span><Bot size={18} /> Draft agent</span>
              <b>{step === 4 ? "Configured" : "Ready"}</b>
            </div>
            <div className="setup-agent-title">
              <span>{agentAvatarLabel}</span>
              <h2>{agentPreviewTitle}</h2>
            </div>
            <p>Answers inbound calls, detects the caller&apos;s language, checks availability, confirms details, and books approved services.</p>
            <div className="setup-preview-stack">
              <div><PhoneCall size={17} /><span>Inbound phone calls</span></div>
              <div><CalendarCheck size={17} /><span>{calendar}</span></div>
              <div><Globe2 size={17} /><span>{languageName}{autoDetectLanguages ? " + auto detect" : ""}</span></div>
              <div><Clock3 size={17} /><span>{formatTimezone(timezone)}</span></div>
            </div>
            <div className="setup-transcript">
              <div className="setup-transcript-label"><AudioLines size={18} /><small>Opening direction</small></div>
              <p>{greetingDirection}</p>
            </div>
          </div>
          <div className="onboarding-preview-note">
            <ShieldCheck size={18} />
            <span>Sauti will create the draft prompt, reusable variables, and test-mode tools from this setup.</span>
          </div>
          <div className="onboarding-trust-badge">
            <span><ShieldCheck size={20} /></span>
            <div><strong>Enterprise grade</strong><small>Secure · Reliable · Trusted</small></div>
          </div>
        </aside>
      </section>
    </main>
  );
}

function ChoiceSection({ title, note, children }: { title: string; note: string; children: React.ReactNode }) {
  return (
    <div className="onboarding-section">
      <div className="onboarding-section-head">
        <h2>{title}</h2>
        <span>{note}</span>
      </div>
      {children}
    </div>
  );
}

function ReviewItem({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <article>
      <span className="review-item-icon"><Icon size={22} /></span>
      <div className="review-item-copy">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
    </article>
  );
}

function greetingDirectionFor(useCase: string, agentName: string) {
  const booking = useCase === "Appointment booking";
  const name = agentName || "Sauti";
  return booking
    ? `Open naturally in the caller's language, mention ${name} only if it sounds natural, and ask one simple question about what they want to book.`
    : `Open naturally in the caller's language, mention ${name} only if it sounds natural, and ask one simple question about what they need.`;
}
function previewLanguageFor(voice: VoiceOption, primaryLanguage: string) {
  return voice.languages.includes(primaryLanguage) ? primaryLanguage : null;
}

function voicePreviewTextFor(language: SupportedLanguage) {
  switch (language) {
    case "fr":
      return "Bonjour, voici un court aperçu de la voix Sauti.";
    case "ar":
      return "مرحبا، هذا مثال قصير على صوت ساوتي.";
    case "sw":
      return "Habari, hii ni sampuli fupi ya sauti ya Sauti.";
    default:
      return "Hi, this is a short Sauti voice preview.";
  }
}
