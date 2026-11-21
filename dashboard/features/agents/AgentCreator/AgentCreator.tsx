"use client";

import { Button } from "@/components/ui/button";
import { Tooltip } from "@/components/ui/primitives";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";
import { LoadFailure } from "@/features/errors/ConsoleErrors";
import { useAuth } from "@/hooks/useAuth";
import {
activateAgent,
createAgentVariable,
deleteAgent,
discardAgentDraft,
generateAgentFromBrief,
getAgent,
getAgentDraft,
getAgentReadiness,
listAgentTemplates,
listAgentVariables,
refreshAgentPhoneNumber,
saveAgentDraft,
setupAgent,
updateAgentSetup,
updateAgentTimezone,
updateAgentVariables
} from "@/lib/api/agents";
import { getBusinessProfile } from "@/lib/api/business-profile";
import { ApiError } from "@/lib/api/client";
import { listCountryCallingCodes, type CountryCallingCode } from "@/lib/api/phone-numbers";
import { loadWorkspaceCallDefaults, loadWorkspaceProfile } from "@/lib/api/tenant";
import { COUNTRIES } from "@/lib/countries";
import { cn } from "@/lib/utils";
import type { Agent, AgentDraft, AgentDraftView, AgentReadiness, AgentSetup, AgentVariableDefinition, CreateAgentVariable } from "@/types/api";
import {
ArrowLeft,
Bot,
Check,
CircleAlert,
FileText,
LoaderCircle,
PanelRightClose,
Play,
Settings2,
Sparkles,
Trash2,
WandSparkles
} from "lucide-react";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import "./AgentCreator.css";
import "./AgentStudio.css";
import "./AgentStudioLayout.css";
import { PersonalisationDrawer } from "./Personalisation";
import "./StudioOverlays.css";
import { TestCallPanel } from "./TestCallPanel";
import { VersionHistory } from "./VersionHistory";

import { CallBehaviorSettings, PostCallSettings } from "./CallSettings";
import { IntegrationsSettings } from "./IntegrationsSettings";
import { KnowledgeSettings } from "./KnowledgeSettings";
import { MainSettings } from "./MainSettings";
import { PhoneNumberPicker } from "./PhoneNumberPicker";
import { BehaviorSettings } from "./PromptSettings";
import { RoutingSettings } from "./RoutingSettings";
import { SpeechSettings } from "./SpeechSettings";
import { AfterHoursBehavior, BLANK_TEMPLATE, DEFAULT_BOOKING_FIELDS, DEFAULT_BOOKING_NOTIFICATIONS, Template, mapStoredTemplate, studioSections } from "./studio-model";
import { TemplateSelection } from "./TemplateSelection";

