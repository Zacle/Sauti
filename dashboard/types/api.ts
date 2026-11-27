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

export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER";

export type WorkspacePermission =
  | "VIEW"
  | "HANDLE_CONVERSATIONS"
  | "CONFIGURE"
  | "MANAGE_MEMBERS"
  | "MANAGE_BILLING"
  | "OWN_WORKSPACE";

/** A workspace as seen by one member. */
export type WorkspaceMembership = {
  workspace: Tenant;
  role: WorkspaceRole;
  permissions: WorkspacePermission[];
};

/** The signed-in person and every workspace they can open. */
export type Account = {
  id: string;
  email: string;
  /** The name colleagues see on this person’s work, such as WhatsApp replies; null until set. */
  displayName: string | null;
  emailVerified: boolean;
  hasPassword: boolean;
  platformAdmin: boolean;
  lastWorkspaceId: string | null;
  workspaces: WorkspaceMembership[];
};

/** Returned by sign-in and refresh. The refresh token is an HttpOnly cookie, never in the body. */
export type SessionResponse = {
  accessToken: string;
  expiresInSeconds: number;
  account: Account;
  /** True only for sessions started with an authenticator code. */
  platformAdminSession: boolean;
};

/** This tab's session. `tenant` is the workspace the tab is working in, or null if the account has none yet. */
export type AuthSession = {
  accessToken: string;
  account: Account;
  tenant: Tenant | null;
  role: WorkspaceRole | null;
  platformAdminSession: boolean;
};

export type WorkspaceMember = {
  userId: string;
  email: string;
  role: WorkspaceRole;
  joinedAt: string;
};

