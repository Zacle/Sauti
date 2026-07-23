export type Tenant = {
  id: string;
  businessName: string;
  email: string;
  countryCode: string;
  plan: string;
  status: string;
  monthlyMinutesLimit: number;
  minutesUsedThisCycle: number;
};

export type AuthSession = {
  accessToken: string;
  refreshToken: string;
  tenant: Tenant;
};

export type WorkspaceNotification = {
  id: string;
  type: "booking.confirmed" | "booking.follow_up_required" | string;
  title: string;
  message: string;
  href: string;
  resourceType: string;
  resourceId: string | null;
  payload: Record<string, unknown>;
  createdAt: string;
  readAt: string | null;
};

export type WorkspaceNotificationList = {
  notifications: WorkspaceNotification[];
  unreadCount: number;
};

export type OnboardingStatus = {
  registered: boolean;
  emailVerified: boolean;
  hasAgent: boolean;
  hasActiveAgent: boolean;
  hasProvisionedNumber: boolean;
  nextStep: string;
};

export type Agent = {
  id: string;
  name: string;
  description: string;
    twilioPhoneNumber: string | null;
    twilioPhoneNumberSid: string | null;
    phoneNumberProvider: string | null;
    phoneNumberStatus: "active" | "pending" | "failed" | "legacy" | string | null;
    phoneNumberOrderId: string | null;
    phoneNumberAssignedAt: string | null;
  defaultLanguage: string;
  supportedLanguages: string[];
  greetingMessage: string;
  systemPrompt: string;
  ttsVoiceId: string | null;
  humanTransferNumber: string | null;
  escalationPhrases: string[];
  bookingEnabled: boolean;
  timezone: string;
  knowledgeBase: string | null;
  operatingHours: string | null;
  afterHoursBehavior: "answer" | "take_message" | "closed";
  afterHoursMessage: string | null;
  maxCallDurationSeconds: number;
  saveTranscript: boolean;
  recordCalls: boolean;
  llmTier: "standard" | "advanced";
  bargeInSensitivity: number;
  bargeInGraceMs: number;
  endCallOnSilenceSeconds: number;
  reminderAfterSilenceSeconds: number;
  maxReminders: number;
  detectVoicemail: boolean;
  handleCallScreening: boolean;
  dtmfEnabled: boolean;
  dtmfTerminationKey: "#" | "*";
  dtmfInputTimeoutSeconds: number;
  dtmfMaxDigits: number;
  dtmfDigitMappings: Record<string, string>;
  sttEndpointingMs: number;
  sttVocabularyDomain: string | null;
  sttBoostedKeywords: string | null;
  safetyGuardrails: string[];
  postCallExtractionFields: string[];
  bookingRequiredFields: string[];
  bookingNotificationChannels: string[];
  bookingNotificationRecipient: string | null;
  businessType: string | null;
  primaryUseCase: string | null;
  businessWebsite: string | null;
  bookableServices: string[];
  calendarProvider: string | null;
  routingPolicy: string | null;
  voiceProfile: string | null;
  webVoiceEnabled: boolean;
  webVoicePublicId: string;
  webVoiceAllowedOrigins: string[];
  webVoiceRequireConsent: boolean;
  whatsappEnabled: boolean;
  whatsappPhoneNumberId: string | null;
  active: boolean;
};

export type AgentDraft = {
  name: string;
  description: string;
  greetingMessage: string;
  systemPrompt: string;
  defaultLanguage: string;
  supportedLanguages: string[];
  ttsVoiceId: string | null;
  humanTransferNumber: string | null;
  escalationPhrases: string[];
  bookingEnabled: boolean;
  timezone: string;
  knowledgeBase: string;
  operatingHours: string | null;
  afterHoursBehavior: "answer" | "take_message" | "closed";
  afterHoursMessage: string | null;
  maxCallDurationSeconds: number;
  saveTranscript: boolean;
  recordCalls: boolean;
  llmTier: "standard" | "advanced";
  bargeInSensitivity: number;
  bargeInGraceMs: number;
  endCallOnSilenceSeconds: number;
  reminderAfterSilenceSeconds: number;
  maxReminders: number;
  detectVoicemail: boolean;
  handleCallScreening: boolean;
  dtmfEnabled: boolean;
  dtmfTerminationKey: "#" | "*";
  dtmfInputTimeoutSeconds: number;
  dtmfMaxDigits: number;
  dtmfDigitMappings: Record<string, string>;
  webVoiceEnabled: boolean;
  webVoiceAllowedOrigins: string[];
  webVoiceRequireConsent: boolean;
  whatsappEnabled: boolean;
  whatsappPhoneNumberId: string | null;
  sttEndpointingMs: number;
  sttVocabularyDomain: string | null;
  sttBoostedKeywords: string | null;
  safetyGuardrails: string[];
  postCallExtractionFields: string[];
  bookingRequiredFields: string[];
  bookingNotificationChannels: string[];
  bookingNotificationRecipient: string | null;
};