export function AgentCreator({
  agentId,
  openPersonalisation = false,
  openPhonePicker = false,
}: {
  agentId?: string;
  openPersonalisation?: boolean;
  openPhonePicker?: boolean;
} = {}) {
  const router = useRouter();
  const { session } = useAuth();
  const editing = Boolean(agentId);
  const [stage, setStage] = useState<"templates" | "studio">(editing ? "studio" : "templates");
  const [brief, setBrief] = useState("");
  const [availableTemplates, setAvailableTemplates] = useState<Template[]>([]);
  const [templatesStatus, setTemplatesStatus] = useState<"loading" | "ready" | "failed">("loading");
  const [templatesAttempt, setTemplatesAttempt] = useState(0);
  const [category, setCategory] = useState("All");
  const [selectedTemplate, setSelectedTemplate] = useState<Template>(BLANK_TEMPLATE);
  const [activeSection, setActiveSection] = useState<(typeof studioSections)[number]["id"]>("main");
  const [channelFocusRequest, setChannelFocusRequest] = useState(0);
  const [name, setName] = useState("Amina");
  const [description, setDescription] = useState(BLANK_TEMPLATE.description);
  const [language, setLanguage] = useState(BLANK_TEMPLATE.defaultLanguage);
  const [supportedLanguages, setSupportedLanguages] = useState(BLANK_TEMPLATE.supportedLanguages);
  const [voice, setVoice] = useState("");
  const [timezone, setTimezone] = useState("Africa/Nairobi");
  const [greeting, setGreeting] = useState(BLANK_TEMPLATE.greeting);
  const [prompt, setPrompt] = useState(BLANK_TEMPLATE.prompt);
  const [bookingEnabled, setBookingEnabled] = useState(false);
  const [defaultBookingDurationMinutes, setDefaultBookingDurationMinutes] = useState(60);
  const [workspaceBookingDurationMinutes, setWorkspaceBookingDurationMinutes] = useState(60);
  const [maxDuration, setMaxDuration] = useState("5");
  const [saveTranscript, setSaveTranscript] = useState(true);
  const [recordCalls, setRecordCalls] = useState(false);
  const [transferEnabled, setTransferEnabled] = useState(false);
  const [transferNumber, setTransferNumber] = useState("");
  const [escalationPhrases, setEscalationPhrases] = useState(BLANK_TEMPLATE.escalationPhrases.join(", "));
  const [operatingHours, setOperatingHours] = useState("always");
  const [afterHoursBehavior, setAfterHoursBehavior] = useState<AfterHoursBehavior>("answer");
  const [afterHoursMessage, setAfterHoursMessage] = useState("");
  const [knowledgeBase, setKnowledgeBase] = useState("");
  const [llmTier, setLlmTier] = useState<"standard" | "advanced">("standard");
  const [bargeInSensitivity, setBargeInSensitivity] = useState(0.7);
  const [bargeInGraceMs, setBargeInGraceMs] = useState(300);
  const [endCallOnSilenceSeconds, setEndCallOnSilenceSeconds] = useState(60);
  const [reminderAfterSilenceSeconds, setReminderAfterSilenceSeconds] = useState(30);
  const [maxReminders, setMaxReminders] = useState(1);
  const [detectVoicemail, setDetectVoicemail] = useState(true);
  const [handleCallScreening, setHandleCallScreening] = useState(true);
  const [dtmfEnabled, setDtmfEnabled] = useState(false);
  const [dtmfTerminationKey, setDtmfTerminationKey] = useState<"#" | "*">("#");
  const [dtmfInputTimeoutSeconds, setDtmfInputTimeoutSeconds] = useState(5);
  const [dtmfMaxDigits, setDtmfMaxDigits] = useState(8);
  const [dtmfDigitMappings, setDtmfDigitMappings] = useState<Record<string, string>>({});
  const [webVoiceEnabled, setWebVoiceEnabled] = useState(false);
  const [webVoiceAllowedOrigins, setWebVoiceAllowedOrigins] = useState("");
  const [webVoiceRequireConsent, setWebVoiceRequireConsent] = useState(true);
  const [whatsappEnabled, setWhatsappEnabled] = useState(false);
  const [whatsappPhoneNumberId, setWhatsappPhoneNumberId] = useState("");
  const [sttEndpointingMs, setSttEndpointingMs] = useState(300);
  const [sttVocabularyDomain, setSttVocabularyDomain] = useState("general");
  const [sttBoostedKeywords, setSttBoostedKeywords] = useState("");
  const [safetyGuardrails, setSafetyGuardrails] = useState<string[]>([]);
  const [postCallExtractionFields, setPostCallExtractionFields] = useState(["summary", "successful", "sentiment", "intent"]);
  const [bookingRequiredFields, setBookingRequiredFields] = useState<string[]>(DEFAULT_BOOKING_FIELDS);
  const [bookingNotificationChannels, setBookingNotificationChannels] = useState<string[]>(DEFAULT_BOOKING_NOTIFICATIONS);
  const [bookingNotificationRecipient, setBookingNotificationRecipient] = useState("");
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [phoneCountries, setPhoneCountries] = useState<CountryCallingCode[]>([]);
  const [customVariables, setCustomVariables] = useState<AgentVariableDefinition[]>([]);
  const [existingVariableKeys, setExistingVariableKeys] = useState<string[]>([]);
  const [inheritedValues, setInheritedValues] = useState<Record<string, string>>({});
  const [pendingDraft, setPendingDraft] = useState<AgentDraftView | null>(null);
  const [draftOpened, setDraftOpened] = useState(false);
  const [versionsRefresh, setVersionsRefresh] = useState(0);
  const [showPersonalisation, setShowPersonalisation] = useState(openPersonalisation);
  const [loadingAgent, setLoadingAgent] = useState(editing);
  const [loadFailure, setLoadFailure] = useState<{ status?: number; message: string } | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [saved, setSaved] = useState(false);
  // The live test panel: open by default only when there is room for it beside the form.
  const [testPanelOpen, setTestPanelOpen] = useState(false);
  useEffect(() => { setTestPanelOpen(window.innerWidth >= 1560); }, []);
  const [loadedAgent, setLoadedAgent] = useState<Agent | null>(null);
  const [readiness, setReadiness] = useState<AgentReadiness | null>(null);
  const [showDelete, setShowDelete] = useState(false);
  const [showNumberPicker, setShowNumberPicker] = useState(openPhonePicker);
  const [deleteError, setDeleteError] = useState("");
  const [generating, setGenerating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const studioFormRef = useRef<HTMLFormElement>(null);
  const openPhoneAfterSaveRef = useRef(false);

  useEffect(() => {
    listCountryCallingCodes().then(setPhoneCountries).catch(() => setPhoneCountries([]));
  }, []);

  useEffect(() => {
    if (editing) return;
    Promise.all([loadWorkspaceProfile(), loadWorkspaceCallDefaults()]).then(([profile, defaults]) => {
      setTimezone(profile.timezone);
      setDefaultBookingDurationMinutes(profile.defaultBookingDurationMinutes);
      setWorkspaceBookingDurationMinutes(profile.defaultBookingDurationMinutes);
      setSaveTranscript(defaults.saveTranscript);
      setRecordCalls(defaults.recordCalls);
      setBargeInSensitivity(defaults.bargeInSensitivity);
    }).catch(() => {
      // Keep safe local defaults when workspace preferences are unavailable.
    });
    // A new agent inherits the workspace business profile for any detail left empty.
    getBusinessProfile().then((profile) => setInheritedValues(profile.facts ?? {})).catch(() => setInheritedValues({}));
  }, [editing]);

  useEffect(() => {
    if (editing) return;
    setTemplatesStatus("loading");
    listAgentTemplates()
      .then((storedTemplates) => {
        setAvailableTemplates(storedTemplates.map(mapStoredTemplate));
        setTemplatesStatus("ready");
      })
      // The backend catalog is the only one; say so instead of showing stale built-in copies.
      .catch(() => setTemplatesStatus("failed"));
  }, [editing, templatesAttempt]);

  /** Fills the editor from saved settings: the published agent, or a draft laid over it. */
  const applyAgentSettings = useCallback((agent: Agent | AgentDraft) => {
    setName(agent.name);
    setDescription(agent.description);
    setLanguage(agent.defaultLanguage);
    setSupportedLanguages(agent.supportedLanguages);
    setVoice(agent.ttsVoiceId ?? "");
    setTimezone(agent.timezone);
    setGreeting(agent.greetingMessage);
    setPrompt(agent.systemPrompt);
    setBookingEnabled(agent.bookingEnabled);
    setDefaultBookingDurationMinutes(agent.defaultBookingDurationMinutes);
    setMaxDuration(String(Math.max(1, Math.round(agent.maxCallDurationSeconds / 60))));
    setSaveTranscript(agent.saveTranscript);
    setRecordCalls(agent.recordCalls);
    setTransferEnabled(Boolean(agent.humanTransferNumber));
    setTransferNumber(agent.humanTransferNumber ?? "");
    setEscalationPhrases(agent.escalationPhrases.join(", "));
    setOperatingHours(agent.operatingHours ?? "always");
    setAfterHoursBehavior(agent.afterHoursBehavior ?? "answer");
    setAfterHoursMessage(agent.afterHoursMessage ?? "");
    setKnowledgeBase(agent.knowledgeBase ?? "");
    setLlmTier(agent.llmTier);
    setBargeInSensitivity(agent.bargeInSensitivity);
    setBargeInGraceMs(agent.bargeInGraceMs);
    setEndCallOnSilenceSeconds(agent.endCallOnSilenceSeconds);
    setReminderAfterSilenceSeconds(agent.reminderAfterSilenceSeconds);
    setMaxReminders(agent.maxReminders);
    setDetectVoicemail(agent.detectVoicemail);
    setHandleCallScreening(agent.handleCallScreening);
    setDtmfEnabled(agent.dtmfEnabled);
    setDtmfTerminationKey(agent.dtmfTerminationKey);
    setDtmfInputTimeoutSeconds(agent.dtmfInputTimeoutSeconds);
    setDtmfMaxDigits(agent.dtmfMaxDigits);
    setDtmfDigitMappings(agent.dtmfDigitMappings);
    setWebVoiceEnabled(agent.webVoiceEnabled);
    setWebVoiceAllowedOrigins(agent.webVoiceAllowedOrigins.join("\n"));
    setWebVoiceRequireConsent(agent.webVoiceRequireConsent);
    setWhatsappEnabled(agent.whatsappEnabled);
    setWhatsappPhoneNumberId(agent.whatsappPhoneNumberId ?? "");
    setSttEndpointingMs(agent.sttEndpointingMs);
    setSttVocabularyDomain(agent.sttVocabularyDomain ?? "general");
    setSttBoostedKeywords(agent.sttBoostedKeywords ?? "");
    setSafetyGuardrails(agent.safetyGuardrails);
    setPostCallExtractionFields(agent.postCallExtractionFields);
    setBookingRequiredFields(agent.bookingRequiredFields);
    setBookingNotificationChannels(agent.bookingNotificationChannels);
    setBookingNotificationRecipient(agent.bookingNotificationRecipient ?? "");
  }, []);

  useEffect(() => {
    if (!agentId) return;
    setDraftOpened(false);
    getAgentDraft(agentId).then(setPendingDraft).catch(() => setPendingDraft(null));
  }, [agentId, loadAttempt]);

  useEffect(() => {
    if (!agentId) return;
    setLoadingAgent(true);
    setLoadFailure(null);
    Promise.all([getAgent(agentId), listAgentVariables(agentId), getAgentReadiness(agentId)])
      .then(([agent, variables, agentReadiness]) => {
        const definitions = variables.map((variable) => ({
          key: variable.key,
          label: variable.label,
          description: variable.description ?? "",
          required: variable.required,
        }));
        setSelectedTemplate({
          id: `saved-${agent.id}`,
          name: "Saved agent",
          category: "Custom",
          group: "Custom",
          description: agent.description,
          icon: Bot,
          greeting: agent.greetingMessage,
          prompt: agent.systemPrompt,
          bookingEnabled: agent.bookingEnabled,
          source: "custom",
          defaultLanguage: agent.defaultLanguage,
          supportedLanguages: agent.supportedLanguages,
          escalationPhrases: agent.escalationPhrases,
          variables: [],
          defaultBookingDurationMinutes: agent.defaultBookingDurationMinutes,
          bookingRequiredFields: agent.bookingRequiredFields,
          bookingNotificationChannels: agent.bookingNotificationChannels,
        });
        setLoadedAgent(agent);
        setReadiness(agentReadiness);
        applyAgentSettings(agent);
        setCustomVariables(definitions);
        setExistingVariableKeys(definitions.map((variable) => variable.key));
        setInheritedValues(Object.fromEntries(
          variables.filter((variable) => variable.inheritedValue).map((variable) => [variable.key, variable.inheritedValue ?? ""]),
        ));
        setVariableValues({
          ...Object.fromEntries(variables.map((variable) => [variable.key, variable.value])),
          ...(agent.calendarProvider ? { calendar_provider: agent.calendarProvider } : {}),
          ...(agent.routingPolicy ? { routing_policy: agent.routingPolicy } : {}),
        });
        setStage("studio");
      })
      .catch((caught) => setLoadFailure({
        status: caught instanceof ApiError ? caught.status : undefined,
        message: caught instanceof Error ? caught.message : "We couldn’t load this agent. Please try again.",
      }))
      .finally(() => setLoadingAgent(false));
  }, [agentId, loadAttempt, applyAgentSettings]);

  const categories = ["All", "Appointments", "Support", "Sales"];

  const visibleTemplates = useMemo(
    () => category === "All"
      ? availableTemplates
      : availableTemplates.filter((template) => template.group === category),
    [availableTemplates, category],
  );
  const agentVariables = useMemo(
    () => [...selectedTemplate.variables, ...customVariables],
    [selectedTemplate.variables, customVariables],
  );
  const countryName = COUNTRIES.find((country) => country.code === session?.tenant?.countryCode)?.name
    ?? "your country";

  function selectTemplate(template: Template) {
    setSelectedTemplate(template);
    setDescription(template.description);
    setGreeting(template.greeting);
    setPrompt(template.prompt);
    setBookingEnabled(template.bookingEnabled);
    setLanguage(template.defaultLanguage);
    setSupportedLanguages(Array.from(new Set([template.defaultLanguage, ...template.supportedLanguages])));
    setEscalationPhrases(template.escalationPhrases.join(", "));
    setDefaultBookingDurationMinutes(workspaceBookingDurationMinutes);
    setBookingRequiredFields(template.bookingRequiredFields);
    setBookingNotificationChannels(template.bookingNotificationChannels);
    setBookingNotificationRecipient("");
    setCustomVariables([]);
    setVariableValues(Object.fromEntries(template.variables.map((variable) => [variable.key, ""])));
    setStage("studio");
    setShowPersonalisation(template.variables.length > 0 || template.bookingEnabled);
    setActiveSection("main");
  }

  async function generateFromBrief() {
    if (!brief.trim()) return;
    setGenerating(true);
    setError("");
    try {
      const generated = await generateAgentFromBrief(brief.trim());
      const custom: Template = {
        id: "generated",
        name: generated.name,
        category: "Custom",
        group: "Custom",
        description: generated.description,
        icon: WandSparkles,
        greeting: generated.greetingMessage,
        prompt: generated.systemPrompt,
        bookingEnabled: generated.bookingEnabled,
        source: "custom",
        defaultLanguage: generated.defaultLanguage,
        supportedLanguages: generated.supportedLanguages,
        escalationPhrases: generated.escalationPhrases,
        variables: generated.variables ?? [],
        defaultBookingDurationMinutes: generated.defaultBookingDurationMinutes,
        bookingRequiredFields: generated.bookingEnabled ? DEFAULT_BOOKING_FIELDS : [],
        bookingNotificationChannels: generated.bookingEnabled ? DEFAULT_BOOKING_NOTIFICATIONS : ["dashboard"],
      };
      setName(generated.name);
      selectTemplate(custom);
      // Details the brief already states are filled in for the owner to check, not retyped.
      setVariableValues(Object.fromEntries(
        (generated.variables ?? []).map((variable) => [variable.key, variable.value ?? ""]),
      ));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to generate an agent setup.");
    } finally {
      setGenerating(false);
    }
  }

  /** Every agent setting as the API expects it. */
  function agentSettings(): AgentDraft {
    return {
      name,
      description,
      greetingMessage: greeting,
      systemPrompt: prompt,
      defaultLanguage: language,
      supportedLanguages: Array.from(new Set([language, ...supportedLanguages])),
      ttsVoiceId: voice,
      humanTransferNumber: transferEnabled && transferNumber.trim() ? transferNumber.trim() : null,
      escalationPhrases: escalationPhrases.split(",").map((phrase) => phrase.trim()).filter(Boolean),
      bookingEnabled,
      defaultBookingDurationMinutes,
      timezone,
      knowledgeBase,
      operatingHours,
      afterHoursBehavior,
      afterHoursMessage: afterHoursMessage.trim() || null,
      maxCallDurationSeconds: Math.max(60, Number(maxDuration || 5) * 60),
      saveTranscript,
      recordCalls,
      llmTier,
      bargeInSensitivity,
      bargeInGraceMs,
      endCallOnSilenceSeconds,
      reminderAfterSilenceSeconds,
      maxReminders,
      detectVoicemail,
      handleCallScreening,
      dtmfEnabled,
      dtmfTerminationKey,
      dtmfInputTimeoutSeconds,
      dtmfMaxDigits,
      dtmfDigitMappings,
      webVoiceEnabled,
      webVoiceAllowedOrigins: webVoiceAllowedOrigins.split(/\r?\n|,/).map((origin) => origin.trim()).filter(Boolean),
      webVoiceRequireConsent,
      whatsappEnabled,
      whatsappPhoneNumberId: whatsappEnabled && whatsappPhoneNumberId.trim() ? whatsappPhoneNumberId.trim() : null,
      sttEndpointingMs,
      sttVocabularyDomain: sttVocabularyDomain === "general" ? null : sttVocabularyDomain,
      sttBoostedKeywords: sttBoostedKeywords.trim() || null,
      safetyGuardrails,
      postCallExtractionFields,
      bookingRequiredFields,
      bookingNotificationChannels,
      bookingNotificationRecipient: bookingNotificationRecipient.trim() || null,
    };
  }

  function definitionsOf(variables: AgentVariableDefinition[]): CreateAgentVariable[] {
    return variables.map((variable) => ({
      key: variable.key,
      label: variable.label,
      description: variable.description,
      value: variableValues[variable.key] ?? "",
      required: variable.required,
    }));
  }

  /** An edit of a saved agent: settings, values for existing details, and any new details. */
  function updateSetup(): AgentSetup {
    return {
      agent: agentSettings(),
      variables: definitionsOf(customVariables.filter((item) => !existingVariableKeys.includes(item.key))),
      values: Object.fromEntries(
        Object.entries(variableValues).filter(([key]) => existingVariableKeys.includes(key)),
      ),
    };
  }

  /** Keeps the edits on the server without changing live calls. */
  async function saveAsDraft() {
    if (!agentId) return;
    setBusy(true);
    setError("");
    try {
      const saved = await saveAgentDraft(agentId, updateSetup());
      setPendingDraft(saved);
      setDraftOpened(true);
      markSaved();
      toast.success("Draft saved", { description: "Live calls keep the published version until you publish." });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save the draft.");
    } finally {
      setBusy(false);
    }
  }

  /** Loads a saved draft into the editor, on top of the published settings. */
  function openDraft(draft: AgentDraftView) {
    applyAgentSettings(draft.setup.agent);
    setVariableValues((current) => ({ ...current, ...(draft.setup.values ?? {}) }));
    const added = (draft.setup.variables ?? []).filter((variable) => !existingVariableKeys.includes(variable.key));
    if (added.length) {
      setCustomVariables((current) => [
        ...current,
        ...added.map((variable) => ({ key: variable.key, label: variable.label, description: variable.description, required: variable.required })),
      ]);
      setVariableValues((current) => ({
        ...current,
        ...Object.fromEntries(added.map((variable) => [variable.key, variable.value])),
      }));
    }
    setDraftOpened(true);
  }

  async function discardDraft() {
    if (!agentId) return;
    try {
      await discardAgentDraft(agentId);
      setPendingDraft(null);
      if (draftOpened) setLoadAttempt((attempt) => attempt + 1);
      toast.success("Draft discarded");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to discard the draft.");
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (!session) {
        router.replace("/agents");
        return;
      }
      const draft = agentSettings();
      if (agentId) {
        // Publishing: one request, applied to live calls and recorded as the next version.
        setLoadedAgent(await updateAgentSetup(agentId, updateSetup()));
        setExistingVariableKeys(customVariables.map((variable) => variable.key));
        setReadiness(await getAgentReadiness(agentId));
        setPendingDraft(null);
        setVersionsRefresh((count) => count + 1);
        markSaved();
        toast.success("Published", { description: "Live calls use these settings from the next call." });
        setBusy(false);
        return;
      }
      // Template, AI-brief and hand-added details are all sent with the agent, values included.
      const created = await setupAgent({
        agent: draft,
        templateId: selectedTemplate.source === "stored" ? selectedTemplate.id : null,
        variables: definitionsOf(agentVariables),
      });
      router.replace(`/agents/${created.id}${openPhoneAfterSaveRef.current ? "?panel=phone" : ""}`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to create the agent.");
      setBusy(false);
    }
  }

  async function continueSetup() {
    if (!agentId || !readiness) return;
    if (readiness.nextStep === "complete_business_details") {
      setShowPersonalisation(true);
      return;
    }
    if (readiness.nextStep === "connect_calendar") {
      setActiveSection("integrations");
      return;
    }
    if (readiness.nextStep === "enable_channel") {
      setActiveSection("main");
      setChannelFocusRequest((current) => current + 1);
      return;
    }
    setBusy(true);
    setError("");
    try {
    if (readiness.nextStep === "assign_phone_number") {
        setShowNumberPicker(true);
      } else if (readiness.nextStep === "activate_agent") {
        setLoadedAgent(await activateAgent(agentId));
      }
      setReadiness(await getAgentReadiness(agentId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to continue setup.");
    } finally {
      setBusy(false);
    }
  }

  async function removeSavedAgent() {
    if (!agentId) return;
    setBusy(true);
    setDeleteError("");
    try {
      await deleteAgent(agentId);
      router.replace("/agents");
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : "Unable to delete this agent.");
    } finally {
      setBusy(false);
    }
  }

  const requiredDetailCount = agentVariables.filter(
    (variable) => variable.required && !variableValues[variable.key]?.trim(),
  ).length;
  const needsBusinessDetails = requiredDetailCount > 0
    || readiness?.nextStep === "complete_business_details";
  const setupActionLabel = readiness?.nextStep === "complete_business_details" ? "Complete business details"
    : readiness?.nextStep === "connect_calendar" ? "Connect calendar"
      : readiness?.nextStep === "assign_phone_number" ? "Assign number"
        : readiness?.nextStep === "enable_channel" ? "Enable a channel"
        : readiness?.nextStep === "activate_agent" ? "Activate agent"
          : readiness?.active ? "Active" : "";
  const setupMilestones = [
    Boolean(name.trim() && description.trim()),
    Boolean(voice),
    !needsBusinessDetails,
    !bookingEnabled || Boolean(readiness?.calendarConfigured),
    Boolean(readiness?.channelConfigured),
    Boolean(readiness?.active),
  ];
  const completedMilestones = setupMilestones.filter(Boolean).length;
  const setupProgress = Math.round((completedMilestones / setupMilestones.length) * 100);
  const completedStudioSections = new Set<(typeof studioSections)[number]["id"]>();
  if (name.trim() && description.trim()) completedStudioSections.add("main");
  if (prompt.trim()) completedStudioSections.add("behavior");
  if (voice) completedStudioSections.add("speech");
  if (maxDuration) completedStudioSections.add("calls");
  if (timezone) completedStudioSections.add("routing");
  if (!bookingEnabled || readiness?.calendarConfigured) completedStudioSections.add("integrations");
  if (knowledgeBase.trim()) completedStudioSections.add("knowledge");
  if (postCallExtractionFields.length) completedStudioSections.add("postcall");
  // History is a record, not a setup step, so it never shows as unfinished.
  completedStudioSections.add("history");

  /*
   * Change tracking: a snapshot of everything Save sends. Compared with the
   * snapshot taken after loading or saving, so switches, sliders, pickers and
   * text fields all count as changes, not just native form input.
   */
  const snapshot = JSON.stringify([
    name, description, greeting, prompt, language, supportedLanguages, voice, transferEnabled, transferNumber,
    escalationPhrases, bookingEnabled, defaultBookingDurationMinutes, timezone, knowledgeBase, operatingHours,
    afterHoursBehavior, afterHoursMessage, maxDuration, saveTranscript, recordCalls, llmTier, bargeInSensitivity,
    bargeInGraceMs, endCallOnSilenceSeconds, reminderAfterSilenceSeconds, maxReminders, detectVoicemail,
    handleCallScreening, dtmfEnabled, dtmfTerminationKey, dtmfInputTimeoutSeconds, dtmfMaxDigits, dtmfDigitMappings,
    webVoiceEnabled, webVoiceAllowedOrigins, webVoiceRequireConsent, whatsappEnabled, whatsappPhoneNumberId,
    sttEndpointingMs, sttVocabularyDomain, sttBoostedKeywords, safetyGuardrails, postCallExtractionFields,
    bookingRequiredFields, bookingNotificationChannels, bookingNotificationRecipient, variableValues,
  ]);
  const snapshotRef = useRef(snapshot);
  snapshotRef.current = snapshot;
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null);
  useEffect(() => {
    if (!loadingAgent && savedSnapshot === null && stage === "studio") setSavedSnapshot(snapshot);
  }, [loadingAgent, savedSnapshot, snapshot, stage]);
  const dirty = savedSnapshot !== null && snapshot !== savedSnapshot;
  // A draft that is open in the editor can be published even before any further edit.
  const canPublish = dirty || (draftOpened && pendingDraft !== null);
  function markSaved() {
    setSaved(true);
    setSavedSnapshot(snapshotRef.current);
  }

  return (
    <>
      {loadingAgent ? (
        <div className="agent-editor-loading"><LoaderCircle className="spin" /> Loading agent configuration...</div>
      ) : loadFailure ? (
        <LoadFailure
          status={loadFailure.status}
          message={loadFailure.message}
          onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
          notFoundTitle="This agent doesn’t exist"
          notFoundDescription="It may have been deleted, or the link belongs to another workspace."
          backHref="/agents"
          backLabel="Back to agents"
        />
      ) : stage === "templates" ? (
        <TemplateSelection
          brief={brief}
          category={category}
          categories={categories}
          templates={visibleTemplates}
          templatesStatus={templatesStatus}
          onRetryTemplates={() => setTemplatesAttempt((attempt) => attempt + 1)}
          onBriefChange={setBrief}
          onCategoryChange={setCategory}
          onGenerate={generateFromBrief}
          generating={generating}
          error={error}
          onSelect={selectTemplate}
          onBack={() => router.push("/agents")}
        />
      ) : (
        <form className={cn("agent-studio", editing && pendingDraft && "has-notice")} ref={studioFormRef} onChange={() => {
          if (saved) setSaved(false);
        }} onSubmit={submit}>
          <header className="agent-studio-header">
            <button type="button" onClick={() => editing ? router.push("/agents") : setStage("templates")}>
              <ArrowLeft size={17} /> {editing ? "Agents" : "Templates"}
            </button>
            <div className="agent-studio-title">
              <span className="agent-list-avatar"><Bot size={20} /></span>
              <div><small>{editing ? "Agent configuration" : "Draft agent"}</small><h1>{name || "Unnamed agent"}</h1></div>
            </div>
            <div className="studio-kit ml-auto flex min-w-0 items-center gap-2 font-sans">
              <div className="mr-1 hidden items-center gap-2 whitespace-nowrap text-[0.8rem] text-fg-muted lg:flex" aria-live="polite">
                {editing ? (
                  <span className="inline-flex items-center gap-1.5 font-medium text-fg-soft">
                    <span aria-hidden="true" className={cn("size-2 rounded-full", loadedAgent?.active ? "bg-success shadow-[0_0_0_3px_color-mix(in_oklab,var(--color-success)_20%,transparent)]" : "bg-fg-muted")} />
                    {loadedAgent?.active ? "Live" : "Draft"}
                  </span>
                ) : null}
                {editing ? <span aria-hidden="true" className="text-line-strong">·</span> : null}
                {busy ? (
                  <span className="inline-flex items-center gap-1.5"><LoaderCircle className="spin size-3.5" /> Saving…</span>
                ) : editing && !dirty ? (
                  <span className="inline-flex items-center gap-1.5"><Check className="size-3.5 text-success" /> All changes saved</span>
                ) : editing ? (
                  <span className="inline-flex items-center gap-1.5 text-fg-soft"><span aria-hidden="true" className="size-1.5 rounded-full bg-warning" /> Unsaved changes</span>
                ) : (
                  <span>Not saved yet</span>
                )}
              </div>
              {(needsBusinessDetails || (editing && readiness && readiness.nextStep !== "ready")) && (
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy}
                  className={cn(needsBusinessDetails && "border-warning/40 bg-warning/[0.08] hover:bg-warning/[0.12]")}
                  onClick={() => needsBusinessDetails ? setShowPersonalisation(true) : void continueSetup()}
                >
                  {needsBusinessDetails ? <CircleAlert className="text-warning" /> : <Settings2 />}
                  <span className="max-[1279px]:hidden">{requiredDetailCount > 0 ? `${requiredDetailCount} details required` : setupActionLabel}</span>
                </Button>
              )}
              <Button
                variant={testPanelOpen ? "soft" : "secondary"}
                size="sm"
                aria-pressed={testPanelOpen}
                onClick={() => setTestPanelOpen((open) => !open)}
              >
                {testPanelOpen ? <PanelRightClose /> : <Play />}
                {testPanelOpen ? "Hide test" : "Test"}
              </Button>
              {editing && (
                <Tooltip content="Delete agent" side="bottom">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="Delete agent"
                    className="text-fg-muted hover:bg-danger/[0.08] hover:text-danger"
                    onClick={() => {
                      setDeleteError("");
                      setShowDelete(true);
                    }}
                  >
                    <Trash2 />
                  </Button>
                </Tooltip>
              )}
              <span aria-hidden="true" className="mx-0.5 h-5 w-px bg-line" />
              {editing && (
                <Button type="button" size="sm" variant="secondary" disabled={busy || !dirty} onClick={() => void saveAsDraft()}>
                  Save draft
                </Button>
              )}
              <Button
                type="submit"
                size="sm"
                variant={editing && !canPublish && !busy ? "secondary" : "primary"}
                disabled={busy || (editing && !canPublish)}
                className={cn("min-w-[7.5rem]", editing && !canPublish && !busy && "disabled:opacity-100 disabled:text-fg-muted disabled:shadow-none")}
              >
                {busy ? <LoaderCircle className="spin" /> : editing && !canPublish ? <Check /> : null}
                {busy ? "Saving…" : editing ? canPublish ? "Publish" : "Published" : "Save draft"}
              </Button>
            </div>
          </header>

          {editing && pendingDraft && (
            <div role="status" className="studio-kit flex flex-wrap items-center justify-between gap-3 border-b border-warning/30 bg-warning/[0.06] px-5 py-2.5 font-sans text-[0.84rem] text-fg">
              <span className="inline-flex min-w-0 items-center gap-2">
                <FileText className="size-4 shrink-0 text-warning" />
                {draftOpened
                  ? "You’re editing an unpublished draft. Callers keep the published version until you publish."
                  : `Unpublished draft saved ${formatWhen(pendingDraft.savedAt)}${pendingDraft.savedBy ? ` by ${pendingDraft.savedBy}` : ""}. Callers still get the published version.`}
              </span>
              <span className="flex gap-2">
                {!draftOpened && (
                  <Button type="button" size="xs" variant="secondary" disabled={busy} onClick={() => openDraft(pendingDraft)}>Open draft</Button>
                )}
                <Button type="button" size="xs" variant="ghost" disabled={busy} onClick={() => void discardDraft()}>Discard draft</Button>
              </span>
            </div>
          )}

          <div className={`agent-studio-layout ${testPanelOpen ? "test-open" : "test-closed"}`}>
            <aside className="agent-studio-nav">
              <span>Agent setup</span>
              <div className="studio-setup-progress" aria-label={`Agent is ${setupProgress}% ready to go live`}>
                <div><strong>Launch readiness</strong><span>{setupProgress}%</span></div>
                <i aria-hidden="true"><span style={{ width: `${setupProgress}%` }} /></i>
                <small>{setupProgress === 100 ? "Ready to activate" : "Complete the remaining setup requirements"}</small>
              </div>
              <div className="studio-nav-sections" aria-label="Agent setup sections">
                {studioSections.filter((section) => editing || section.id !== "history").map(({ id, label, description: sectionDescription, icon: Icon }) => (
                  <button className={activeSection === id ? "active" : ""} type="button" key={id} onClick={() => setActiveSection(id)}>
                    <Icon size={18} />
                    <span><strong>{label}</strong><small>{sectionDescription}</small></span>
                    {completedStudioSections.has(id) && <Check className="studio-nav-check" size={14} aria-label="Configured" />}
                  </button>
                ))}
              </div>
              <div className="studio-template-note">
                <Sparkles size={17} />
                <div><small>{editing ? "Business details" : "Template"}</small><strong>{editing ? `${agentVariables.length} prompt variables` : selectedTemplate.name}</strong></div>
                <button className="console-secondary-button" type="button" onClick={() => editing ? setShowPersonalisation(true) : agentVariables.length ? setShowPersonalisation(true) : setStage("templates")}>
                  {editing || agentVariables.length ? "Personalise" : "Change"}
                </button>
              </div>
            </aside>

            <section className="agent-studio-form">
              {activeSection === "main" && (
                <MainSettings
                  name={name}
                  description={description}
                  language={language}
                  supportedLanguages={supportedLanguages}
                  voice={voice}
                  timezone={timezone}
                  maxDuration={maxDuration}
                  greeting={greeting}
                  bookingEnabled={bookingEnabled}
                  defaultBookingDurationMinutes={defaultBookingDurationMinutes}
                  bookingRequiredFields={bookingRequiredFields}
                  bookingFieldSuggestions={selectedTemplate.bookingRequiredFields}
                  bookingNotificationChannels={bookingNotificationChannels}
                  bookingNotificationRecipient={bookingNotificationRecipient}
                  saveTranscript={saveTranscript}
                  recordCalls={recordCalls}
                  webVoiceEnabled={webVoiceEnabled}
                  webVoiceAllowedOrigins={webVoiceAllowedOrigins}
                  webVoiceRequireConsent={webVoiceRequireConsent}
                  webVoicePublicId={loadedAgent?.webVoicePublicId}
                  whatsappEnabled={whatsappEnabled}
                  whatsappPhoneNumberId={whatsappPhoneNumberId}
                  phoneNumber={loadedAgent?.twilioPhoneNumber}
                  phoneNumberProvider={loadedAgent?.phoneNumberProvider}
                  phoneNumberStatus={loadedAgent?.phoneNumberStatus}
                  channelFocusRequest={channelFocusRequest}
                  onAssignPhone={() => {
                    if (agentId) {
                      setShowNumberPicker(true);
                      return;
                    }
                    openPhoneAfterSaveRef.current = true;
                    studioFormRef.current?.requestSubmit();
                  }}
                  onRefreshPhone={agentId && loadedAgent?.phoneNumberOrderId ? async () => {
                    setBusy(true);
                    setError("");
                    try {
                      const refreshed = await refreshAgentPhoneNumber(agentId);
                      setLoadedAgent(refreshed);
                      setReadiness(await getAgentReadiness(agentId));
                    } catch (caught) {
                      setError(caught instanceof Error ? caught.message : "Unable to refresh number status.");
                    } finally {
                      setBusy(false);
                    }
                  } : undefined}
                  onName={setName}
                  onDescription={setDescription}
                  onLanguage={(value) => {
                    setLanguage(value);
                    setSupportedLanguages((current) => Array.from(new Set([value, ...current])));
                  }}
                  onSupportedLanguages={setSupportedLanguages}
                  onVoice={setVoice}
                  onTimezone={setTimezone}
                  onDuration={setMaxDuration}
                  onGreeting={setGreeting}
                  onBooking={setBookingEnabled}
                  onDefaultBookingDurationMinutes={setDefaultBookingDurationMinutes}
                  onBookingRequiredFields={setBookingRequiredFields}
                  onBookingNotificationChannels={setBookingNotificationChannels}
                  onBookingNotificationRecipient={setBookingNotificationRecipient}
                  onSaveTranscript={setSaveTranscript}
                  onRecordCalls={setRecordCalls}
                  onWebVoiceEnabled={setWebVoiceEnabled}
                  onWebVoiceAllowedOrigins={setWebVoiceAllowedOrigins}
                  onWebVoiceRequireConsent={setWebVoiceRequireConsent}
                  onWhatsappEnabled={setWhatsappEnabled}
                  onWhatsappPhoneNumberId={setWhatsappPhoneNumberId}
                />
              )}
              {activeSection === "behavior" && (
                <BehaviorSettings
                  prompt={prompt}
                  onPrompt={setPrompt}
                  template={selectedTemplate.name}
                  variables={agentVariables}
                  values={{ ...variableValues, agent_name: name, timezone }}
                  agentId={agentId}
                  language={language}
                  unsaved={dirty}
                  savedVersion={savedSnapshot ?? ""}
                />
              )}
              {activeSection === "speech" && (
                <SpeechSettings
                  voice={voice}
                  language={language}
                  supportedLanguages={supportedLanguages}
                  bargeInSensitivity={bargeInSensitivity}
                  bargeInGraceMs={bargeInGraceMs}
                  sttEndpointingMs={sttEndpointingMs}
                  vocabularyDomain={sttVocabularyDomain}
                  boostedKeywords={sttBoostedKeywords}
                  onVoice={setVoice}
                  onBargeInSensitivity={setBargeInSensitivity}
                  onBargeInGraceMs={setBargeInGraceMs}
                  onSttEndpointingMs={setSttEndpointingMs}
                  onVocabularyDomain={setSttVocabularyDomain}
                  onBoostedKeywords={setSttBoostedKeywords}
                />
              )}
              {activeSection === "calls" && (
                <CallBehaviorSettings
                  maxDuration={maxDuration}
                  endCallOnSilenceSeconds={endCallOnSilenceSeconds}
                  reminderAfterSilenceSeconds={reminderAfterSilenceSeconds}
                  maxReminders={maxReminders}
                  detectVoicemail={detectVoicemail}
                  handleCallScreening={handleCallScreening}
                  dtmfEnabled={dtmfEnabled}
                  dtmfTerminationKey={dtmfTerminationKey}
                  dtmfInputTimeoutSeconds={dtmfInputTimeoutSeconds}
                  dtmfMaxDigits={dtmfMaxDigits}
                  dtmfDigitMappings={dtmfDigitMappings}
                  safetyGuardrails={safetyGuardrails}
                  onMaxDuration={setMaxDuration}
                  onEndCallOnSilenceSeconds={setEndCallOnSilenceSeconds}
                  onReminderAfterSilenceSeconds={setReminderAfterSilenceSeconds}
                  onMaxReminders={setMaxReminders}
                  onDetectVoicemail={setDetectVoicemail}
                  onHandleCallScreening={setHandleCallScreening}
                  onDtmfEnabled={setDtmfEnabled}
                  onDtmfTerminationKey={setDtmfTerminationKey}
                  onDtmfInputTimeoutSeconds={setDtmfInputTimeoutSeconds}
                  onDtmfMaxDigits={setDtmfMaxDigits}
                  onDtmfDigitMappings={setDtmfDigitMappings}
                  onSafetyGuardrails={setSafetyGuardrails}
                />
              )}
              {activeSection === "routing" && (
                <RoutingSettings
                  transferEnabled={transferEnabled}
                  transferNumber={transferNumber}
                  escalationPhrases={escalationPhrases}
                  operatingHours={operatingHours}
                  timezone={timezone}
                  afterHoursBehavior={afterHoursBehavior}
                  afterHoursMessage={afterHoursMessage}
                  onTransferEnabled={setTransferEnabled}
                  onTransferNumber={setTransferNumber}
                  onEscalationPhrases={setEscalationPhrases}
                  onOperatingHours={setOperatingHours}
                  onAfterHoursBehavior={setAfterHoursBehavior}
                  onAfterHoursMessage={setAfterHoursMessage}
                />
              )}
              {activeSection === "integrations" && (
                <IntegrationsSettings agentId={agentId} bookingEnabled={bookingEnabled} />
              )}
              {activeSection === "knowledge" && <KnowledgeSettings agentId={agentId} knowledgeBase={knowledgeBase} onKnowledgeBase={setKnowledgeBase} />}
              {activeSection === "postcall" && <PostCallSettings fields={postCallExtractionFields} onFields={setPostCallExtractionFields} />}
              {activeSection === "history" && agentId && (
                <VersionHistory agentId={agentId} refreshKey={versionsRefresh} hasUnsavedChanges={dirty} onRestored={() => setLoadAttempt((attempt) => attempt + 1)} />
              )}
              {error && <div className="form-alert error">{error}</div>}
            </section>

            <TestCallPanel
              onClose={() => setTestPanelOpen(false)}
              agentId={agentId}
              agentName={name || "your agent"}
              voiceId={voice}
              defaultLanguage={language}
              supportedLanguages={supportedLanguages}
            />
          </div>
          {showPersonalisation && (
            <PersonalisationDrawer
              agentSaved={Boolean(agentId)}
              countryName={countryName}
              defaultPhoneRegion={session?.tenant?.countryCode || ""}
              phoneCountries={phoneCountries}
              timezone={timezone}
              bookingEnabled={bookingEnabled}
              defaultBookingDurationMinutes={defaultBookingDurationMinutes}
              values={variableValues}
              inherited={inheritedValues}
              variables={agentVariables}
              onAdd={addCustomVariable}
              onChange={(key, value) => {
                setSaved(false);
                setVariableValues((current) => ({ ...current, [key]: value }));
              }}
              onClose={() => setShowPersonalisation(false)}
              onTimezone={(value) => {
                setSaved(false);
                setTimezone(value);
              }}
              onDefaultBookingDurationMinutes={(value) => {
                setSaved(false);
                setDefaultBookingDurationMinutes(value);
              }}
              onSave={savePersonalisation}
            />
          )}
          {showDelete && loadedAgent && (
            <DeleteAgentDialog
              agent={loadedAgent}
              busy={busy}
              error={deleteError}
              onCancel={() => !busy && setShowDelete(false)}
              onConfirm={() => void removeSavedAgent()}
            />
          )}
          {showNumberPicker && agentId && (
            <PhoneNumberPicker
              agentId={agentId}
              defaultCountryCode={session?.tenant?.countryCode ?? "US"}
              existingNumber={loadedAgent?.twilioPhoneNumber}
              existingProvider={loadedAgent?.phoneNumberProvider}
              onClose={() => setShowNumberPicker(false)}
              onAssigned={(agent) => {
                setLoadedAgent(agent);
                setShowNumberPicker(false);
                markSaved();
                void getAgentReadiness(agent.id).then(setReadiness);
              }}
            />
          )}
        </form>
      )}
    </>
  );

  function addCustomVariable(variable: CreateAgentVariable) {
    if (agentVariables.some((existing) => existing.key === variable.key)) {
      throw new Error(`Variable {{${variable.key}}} already exists.`);
    }
    setCustomVariables((current) => [...current, {
      key: variable.key,
      label: variable.label,
      description: variable.description,
      required: variable.required,
    }]);
    setVariableValues((current) => ({ ...current, [variable.key]: variable.value }));
    setError("");
  }

  async function savePersonalisation() {
    if (!agentId) {
      setShowPersonalisation(false);
      return;
    }
    setBusy(true);
    setError("");
    try {
      const persistedValues = Object.fromEntries(
        Object.entries(variableValues).filter(([key]) => existingVariableKeys.includes(key)),
      );
      if (Object.keys(persistedValues).length) {
        await updateAgentVariables(agentId, persistedValues);
      }
      const newVariables = customVariables.filter((variable) => !existingVariableKeys.includes(variable.key));
      for (const variable of newVariables) {
        await createAgentVariable(agentId, {
          key: variable.key,
          label: variable.label,
          description: variable.description,
          value: variableValues[variable.key] ?? "",
          required: variable.required,
        });
      }
      setExistingVariableKeys((current) => Array.from(new Set([
        ...current,
        ...newVariables.map((variable) => variable.key),
      ])));
      if (loadedAgent && loadedAgent.timezone !== timezone) {
        setLoadedAgent(await updateAgentTimezone(agentId, timezone));
      }
      setReadiness(await getAgentReadiness(agentId));
      markSaved();
      setShowPersonalisation(false);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Unable to save business details.";
      setError(message);
      throw new Error(message);
    } finally {
      setBusy(false);
    }
  }
}

const whenFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/** "21 Nov, 14:05" for a timestamp from the API. */
function formatWhen(value: string) {
  return whenFormat.format(new Date(value));
}
