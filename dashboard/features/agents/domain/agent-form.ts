/**
 * The agent editor's settings form: one object, changed only through {@link agentFormReducer}.
 *
 * Field values are kept as the controls show them (call length in minutes, allowed origins as
 * lines of text); {@link formFromSettings} and {@link settingsFromForm} convert to and from what
 * the API stores, so a load followed by a save sends back exactly what was loaded.
 */
import type { Agent, AgentDraft } from "@/types/api";

export type AfterHoursBehavior = AgentDraft["afterHoursBehavior"];

export type AgentForm = {
  name: string;
  description: string;
  language: string;
  supportedLanguages: string[];
  voice: string;
  timezone: string;
  greeting: string;
  prompt: string;
  bookingEnabled: boolean;
  defaultBookingDurationMinutes: number;
  /** Whole minutes, as typed. */
  maxDuration: string;
  saveTranscript: boolean;
  recordCalls: boolean;
  transferEnabled: boolean;
  transferNumber: string;
  /** Comma-separated, as typed. */
  escalationPhrases: string;
  operatingHours: string;
  afterHoursBehavior: AfterHoursBehavior;
  afterHoursMessage: string;
  knowledgeBase: string;
  llmTier: AgentDraft["llmTier"];
  bargeInSensitivity: number;
  bargeInGraceMs: number;
  endCallOnSilenceSeconds: number;
  reminderAfterSilenceSeconds: number;
  maxReminders: number;
  detectVoicemail: boolean;
  handleCallScreening: boolean;
  dtmfEnabled: boolean;
  dtmfTerminationKey: AgentDraft["dtmfTerminationKey"];
  dtmfInputTimeoutSeconds: number;
  dtmfMaxDigits: number;
  dtmfDigitMappings: Record<string, string>;
  webVoiceEnabled: boolean;
  /** One origin per line, as typed. */
  webVoiceAllowedOrigins: string;
  webVoiceRequireConsent: boolean;
  whatsappEnabled: boolean;
  whatsappPhoneNumberId: string;
  sttEndpointingMs: number;
  /** "general" means no special vocabulary. */
  sttVocabularyDomain: string;
  sttBoostedKeywords: string;
  safetyGuardrails: string[];
  postCallExtractionFields: string[];
  bookingRequiredFields: string[];
  bookingNotificationChannels: string[];
  bookingNotificationRecipient: string;
};

export const DEFAULT_BOOKING_FIELDS = ["caller_name", "caller_phone", "service_type", "appointment_at"];
export const DEFAULT_BOOKING_NOTIFICATIONS = ["dashboard", "email"];

/** A new, blank agent before a template or workspace defaults are applied. */
export const initialAgentForm: AgentForm = {
  name: "Amina",
  description: "Start without predefined behavior.",
  language: "en",
  supportedLanguages: ["en"],
  voice: "",
  timezone: "Africa/Nairobi",
  greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, and ask one simple question about what the caller needs.",
  prompt: "",
  bookingEnabled: false,
  defaultBookingDurationMinutes: 60,
  maxDuration: "5",
  saveTranscript: true,
  recordCalls: false,
  transferEnabled: false,
  transferNumber: "",
  escalationPhrases: "speak to a person, talk to a human, human agent",
  operatingHours: "always",
  afterHoursBehavior: "answer",
  afterHoursMessage: "",
  knowledgeBase: "",
  llmTier: "standard",
  bargeInSensitivity: 0.7,
  bargeInGraceMs: 300,
  endCallOnSilenceSeconds: 60,
  reminderAfterSilenceSeconds: 30,
  maxReminders: 1,
  detectVoicemail: true,
  handleCallScreening: true,
  dtmfEnabled: false,
  dtmfTerminationKey: "#",
  dtmfInputTimeoutSeconds: 5,
  dtmfMaxDigits: 8,
  dtmfDigitMappings: {},
  webVoiceEnabled: false,
  webVoiceAllowedOrigins: "",
  webVoiceRequireConsent: true,
  whatsappEnabled: false,
  whatsappPhoneNumberId: "",
  sttEndpointingMs: 300,
  sttVocabularyDomain: "general",
  sttBoostedKeywords: "",
  safetyGuardrails: [],
  postCallExtractionFields: ["summary", "successful", "sentiment", "intent"],
  bookingRequiredFields: DEFAULT_BOOKING_FIELDS,
  bookingNotificationChannels: DEFAULT_BOOKING_NOTIFICATIONS,
  bookingNotificationRecipient: "",
};