export type WorkspaceInvitation = {
  id: string;
  email: string;
  role: WorkspaceRole;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
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

export type AdminBillingReadiness = {
  provider: string;
  environment: "sandbox" | "live" | string;
  status: "configuration_missing" | "in_progress" | "attention" | "ready" | string;
  apiConfigured: boolean;
  webhookConfigured: boolean;
  tenantSigningConfigured: boolean;
  plansConfigured: boolean;
  addOnsConfigured: boolean;
  configuredPlans: number;
  normalizedSandboxEvents: number;
  retryingProviderEvents: number;
  failedProviderEvents: number;
  lastSandboxEvidenceAt: string | null;
  generatedAt: string;
  representativeLifecycle: {
    status: "not_started" | "awaiting_payment" | "awaiting_cancellation" | "accepted" | string;
    plan: string | null;
    interval: string | null;
    membershipReference: string | null;
    membershipActivatedAt: string | null;
    paymentSucceededAt: string | null;
    cancellationObservedAt: string | null;
  };
  variants: Array<{
    plan: string;
    interval: string;
    configured: boolean;
    planReference: string | null;
    status: "configuration_missing" | "configured" | string;
    sandboxEvidenceAt: string | null;
  }>;
  sandboxFinancial: AdminBillingFinancialSummary;
  liveFinancial: AdminBillingFinancialSummary;
};

export type AdminBillingFinancialSummary = {
  environment: "sandbox" | "live" | string;
  payments: number;
  paid: number;
  partiallyRefunded: number;
  refunded: number;
  openDisputes: number;
  disputeLost: number;
  unresolved: number;
  totals: Array<{
    currency: string;
    gross: number;
    refunded: number;
    disputeLoss: number;
    net: number;
    openDisputeExposure: number;
  }>;
  recentPositions: Array<{
    paymentReference: string;
    state: string;
    currency: string | null;
    gross: number;
    refunded: number;
    disputeLoss: number;
    net: number;
    openDisputeExposure: number;
    unresolved: boolean;
    lastEvidenceAt: string;
  }>;
  lastReconciledAt: string | null;
};

export type AdminLaunchReadiness = {
  status: "blocked" | "review_pending" | "approved" | string;
  automatedBlockingChecks: number;
  manualBlockingChecks: number;
  automatedChecks: Array<{
    key: string;
    label: string;
    passed: boolean;
    status: "ready" | "blocked" | string;
    action: string;
  }>;
  manualReview: {
    securityReviewCompleted: boolean;
    privacyLegalReviewCompleted: boolean;
    googleVerificationCompleted: boolean;
    googleVerificationReference: string | null;
    googleVerifiedAt: string | null;
    liveAcceptanceCompleted: boolean;
    liveAcceptanceEvidence: string | null;
    liveAcceptedAt: string | null;
    generalAvailabilityApproved: boolean;
    notes: string | null;
    reviewedBy: string | null;
    reviewedAt: string | null;
  };
  generatedAt: string;
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
    /** Minutes an admin added: to the one-off trial total, or to every paid cycle. */
    extraMinutes: number;
    /** Outbound messages this cycle (or in the trial), and an admin's extra messages. */
    messagesUsed: number; messagesLimit: number; extraMessages: number;
    trial: boolean;
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

export type PrivacyRetentionSettings = {
  conversationRetentionDays: number;
  recordingRetentionDays: number;
  recordingEnabledForAnyAgent: boolean;
  policyVersion: string;
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
  /** A value the AI brief found in the owner's own words, offered for them to confirm. */
  value?: string;
};

/** A service on the workspace business profile; price and duration are optional. */
export type BusinessService = {
  name: string;
  priceAmount: number | null;
  currency: string | null;
  durationMinutes: number | null;
  description: string | null;
};

export type BusinessFaq = { question: string; answer: string };

/** Business facts entered once per workspace; every agent uses them unless it sets its own value. */
export type BusinessProfile = {
  workspaceName: string;
  displayName: string | null;
  description: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  cancellationPolicy: string | null;
  services: BusinessService[];
  faqs: BusinessFaq[];
  /** The values agents receive, keyed like agent variables (business_address, services...). */
  facts: Record<string, string>;
  updatedAt: string | null;
};

export type BusinessProfileRequest = Omit<BusinessProfile, "workspaceName" | "updatedAt" | "facts">;

/** The whole agent configuration, saved in one request. */
export type AgentSetup = {
  agent: AgentDraft;
  templateId?: string | null;
  variables?: CreateAgentVariable[];
  values?: Record<string, string>;
};

/** Unpublished edits kept on the server; live calls never use them until they are published. */
export type AgentDraftView = { setup: AgentSetup; savedBy: string | null; savedAt: string };

/** One publish in an agent's history; `current` is the version live now. */
export type AgentVersion = {
  version: number;
  /** publish: saved with Publish or a restore. edit: recorded after a direct change (a detail, a tool, a document, an integration). */
  kind: "publish" | "edit";
  note: string | null;
  publishedBy: string | null;
  publishedAt: string;
  current: boolean;
  /** What restoring also puts back besides settings and details: "tools", "integrations", "documents". */
  parts: string[];
};

export type InstructionsPreviewWarning = {
  kind: "missing_required" | "empty_optional" | "unknown_placeholder";
  key: string;
  label: string;
  message: string;
};

/** Exactly what the live voice assistant is given for a saved agent. */
export type InstructionsPreview = {
  language: string;
  greeting: string;
  instructions: string;
  tools: string[];
  warnings: InstructionsPreviewWarning[];
  /** What each tool may do on a call and whether the caller is asked first. */
  toolRules: ToolRule[];
};

export type ToolRule = {
  name: string;
  effect: "read_only" | "data_write" | "external_communication" | "financial" | "transfer" | "terminal";
  /** none: runs straight away; explicit: the caller approves on a later turn; verified_review: the booking is read back for approval. */
  confirmation: "none" | "explicit" | "verified_review";
};

export type AgentVariable = {
  key: string;
  label: string;
  description: string | null;
  value: string;
  required: boolean;
  filled: boolean;
  /** The workspace business-profile value used while this agent has none of its own. */
  inheritedValue?: string | null;
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
  /** Only on a single call; lists leave it out and the drawer loads the turns. */
  transcript?: string | null;
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
  /** Set while the call's record is being completed after it ended: its transcript, then its summary. */
  recordStatus?: "transcript" | "analysis" | null;
  /** The booking made on this call, when there is one (call search and single call only). */
  booking?: CallBookingSummary | null;
  /** In a search result: where the text was found, such as "Caller: Are you open on Sunday?". */
  matchedText?: string | null;
};

export type CallBookingSummary = {
  id: string;
  bookingReference: string;
  serviceType: string;
  appointmentAt: string;
  status: string;
  callerName: string;
};

/** One page of calls, newest first, with totals over every call that matches. */
export type CallSearchResponse = {
  items: Call[];
  total: number;
  page: number;
  size: number;
  answered: number;
  averageDurationSeconds: number;
  bookings: number;
};

export type CallSearchParams = {
  q?: string;
  from?: string;
  to?: string;
  type?: "all" | "phone" | "test";
  status?: string;
  agentId?: string;
  page?: number;
  size?: number;
};

/** Something the agent did, or tried to do, during a call. */
export type CallToolAction = {
  toolName: string;
  effect: "data_write" | "external_communication" | "financial" | "transfer" | "terminal";
  /** started on a finished call means the server stopped while it ran, so the outcome is unknown. */
  status: "awaiting_confirmation" | "started" | "performed" | "not_performed" | "failed";
  summary: string | null;
  at: string;
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
  /** A trial's minutes are a one-off total; cycleEndsAt is then null. */
  trial?: boolean;
  planMinutes?: number;
  extraMinutes?: number;
  cycleStartsAt?: string | null;
  cycleEndsAt?: string | null;
  /** New AI calls are refused: unpaid, or the minutes are used. */
  accessBlocked?: boolean;
  blockedReason?: "unpaid" | "over_limit" | null;
  /** Outbound SMS and WhatsApp messages share one allowance on the same cycle. */
  messagesIncluded?: number;
  messagesUsed?: number;
  messagesRemaining?: number;
  planMessages?: number;
  extraMessages?: number;
  /** AI replies, the SMS tool and automatic confirmations are held; staff can still reply. */
  messagesBlocked?: boolean;
  messagesBlockedReason?: "unpaid" | "messages_over_limit" | null;
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
  subscription: {
    provider: string;
    providerReference: string;
    plan: string;
    interval: "monthly" | "annual";
    status: string;
    renewsAt: string | null;
    manageUrl: string | null;
  } | null;
  pendingPlanChange: BillingPlanChange | null;
  addOns: Array<{
    addOn: "agent" | "line" | "number" | "voice" | "messaging";
    quantity: number;
    status: string;
    manageUrl: string | null;
  }>;
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

export type BillingCancellation = {
  provider: string;
  status: string;
};

export type BillingPlanChange = {
  id: string;
  status: "requested" | "scheduled" | "completed";
  currentPlan: "launch" | "growth" | "scale";
  targetPlan: "launch" | "growth" | "scale";
  targetInterval: "monthly" | "annual";
  effectiveAt: string | null;
  collectionMethod: "charge_automatically" | "send_invoice" | null;
};

export type BillingCheckout = {
  url: string;
  plan: "launch" | "growth" | "scale";
  interval: "monthly" | "annual";
  provider: string;
};

export type BillingCheckoutStatus = {
  provider: string;
  environment: "sandbox" | "live";
  configured: boolean;
  addOnsConfigured: boolean;
};

export type BillingAddOnCheckout = {
  url: string;
  addOn: "agent" | "line" | "number" | "voice" | "messaging";
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

/** What an invitation link shows before anyone signs in. */
export type InvitationPreview = {
  workspaceName: string;
  email: string;
  role: WorkspaceRole;
  expiresAt: string;
  accountExists: boolean;
};

export type ApiKeyAccess = "READ_ONLY" | "FULL";

export type WorkspaceApiKey = {
  id: string;
  name: string;
  hint: string;
  access: ApiKeyAccess;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
};

/** Returned once, at creation. `secret` cannot be shown again. */
export type CreatedApiKey = { key: WorkspaceApiKey; secret: string };

export type ActivityEntry = {
  id: string;
  action: string;
  actor: string;
  summary: string;
  at: string;
};

/** Administration sign-in, step one. `authenticatorReady` false means set up the app first. */
export type AdminChallenge = { challenge: string; authenticatorReady: boolean };

export type AuthenticatorSetup = { secret: string; otpauthUri: string };

/** A workspace that closes if its only Owner deletes their account. */
export type SoleOwnedWorkspace = { workspaceId: string; name: string; otherMembers: number };
