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
  role: "OWNER" | "PLATFORM_ADMIN" | string;
};

export type AdminOverview = {
  workspaces: number;
  calls: number;
  bookings: number;
  customers: number;
  newDemoRequests: number;
  invitedDemoRequests: number;
  activatedPilots: number;
};

export type AdminDemoRequest = {
  id: string;
  businessName: string;
  contactName: string;
  email: string;
  countryCode: string;
  phone: string | null;
  industry: string;
  monthlyCallVolume: string;
  channels: string;
  primaryUseCase: string;
  notes: string | null;
    status: "new" | "approved" | "invited" | "rejected" | "activated" | string;
    assignedTo: string | null;
    internalNotes: string | null;
    rejectedAt: string | null;
    rejectedReason: string | null;
    invitation: {
      id: string;
      deliveryStatus: "pending" | "sent" | "failed" | string;
      deliveryAttempts: number;
      lastDeliveryAttemptAt: string | null;
      sentAt: string | null;
      lastDeliveryError: string | null;
      expiresAt: string;
      revokedAt: string | null;
      acceptedAt: string | null;
    } | null;
    createdAt: string;
  };

export type AdminDemoRequestPage = {
  requests: AdminDemoRequest[];
  total: number;
  page: number;
  pageSize: number;
};

export type AdminAuditEvent = {
  id: string; actorEmail: string; action: string; resourceType: string;
  resourceId: string; summary: string; createdAt: string;
};

export type AdminAuditPage = {
  events: AdminAuditEvent[]; total: number; page: number; pageSize: number;
};

export type AdminWorkspace = {
    id: string; businessName: string; email: string; countryCode: string;
    plan: string; status: string; minutesUsed: number; minutesLimit: number;
    agents: number; calls: number; bookings: number; customers: number;
    pilotPolicy: {
      status: "pending" | "approved" | "suspended" | string;
      currency: string; monthlyBudget: number;
      phoneNumbersApproved: boolean; liveCallingApproved: boolean;
      smsApproved: boolean; whatsappApproved: boolean;
      approvedBy: string | null; approvedAt: string | null; notes: string | null;
    } | null;
    createdAt: string;
  };

export type AdminWorkspacePage = {
  workspaces: AdminWorkspace[]; total: number; page: number; pageSize: number;
};

export type AdminPilotReadiness = {
  checks: Array<{ key: string; label: string; status: "ready" | "not_ready" | "not_required" | string; required: boolean; detail: string }>;
  completedChecks: number; blockingChecks: number; launchApproved: boolean; readyForLaunch: boolean;
  supportContactName: string | null; supportContactEmail: string | null; supportContactPhone: string | null;
  launchNotes: string | null; approvedBy: string | null; approvedAt: string | null;
};

export type AdminCustomer = {
  tenantId: string; businessName: string; phone: string; calls: number; lastContactAt: string;
};

export type AdminCustomerCall = {
  id: string; agentName: string; direction: string; outcome: string;
  language: string | null; durationSeconds: number | null; startedAt: string;
};

export type AdminCustomerDetail = AdminCustomer & { recentCalls: AdminCustomerCall[] };

export type AdminCustomerPage = {
  customers: AdminCustomer[]; total: number; page: number; pageSize: number;
};

export type AdminPlatformAnalytics = {
  days: number;
  from: string;
  to: string;
  generatedAt: string;
  web: {
    pageViews: number; uniqueVisitors: number; voiceDemoStarts: number; voiceDemoCompletions: number;
    demoRequests: number; visitorToRequestPercent: number;
    daily: Array<{ date: string; pageViews: number; visitors: number; voiceDemoStarts: number; voiceDemoCompletions: number; demoRequests: number }>;
    topPages: Array<{ value: string; count: number }>;
    topSources: Array<{ value: string; count: number }>;
  };
  activity: Array<{ date: string; calls: number; completed: number; failed: number; durationSeconds: number; activeWorkspaces: number }>;
  costTotals: Array<{ currency: string; costBasis: string; category: string; amount: number }>;
  dailyCosts: Array<{ date: string; currency: string; amount: number }>;
  unpricedUsage: Array<{ category: string; unit: string; quantity: number }>;
  providers: Array<{
    provider: string; status: "healthy" | "degraded" | "attention" | "unknown" | string;
    configuredConnections: number; connectionErrors: number; deliveryAttempts: number;
    delivered: number; retryingDeliveries: number; failedDeliveries: number;
    pendingCosts: number; retryingCosts: number; reconciledCosts: number;
    estimatedCosts: number; unavailableCosts: number; lastActivityAt: string | null;
  }>;
};

export type AdminReliabilityIncident = {
  id: string;
  provider: string;
  severity: "warning" | "critical" | string;
  status: "open" | "resolved" | string;
  summary: string;
  firstDetectedAt: string;
  lastDetectedAt: string;
  notifiedAt: string | null;
  resolvedAt: string | null;
};

export type AdminQueueHealth = {
  key: string;
  label: string;
  pending: number;
  retrying: number;
  exhausted: number;
  oldestQueuedAt: string | null;
};

export type AdminSlo = {
  key: string;
  label: string;
  status: "healthy" | "warning" | "critical" | "insufficient_data" | "unavailable" | string;
  actual: number;
  unit: "minutes" | "percent" | "milliseconds" | string;
  warningThreshold: number;
  criticalThreshold: number;
  sampleSize: number;
  windowMinutes: number;
  detail: string;
};

export type AdminReliabilityDrill = {
  id: string;
  status: "detected" | "acknowledged" | "resolved" | string;
  initiatedBy: string;
  initiatedAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  detectionEmailSentAt: string | null;
  recoveryEmailSentAt: string | null;
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
  defaultBookingDurationMinutes: number;
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
  defaultBookingDurationMinutes: number;
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
  defaultBookingDurationMinutes: number;
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

export type StartTestCallResponse = {
  call: Call;
  greeting: string;
  runtime: BrowserVoiceRuntimeSession;
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

export type BillingLedgerEntry = {
  id: string;
  direction: "credit" | "debit";
  category: string;
  quantity: number;
  unit: string;
  amount: number | null;
  currency: string | null;
  costBasis: "unpriced" | "rate_card" | "provider_quote" | "provider_confirmed" | "credit";
  externalReference: string | null;
  description: string | null;
  createdAt: string;
};

export type BillingAccount = {
  id: string;
  status: "preview" | "trialing" | "active" | "past_due" | "suspended" | "cancelled";
  enforcementMode: "observe" | "enforce";
  billingCurrency: string;
  monthlySpendingLimit: number | null;
  lowBalanceThreshold: number;
  communicationBalances: Record<string, number>;
  paidResourcesAllowed: boolean;
  costTotals: Array<{
    costBasis: BillingLedgerEntry["costBasis"];
    currency: string;
    amount: number;
  }>;
  unpricedUsage: Array<{
    category: string;
    unit: string;
    quantity: number;
  }>;
  reconciliation: {
    pending: number;
    retrying: number;
    reconciled: number;
    estimated: number;
    unavailable: number;
  };
  recentEntries: BillingLedgerEntry[];
};

export type BillingCheckout = {
  url: string;
  plan: "launch" | "growth" | "scale";
  interval: "monthly" | "annual";
  provider: string;
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