/** The form for saved settings: a published agent, or a draft laid over it. */
export function formFromSettings(agent: Agent | AgentDraft): AgentForm {
  return {
    name: agent.name,
    description: agent.description,
    language: agent.defaultLanguage,
    supportedLanguages: agent.supportedLanguages,
    voice: agent.ttsVoiceId ?? "",
    timezone: agent.timezone,
    greeting: agent.greetingMessage,
    prompt: agent.systemPrompt,
    bookingEnabled: agent.bookingEnabled,
    defaultBookingDurationMinutes: agent.defaultBookingDurationMinutes,
    maxDuration: String(Math.max(1, Math.round(agent.maxCallDurationSeconds / 60))),
    saveTranscript: agent.saveTranscript,
    recordCalls: agent.recordCalls,
    transferEnabled: Boolean(agent.humanTransferNumber),
    transferNumber: agent.humanTransferNumber ?? "",
    escalationPhrases: agent.escalationPhrases.join(", "),
    operatingHours: agent.operatingHours ?? "always",
    afterHoursBehavior: agent.afterHoursBehavior ?? "answer",
    afterHoursMessage: agent.afterHoursMessage ?? "",
    knowledgeBase: agent.knowledgeBase ?? "",
    llmTier: agent.llmTier,
    bargeInSensitivity: agent.bargeInSensitivity,
    bargeInGraceMs: agent.bargeInGraceMs,
    endCallOnSilenceSeconds: agent.endCallOnSilenceSeconds,
    reminderAfterSilenceSeconds: agent.reminderAfterSilenceSeconds,
    maxReminders: agent.maxReminders,
    detectVoicemail: agent.detectVoicemail,
    handleCallScreening: agent.handleCallScreening,
    dtmfEnabled: agent.dtmfEnabled,
    dtmfTerminationKey: agent.dtmfTerminationKey,
    dtmfInputTimeoutSeconds: agent.dtmfInputTimeoutSeconds,
    dtmfMaxDigits: agent.dtmfMaxDigits,
    dtmfDigitMappings: agent.dtmfDigitMappings,
    webVoiceEnabled: agent.webVoiceEnabled,
    webVoiceAllowedOrigins: agent.webVoiceAllowedOrigins.join("\n"),
    webVoiceRequireConsent: agent.webVoiceRequireConsent,
    whatsappEnabled: agent.whatsappEnabled,
    whatsappPhoneNumberId: agent.whatsappPhoneNumberId ?? "",
    sttEndpointingMs: agent.sttEndpointingMs,
    sttVocabularyDomain: agent.sttVocabularyDomain ?? "general",
    sttBoostedKeywords: agent.sttBoostedKeywords ?? "",
    safetyGuardrails: agent.safetyGuardrails,
    postCallExtractionFields: agent.postCallExtractionFields,
    bookingRequiredFields: agent.bookingRequiredFields,
    bookingNotificationChannels: agent.bookingNotificationChannels,
    bookingNotificationRecipient: agent.bookingNotificationRecipient ?? "",
  };
}