export type AgentStats = {
  agentId: string;
  totalCalls: number;
  bookingCalls: number;
  bookingRate: number;
};

export type AgentReadiness = {
  agentId: string;
  businessDetailsComplete: boolean;
  calendarRequired: boolean;
  calendarConfigured: boolean;
  phoneNumberConfigured: boolean;
  webVoiceConfigured: boolean;
  whatsappConfigured: boolean;
  channelConfigured: boolean;
  active: boolean;
  readyToActivate: boolean;
  nextStep: "complete_business_details" | "connect_calendar" | "assign_phone_number" | "enable_channel" | "activate_agent" | "ready";
  missingRequiredVariables: string[];
};

export type GeneratedAgentDraft = {
  name: string;
  description: string;
  greetingMessage: string;
  systemPrompt: string;
  bookingEnabled: boolean;
  defaultLanguage: string;
  supportedLanguages: string[];
  escalationPhrases: string[];
  variables: AgentVariableDefinition[];
};

export type VoiceOption = {
  provider: string;
  id: string;
  name: string;
  description: string | null;
  category: string;
  previewUrl: string | null;
  languages: string[];
  traits: Record<string, string>;
  owned: boolean;
};

export type VoiceCatalog = {
  enabledProviders: string[];
  voices: VoiceOption[];
};