/** Every agent setting as the API expects it. */
export function settingsFromForm(form: AgentForm): AgentDraft {
  return {
    name: form.name,
    description: form.description,
    greetingMessage: form.greeting,
    systemPrompt: form.prompt,
    defaultLanguage: form.language,
    supportedLanguages: Array.from(new Set([form.language, ...form.supportedLanguages])),
    ttsVoiceId: form.voice,
    humanTransferNumber: form.transferEnabled && form.transferNumber.trim() ? form.transferNumber.trim() : null,
    escalationPhrases: form.escalationPhrases.split(",").map((phrase) => phrase.trim()).filter(Boolean),
    bookingEnabled: form.bookingEnabled,
    defaultBookingDurationMinutes: form.defaultBookingDurationMinutes,
    timezone: form.timezone,
    knowledgeBase: form.knowledgeBase,
    operatingHours: form.operatingHours,
    afterHoursBehavior: form.afterHoursBehavior,
    afterHoursMessage: form.afterHoursMessage.trim() || null,
    maxCallDurationSeconds: Math.max(60, Number(form.maxDuration || 5) * 60),
    saveTranscript: form.saveTranscript,
    recordCalls: form.recordCalls,
    llmTier: form.llmTier,
    bargeInSensitivity: form.bargeInSensitivity,
    bargeInGraceMs: form.bargeInGraceMs,
    endCallOnSilenceSeconds: form.endCallOnSilenceSeconds,
    reminderAfterSilenceSeconds: form.reminderAfterSilenceSeconds,
    maxReminders: form.maxReminders,
    detectVoicemail: form.detectVoicemail,
    handleCallScreening: form.handleCallScreening,
    dtmfEnabled: form.dtmfEnabled,
    dtmfTerminationKey: form.dtmfTerminationKey,
    dtmfInputTimeoutSeconds: form.dtmfInputTimeoutSeconds,
    dtmfMaxDigits: form.dtmfMaxDigits,
    dtmfDigitMappings: form.dtmfDigitMappings,
    webVoiceEnabled: form.webVoiceEnabled,
    webVoiceAllowedOrigins: form.webVoiceAllowedOrigins.split(/\r?\n|,/).map((origin) => origin.trim()).filter(Boolean),
    webVoiceRequireConsent: form.webVoiceRequireConsent,
    whatsappEnabled: form.whatsappEnabled,
    whatsappPhoneNumberId: form.whatsappEnabled && form.whatsappPhoneNumberId.trim() ? form.whatsappPhoneNumberId.trim() : null,
    sttEndpointingMs: form.sttEndpointingMs,
    sttVocabularyDomain: form.sttVocabularyDomain === "general" ? null : form.sttVocabularyDomain,
    sttBoostedKeywords: form.sttBoostedKeywords.trim() || null,
    safetyGuardrails: form.safetyGuardrails,
    postCallExtractionFields: form.postCallExtractionFields,
    bookingRequiredFields: form.bookingRequiredFields,
    bookingNotificationChannels: form.bookingNotificationChannels,
    bookingNotificationRecipient: form.bookingNotificationRecipient.trim() || null,
  };
}

type Update<T> = T | ((current: T) => T);

export type AgentFormAction =
  | { [K in keyof AgentForm]: { type: "set"; field: K; value: Update<AgentForm[K]> } }[keyof AgentForm]
  | { type: "patch"; values: Partial<AgentForm> }
  | { type: "load"; settings: Agent | AgentDraft };

export function agentFormReducer(form: AgentForm, action: AgentFormAction): AgentForm {
  switch (action.type) {
    case "set": {
      const current = form[action.field];
      const next = typeof action.value === "function"
        ? (action.value as (value: typeof current) => typeof current)(current)
        : action.value;
      return Object.is(next, current) ? form : { ...form, [action.field]: next };
    }
    case "patch":
      return { ...form, ...action.values };
    case "load":
      return formFromSettings(action.settings);
  }
}

/** `setName`, `setPrompt`, … for each field, shaped like React state setters so controls need no change. */
export type AgentFormSetters = {
  [K in keyof AgentForm as `set${Capitalize<K & string>}`]: (value: Update<AgentForm[K]>) => void;
};

export function agentFormSetters(dispatch: (action: AgentFormAction) => void): AgentFormSetters {
  const setters: Record<string, (value: unknown) => void> = {};
  for (const field of Object.keys(initialAgentForm) as (keyof AgentForm)[]) {
    const name = `set${field.charAt(0).toUpperCase()}${field.slice(1)}`;
    setters[name] = (value) => dispatch({ type: "set", field, value } as AgentFormAction);
  }
  return setters as AgentFormSetters;
}