export type AgentTemplate = {
  id: string;
  tenantId: string | null;
  scope: "system" | "tenant";
  editable: boolean;
  name: string;
  description: string;
  category: string;
  greetingMessage: string;
  systemPrompt: string;
  defaultLanguage: string;
  supportedLanguages: string[];
  configurationJson: string;
  version: number;
  published: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AgentVariableDefinition = {
  key: string;
  label: string;
  description: string;
  example?: string;
  required: boolean;
};

export type AgentVariable = {
  key: string;
  label: string;
  description: string | null;
  value: string;
  required: boolean;
  filled: boolean;
};

export type KnowledgeDocument = {
  id: string;
  fileName: string;
  mediaType: string | null;
  status: "processing" | "ready" | "failed";
  characterCount: number;
  chunkCount: number;
  originalStored: boolean;
  originalSizeBytes: number | null;
  errorMessage: string | null;
  createdAt: string;
};

export type CreateAgentVariable = {
  key: string;
  label: string;
  description: string;
  value: string;
  required: boolean;
};

export type Call = {
  id: string;
  agentId: string;
  twilioCallSid: string;
  callerNumber: string;
  direction: string;
  languageDetected: string | null;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number | null;
  outcome: string;
  transcript: string | null;
  conversationJson?: string | null;
  recordingUrl: string | null;
  recordingSid?: string | null;
  failureReason: string | null;
  callSummary?: string | null;
  callSuccessful?: boolean | null;
  sentiment?: string | null;
  intent?: string | null;
  transferStatus?: string | null;
  transferTargetNumber?: string | null;
  transferChildCallSid?: string | null;
  transferFailureReason?: string | null;
  transferRequestedAt?: string | null;
  transferCompletedAt?: string | null;
  afterHours?: boolean;
};

export type CallTurn = {
  turnIndex: number;
  callerTranscript: string;
  agentResponse: string;
  language: string;
  interrupted: boolean;
};

export type BrowserVoiceRuntimeSession = {
  provider: string;
  clientToken: string;
  apiBaseUrl: string;
  configuration: Record<string, unknown>;
};

export type BrowserTtsSession = {
  provider: "cartesia";
  clientToken: string;
  voiceId: string;
  modelId: string;
};

export type StartTestCallResponse = {
  call: Call;
  greeting: string;
  greetingAudioBase64: string | null;
  websocketUrl: string;
  token: string;
  inputSampleRate: number;
  mode: string;
  availabilityToolEnabled: boolean;
  runtime: BrowserVoiceRuntimeSession | null;
  browserTts: BrowserTtsSession | null;
  settings: {
    bargeInSensitivity: number;
    bargeInGraceMs: number;
    sttEndpointingMs: number;
    maxCallDurationSeconds: number;
    endCallOnSilenceSeconds: number;
    reminderAfterSilenceSeconds: number;
    maxReminders: number;
    detectVoicemail: boolean;
    handleCallScreening: boolean;
  };
};

export type AvailablePhoneNumber = {
  phoneNumber: string;
  type: string;
  locality: string;
  region: string;
  upfrontCost: string;
  monthlyCost: string;
  currency: string;
};

export type Booking = {
  id: string;
  bookingReference: string;
  agentId: string;
  callId: string | null;
  callerName: string;
  callerPhone: string;
  callerEmail: string | null;
  serviceType: string;
  bookedAt: string;
  appointmentAt: string;
  durationMinutes: number;
  externalEventId: string | null;
  status: string;
  confirmationSent: boolean;
  capturedData: Record<string, unknown>;
  calendarSyncStatus: "pending" | "synced" | "failed" | string;
  calendarSyncError: string | null;
};

export type AnalyticsSummary = {
  totalCalls: number;
  attemptedCalls: number;
  connectedCalls: number;
  completedCalls: number;
  faqAnsweredCalls: number;
  transferredCalls: number;
  voicemailCalls: number;
  bookingCalls: number;
  totalDurationSeconds: number;
  connectRate: number;
  averageDurationSeconds: number;
  avgTurnsPerCall: number;
  avgSttLatencyMs: number;
  avgLlmLatencyMs: number;
  avgTtsLatencyMs: number;
  totalCallsDelta: AnalyticsDelta;
  connectRateDelta: AnalyticsDelta;
  totalDurationSecondsDelta: AnalyticsDelta;
  averageDurationSecondsDelta: AnalyticsDelta;
  bookingCallsDelta: AnalyticsDelta;
  transferredCallsDelta: AnalyticsDelta;
};

export type AnalyticsDelta = {
  value: number;
  previousValue: number;
  percentChange: number;
};

export type DailyVolume = {
  date: string;
  callCount: number;
};

export type AnalyticsAgentSummary = {
  agentId: string;
  agentName: string;
  totalCalls: number;
  connectedCalls: number;
  bookingCalls: number;
  connectRate: number;
  avgDurationSeconds: number;
};

export type AnalyticsOutcomeByDay = {
  date: string;
  completed: number;
  transferred: number;
  voicemail: number;
  noAnswer: number;
  busy: number;
  failed: number;
  afterHours: number;
};

export type AnalyticsConnectRateByDay = {
  date: string;
  attempts: number;
  connected: number;
  rate: number;
};

export type AnalyticsFunnel = {
  attempted: number;
  connected: number;
  completed: number;
};

export type AnalyticsLanguageBreakdown = {
  language: string;
  callCount: number;
};

export type AnalyticsChannelBreakdown = {
  channel: string;
  totalCalls: number;
  connectedCalls: number;
  completedCalls: number;
  bookingCalls: number;
  connectRate: number;
};

export type AnalyticsTopIntent = {
  intent: string;
  callCount: number;
};

export type AnalyticsSentimentByDay = {
  date: string;
  analysedCalls: number;
  averageScore: number;
  positive: number;
  neutral: number;
  negative: number;
  mixed: number;
};

export type AnalyticsAfterHours = {
  totalCalls: number;
  connectedCalls: number;
  completedCalls: number;
  behaviors: Array<{ behavior: string; callCount: number }>;
};

export type AnalyticsIntegrationEvents = {
  provider: string;
  attempted: number;
  delivered: number;
  failed: number;
  retrying: number;
};

export type AnalyticsData = {
  summary: AnalyticsSummary;
  outcomesByDay: AnalyticsOutcomeByDay[];
  connectRateByDay: AnalyticsConnectRateByDay[];
  funnel: AnalyticsFunnel;
  languages: AnalyticsLanguageBreakdown[];
  channels: AnalyticsChannelBreakdown[];
  topIntents: AnalyticsTopIntent[];
  sentimentByDay: AnalyticsSentimentByDay[];
  agents: AnalyticsAgentSummary[];
  afterHours: AnalyticsAfterHours;
  integrationEvents: AnalyticsIntegrationEvents[];
};

export type BillingUsage = {
  plan: string;
  status: string;
  monthlyMinutesLimit: number;
  minutesUsedThisCycle: number;
  remainingMinutes: number;
  usagePercent: number;
  limitReached: boolean;
};

export type DashboardData = {
  onboarding: OnboardingStatus;
  agents: Agent[];
  readiness: AgentReadiness[];
  calls: Call[];
  bookings: Booking[];
  analytics: AnalyticsSummary;
  daily: DailyVolume[];
  usage: BillingUsage;
};
