"use client";


import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/field";
import { Badge } from "@/components/ui/primitives";
import { Select } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import {
ChevronDown,
Clock3,
Copy,
ExternalLink,
Languages,
Phone,
Plus,
RadioTower,
RefreshCw,
X
} from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { LANGUAGE_OPTIONS, TIMEZONE_OPTIONS, bookingDurationLabel, bookingDurationOptions } from "../domain/agent-options";
import {
Chip,
ChipGroup,
Field,
FieldGrid,
Meta,
Note,
RowList,
StudioGroup,
StudioInput,
StudioSection,
SwitchRow,
studioSelectClass
} from "./studio-kit";

import { readablePhoneStatus, readableProvider } from "./PhoneNumberPicker";
import { BOOKING_FIELD_CATALOG, CORE_BOOKING_FIELDS, DEFAULT_BOOKING_FIELDS } from "./studio-model";

export function languageName(code: string) {
  return LANGUAGE_OPTIONS.find(([value]) => value === code)?.[1] ?? code.toUpperCase();
}

export function MainSettings(props: {
  name: string; description: string; language: string; supportedLanguages: string[]; voice: string; timezone: string; maxDuration: string; greeting: string;
  bookingEnabled: boolean; defaultBookingDurationMinutes: number; bookingRequiredFields: string[]; bookingFieldSuggestions: string[]; bookingNotificationChannels: string[];
  bookingNotificationRecipient: string; saveTranscript: boolean; recordCalls: boolean;
  webVoiceEnabled: boolean; webVoiceAllowedOrigins: string; webVoiceRequireConsent: boolean;
  webVoicePublicId?: string; whatsappEnabled: boolean; whatsappPhoneNumberId: string; phoneNumber?: string | null;
  phoneNumberProvider?: string | null; phoneNumberStatus?: string | null;
  channelFocusRequest: number;
  onAssignPhone?: () => void;
  onRefreshPhone?: () => void;
  onName: (value: string) => void; onDescription: (value: string) => void; onLanguage: (value: string) => void;
  onSupportedLanguages: (value: string[]) => void; onVoice: (value: string) => void;
  onTimezone: (value: string) => void; onDuration: (value: string) => void; onGreeting: (value: string) => void;
  onBooking: (value: boolean) => void; onDefaultBookingDurationMinutes: (value: number) => void; onBookingRequiredFields: (value: string[]) => void;
  onBookingNotificationChannels: (value: string[]) => void; onBookingNotificationRecipient: (value: string) => void;
  onSaveTranscript: (value: boolean) => void; onRecordCalls: (value: boolean) => void;
  onWebVoiceEnabled: (value: boolean) => void; onWebVoiceAllowedOrigins: (value: string) => void;
  onWebVoiceRequireConsent: (value: boolean) => void;
  onWhatsappEnabled: (value: boolean) => void; onWhatsappPhoneNumberId: (value: string) => void;
}) {
  const channelSettingsRef = useRef<HTMLElement>(null);
  const [highlightChannels, setHighlightChannels] = useState(false);
  const [widgetColor, setWidgetColor] = useState("#31d9c9");
  const [widgetPosition, setWidgetPosition] = useState<"right" | "left">("right");
  const [widgetLanguage, setWidgetLanguage] = useState(props.language);
  const [widgetLabel, setWidgetLabel] = useState("Talk to us");
  const [languageCodeToAdd, setLanguageCodeToAdd] = useState("");
  useEffect(() => {
    if (!props.supportedLanguages.includes(widgetLanguage)) setWidgetLanguage(props.language);
  }, [props.language, props.supportedLanguages, widgetLanguage]);
  useEffect(() => {
    if (!props.channelFocusRequest) return;
    setHighlightChannels(true);
    window.requestAnimationFrame(() => {
      channelSettingsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    const timeout = window.setTimeout(() => setHighlightChannels(false), 1800);
    return () => window.clearTimeout(timeout);
  }, [props.channelFocusRequest]);
  function focusChannels() {
    setHighlightChannels(true);
    channelSettingsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    window.setTimeout(() => setHighlightChannels(false), 1800);
  }
  const normalizedPhoneStatus = props.phoneNumberStatus?.toLowerCase();
  const phoneConfigured = Boolean(props.phoneNumber)
    && (!normalizedPhoneStatus || normalizedPhoneStatus === "active"
      || normalizedPhoneStatus === "success" || normalizedPhoneStatus === "legacy");
  const channelConfigured = phoneConfigured || props.webVoiceEnabled
    || (props.whatsappEnabled && Boolean(props.whatsappPhoneNumberId.trim()));
  const widgetEmbedCode = props.webVoicePublicId
    ? `<script src="https://YOUR_SAUTI_DOMAIN/sauti-widget.js" data-base-url="https://YOUR_SAUTI_DOMAIN" data-agent="${props.webVoicePublicId}" data-lang="${widgetLanguage}" data-color="${widgetColor}" data-position="${widgetPosition}" data-label="${widgetLabel.replaceAll("\"", "&quot;")}" defer></script>`
    : "";
  const customLanguages = props.supportedLanguages.filter((code) => !LANGUAGE_OPTIONS.some(([known]) => known === code));
  const languageCodeValid = /^[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*$/.test(languageCodeToAdd.trim());

  function toggleLanguage(code: string) {
    if (code === props.language) return;
    props.onSupportedLanguages(props.supportedLanguages.includes(code)
      ? props.supportedLanguages.filter((item) => item !== code)
      : [...props.supportedLanguages, code]);
  }

  function addLanguage() {
    if (!languageCodeValid) return;
    props.onSupportedLanguages(Array.from(new Set([...props.supportedLanguages, languageCodeToAdd.trim()])));
    setLanguageCodeToAdd("");
  }

  return (
    <StudioSection eyebrow="Main settings" title="Identity and call setup" description="Configure the essentials callers experience on every conversation.">
      {!channelConfigured && (
        <div className="pb-6">
          <Note
            tone="warning"
            icon={<RadioTower />}
            title="No customer channel yet"
            action={<Button variant="secondary" size="xs" onClick={focusChannels}><Plus /> Add channel</Button>}
          >
            Turn on Web Voice or WhatsApp, or choose a phone number, so customers can reach this agent.
          </Note>
        </div>
      )}

      <StudioGroup title="Agent profile" description="How this agent is identified inside your workspace.">
        <FieldGrid>
          <Field label="Agent name" htmlFor="agent-name">
            <StudioInput id="agent-name" value={props.name} onChange={(event) => props.onName(event.target.value)} placeholder="e.g. Front desk" />
          </Field>
          <Field label="Short description" htmlFor="agent-description">
            <StudioInput id="agent-description" maxLength={500} value={props.description} onChange={(event) => props.onDescription(event.target.value)} placeholder="What this agent handles" />
          </Field>
        </FieldGrid>
      </StudioGroup>

      <StudioGroup title="Call defaults" description="The language and local time the agent uses on every conversation.">
        <FieldGrid>
          <Field label="Primary language" hint="Callers are greeted in this language.">
            <Select
              size="md"
              className={studioSelectClass}
              ariaLabel="Primary language"
              icon={<Languages />}
              value={props.language}
              onValueChange={props.onLanguage}
              options={Array.from(new Set([...LANGUAGE_OPTIONS.map(([value]) => value), ...props.supportedLanguages]))
                .map((value) => ({ value, label: languageName(value) }))}
            />
          </Field>
          <Field label="Timezone" hint="Used for opening hours and bookings.">
            <Select
              size="md"
              className={studioSelectClass}
              ariaLabel="Agent timezone"
              icon={<Clock3 />}
              searchable
              searchPlaceholder="Find a city or timezone"
              value={props.timezone}
              onValueChange={props.onTimezone}
              options={TIMEZONE_OPTIONS}
            />
          </Field>
        </FieldGrid>
      </StudioGroup>

      <StudioGroup
        id="agent-channel-settings"
        groupRef={channelSettingsRef}
        highlight={highlightChannels}
        title="Live channels"
        description="Customers can reach this agent through any combination of these."
      >
        <RowList>
          <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3 pb-3.5">
            <div className="min-w-0">
              <span className="flex flex-wrap items-center gap-2 text-[0.88rem] font-medium text-ink">
                Phone calls
                {props.phoneNumber ? (
                  <Badge tone={phoneConfigured ? "success" : "warning"} dot>{readablePhoneStatus(props.phoneNumberStatus)}</Badge>
                ) : null}
              </span>
              <span className="mt-0.5 block text-[0.78rem] leading-relaxed text-fg-muted">
                {props.phoneNumber
                  ? `${props.phoneNumber} · ${readableProvider(props.phoneNumberProvider)}`
                  : "Pick a local business number. You confirm the price before anything is bought."}
              </span>
            </div>
            <div className="flex shrink-0 gap-2">
              {props.phoneNumber && props.phoneNumberStatus !== "active" && props.onRefreshPhone ? (
                <Button variant="ghost" size="xs" onClick={props.onRefreshPhone}><RefreshCw /> Refresh</Button>
              ) : null}
              <Button variant="secondary" size="xs" disabled={!props.onAssignPhone} onClick={props.onAssignPhone}>
                <Phone /> {props.phoneNumber ? "Change number" : props.onAssignPhone ? "Choose number" : "Save agent first"}
              </Button>
            </div>
          </div>

          <SwitchRow
            title="Web Voice"
            description="Visitors talk to this agent from your website, without a phone call."
            checked={props.webVoiceEnabled}
            onChange={props.onWebVoiceEnabled}
          >
            <RowList>
              <SwitchRow
                title="Ask for microphone consent"
                description="Visitors accept microphone use before the conversation starts."
                checked={props.webVoiceRequireConsent}
                onChange={props.onWebVoiceRequireConsent}
              />
            </RowList>
            <Field label="Allowed websites" htmlFor="web-voice-origins" hint="One address per line. Leave empty while you test the hosted page.">
              <Textarea
                id="web-voice-origins"
                rows={3}
                className="min-h-0 font-mono text-[0.82rem]"
                value={props.webVoiceAllowedOrigins}
                onChange={(event) => props.onWebVoiceAllowedOrigins(event.target.value)}
                placeholder={"https://example.com\nhttps://booking.example.com"}
              />
            </Field>
            {props.webVoicePublicId ? (
              <>
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--radius-control)] border border-line bg-surface px-3.5 py-2.5">
                  <div className="min-w-0">
                    <p className="m-0 text-[0.74rem] text-fg-muted">Hosted call page</p>
                    <code className="block truncate font-mono text-[0.82rem] text-ink">/call/{props.webVoicePublicId}</code>
                  </div>
                  <Button asChild variant="secondary" size="xs">
                    <Link href={`/call/${props.webVoicePublicId}`} target="_blank">Open page <ExternalLink /></Link>
                  </Button>
                </div>
                <FieldGrid>
                  <Field label="Button label" htmlFor="widget-label">
                    <StudioInput id="widget-label" maxLength={40} value={widgetLabel} onChange={(event) => setWidgetLabel(event.target.value)} />
                  </Field>
                  <Field label="Widget language">
                    <Select size="md" className={studioSelectClass} ariaLabel="Widget language" value={widgetLanguage} onValueChange={setWidgetLanguage}
                      options={props.supportedLanguages.map((code) => ({ value: code, label: languageName(code) }))} />
                  </Field>
                  <Field label="Accent colour" htmlFor="widget-color">
                    <div className="flex h-10 items-center gap-2.5 rounded-[var(--radius-control)] border border-line-strong bg-tint/[0.03] px-2">
                      <input
                        id="widget-color"
                        type="color"
                        value={widgetColor}
                        onChange={(event) => setWidgetColor(event.target.value)}
                        className="size-7 cursor-pointer rounded-[5px] border-0 bg-transparent p-0"
                      />
                      <code className="font-mono text-[0.82rem] uppercase text-fg-soft">{widgetColor}</code>
                    </div>
                  </Field>
                  <Field label="Position">
                    <Select size="md" className={studioSelectClass} ariaLabel="Widget position" value={widgetPosition} onValueChange={(value) => setWidgetPosition(value as "right" | "left")}
                      options={[{ value: "right", label: "Bottom right" }, { value: "left", label: "Bottom left" }]} />
                  </Field>
                </FieldGrid>
                <Field
                  label="Embed code"
                  hint="Paste this just before the closing </body> tag on your site."
                  badge={(
                    <Button variant="ghost" size="xs" className="-my-1 h-7" onClick={() => void navigator.clipboard.writeText(widgetEmbedCode).then(() => toast.success("Embed code copied"))}>
                      <Copy /> Copy
                    </Button>
                  )}
                >
                  <pre className="m-0 max-h-32 overflow-auto whitespace-pre-wrap break-all rounded-[var(--radius-control)] border border-line bg-tint/[0.04] p-3 font-mono text-[0.74rem] leading-relaxed text-fg-soft">{widgetEmbedCode}</pre>
                </Field>
              </>
            ) : (
              <p className="m-0 text-[0.78rem] text-fg-muted">Save the agent to get its hosted page and embed code.</p>
            )}
          </SwitchRow>

          <SwitchRow
            title="WhatsApp"
            description="Answer customer WhatsApp messages and voice notes. Your team can take over any conversation from the inbox."
            checked={props.whatsappEnabled}
            onChange={props.onWhatsappEnabled}
          >
            <Field label="Meta phone number ID" htmlFor="whatsapp-number-id" hint="The ID shown in WhatsApp Manager, not the phone number itself.">
              <StudioInput
                id="whatsapp-number-id"
                inputMode="numeric"
                className="font-mono"
                value={props.whatsappPhoneNumberId}
                onChange={(event) => props.onWhatsappPhoneNumberId(event.target.value.replace(/\D/g, ""))}
                placeholder="123456789012345"
              />
            </Field>
            <p className="m-0 text-[0.78rem] leading-relaxed text-fg-muted">
              Set the Meta webhook to <code className="rounded-[4px] bg-tint/[0.06] px-1 py-0.5 font-mono text-[0.76rem] text-fg-soft">https://YOUR_BACKEND/webhooks/whatsapp</code>.
            </p>
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup
        title="Caller languages"
        description="The agent understands and replies in these languages. The primary language is always on."
        action={<Meta>{props.supportedLanguages.length} on</Meta>}
      >
        <ChipGroup label="Supported languages">
          {LANGUAGE_OPTIONS.map(([code, label]) => (
            <Chip
              key={code}
              selected={props.supportedLanguages.includes(code)}
              locked={code === props.language}
              onClick={() => toggleLanguage(code)}
              trailing={code === props.language ? <span className="text-[0.72rem] font-normal text-fg-muted">Primary</span> : undefined}
            >
              {label}
            </Chip>
          ))}
          {customLanguages.map((code) => (
            <Chip
              key={code}
              selected
              locked={code === props.language}
              onClick={() => toggleLanguage(code)}
              trailing={code !== props.language ? <X className="text-fg-muted" aria-label={`Remove ${code}`} /> : undefined}
            >
              {languageName(code)}
            </Chip>
          ))}
        </ChipGroup>
        <Field label="Add another language" htmlFor="language-code" hint="Use a language code, for example nl for Dutch or fr-CA for Canadian French.">
          <div className="flex gap-2">
            <StudioInput
              id="language-code"
              className="font-mono"
              value={languageCodeToAdd}
              onChange={(event) => setLanguageCodeToAdd(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addLanguage(); } }}
              placeholder="nl"
            />
            <Button variant="secondary" size="sm" className="h-10 shrink-0" disabled={!languageCodeValid} onClick={addLanguage}><Plus /> Add</Button>
          </div>
        </Field>
      </StudioGroup>

      <StudioGroup title="Bookings" description="Let the agent check your calendar and book appointments during the call.">
        <RowList>
          <SwitchRow
            title="Appointment booking"
            description="Check availability and create bookings while the caller is on the line."
            checked={props.bookingEnabled}
            onChange={props.onBooking}
          >
            <BookingDurationSetting value={props.defaultBookingDurationMinutes} onChange={props.onDefaultBookingDurationMinutes} />
            <BookingFieldPicker fields={props.bookingRequiredFields} onChange={props.onBookingRequiredFields} suggestions={props.bookingFieldSuggestions} />
            <Field label="Notify the business owner" hint="Where new bookings are announced.">
              <ChipGroup label="Booking notifications">
                {[{ value: "dashboard", label: "Dashboard" }, { value: "email", label: "Email" }].map((channel) => {
                  const selected = props.bookingNotificationChannels.includes(channel.value);
                  return (
                    <Chip
                      key={channel.value}
                      selected={selected}
                      onClick={() => props.onBookingNotificationChannels(selected
                        ? props.bookingNotificationChannels.filter((item) => item !== channel.value)
                        : [...props.bookingNotificationChannels, channel.value])}
                    >
                      {channel.label}
                    </Chip>
                  );
                })}
              </ChipGroup>
            </Field>
            {props.bookingNotificationChannels.includes("email") && (
              <Field label="Notification email" htmlFor="booking-email" badge={<Meta>Optional</Meta>} hint="Leave blank to email the workspace owner.">
                <StudioInput
                  id="booking-email"
                  inputMode="email"
                  type="email"
                  value={props.bookingNotificationRecipient}
                  onChange={(event) => props.onBookingNotificationRecipient(event.target.value)}
                  placeholder="owner@business.com"
                />
              </Field>
            )}
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup title="Privacy and call records" description="What Sauti keeps after each call.">
        <RowList>
          <SwitchRow title="Save transcripts" description="Keep a written record of each call for review and analytics." checked={props.saveTranscript} onChange={props.onSaveTranscript} />
          <SwitchRow
            title="Record phone calls"
            description="Keep audio of both sides. Make sure your greeting gives any consent notice your callers’ location requires."
            checked={props.recordCalls}
            onChange={props.onRecordCalls}
          />
        </RowList>
      </StudioGroup>

      <StudioGroup title="Greeting" description="The first thing callers hear when the agent answers." action={<Meta>{props.greeting.length} characters</Meta>}>
        <Textarea
          aria-label="Greeting"
          rows={3}
          className="min-h-0 text-[0.9rem]"
          value={props.greeting}
          onChange={(event) => props.onGreeting(event.target.value)}
          placeholder="Hello, you’ve reached {{business_name}}. How can I help?"
        />
      </StudioGroup>
    </StudioSection>
  );
}


export function BookingDurationSetting({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return (
    <Field label="Standard appointment length" hint="Used to find free slots, unless the caller asks for a different length.">
      <Select
        size="md"
        className={cn(studioSelectClass, "sm:max-w-[calc(50%-0.5rem)]")}
        ariaLabel="Standard booking duration"
        icon={<Clock3 />}
        value={String(value)}
        onValueChange={(next) => onChange(Number(next))}
        options={bookingDurationOptions(value).map((minutes) => ({ value: String(minutes), label: bookingDurationLabel(minutes) }))}
      />
    </Field>
  );
}

export function BookingFieldPicker({
  fields,
  suggestions,
  onChange,
}: {
  fields: string[];
  suggestions: string[];
  onChange: (value: string[]) => void;
}) {
  const [customField, setCustomField] = useState("");
  const [showAll, setShowAll] = useState(false);
  const normalizedFields = Array.from(new Set([...DEFAULT_BOOKING_FIELDS, ...fields]));
  const suggestedFields = Array.from(new Set(suggestions)).filter((field) => !CORE_BOOKING_FIELDS.has(field));
  const moreFields = Array.from(new Set([...BOOKING_FIELD_CATALOG, ...fields]))
    .filter((field) => !CORE_BOOKING_FIELDS.has(field) && !suggestedFields.includes(field));
  const atLimit = normalizedFields.length >= 25;
  const normalizedCustom = normalizeBookingField(customField);

  function toggleField(field: string) {
    if (CORE_BOOKING_FIELDS.has(field)) return;
    onChange(normalizedFields.includes(field)
      ? normalizedFields.filter((item) => item !== field)
      : [...normalizedFields, field]);
  }

  function addCustomField() {
    if (!normalizedCustom) return;
    if (!normalizedFields.includes(normalizedCustom)) onChange([...normalizedFields, normalizedCustom]);
    setCustomField("");
  }

  const chip = (field: string) => {
    const selected = normalizedFields.includes(field);
    return (
      <Chip key={field} selected={selected} disabled={atLimit && !selected} onClick={() => toggleField(field)}>
        {bookingFieldLabel(field)}
      </Chip>
    );
  };

  return (
    <div className="grid gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="m-0 text-[0.84rem] font-medium text-ink">Details to collect</p>
          <p className="m-0 mt-0.5 text-[0.76rem] leading-relaxed text-fg-muted">The agent asks for every selected detail before it books.</p>
        </div>
        <Meta>{normalizedFields.length} of 25</Meta>
      </div>
      <div className="grid gap-2">
        <p className="m-0 text-[0.74rem] font-medium text-fg-muted">Always collected</p>
        <ChipGroup label="Always collected">
          {DEFAULT_BOOKING_FIELDS.map((field) => <Chip key={field} selected locked>{bookingFieldLabel(field)}</Chip>)}
        </ChipGroup>
      </div>
      {suggestedFields.length > 0 && (
        <div className="grid gap-2">
          <p className="m-0 text-[0.74rem] font-medium text-fg-muted">Suggested for your business</p>
          <ChipGroup label="Suggested details">{suggestedFields.map(chip)}</ChipGroup>
        </div>
      )}
      {moreFields.length > 0 && (
        <div className="grid gap-2">
          {showAll ? <ChipGroup label="More details">{moreFields.map(chip)}</ChipGroup> : null}
          <Button variant="ghost" size="xs" className="-ml-3 w-fit" onClick={() => setShowAll((current) => !current)}>
            <ChevronDown className={cn("transition-transform", showAll && "rotate-180")} />
            {showAll ? "Show fewer details" : `Show ${moreFields.length} more details`}
          </Button>
        </div>
      )}
      <Field
        label="Add your own"
        htmlFor="custom-booking-field"
        hint={normalizedCustom ? <>Saved as <code className="font-mono text-fg-soft">{normalizedCustom}</code></> : "For example, insurance member number."}
      >
        <div className="flex gap-2">
          <StudioInput
            id="custom-booking-field"
            value={customField}
            onChange={(event) => setCustomField(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addCustomField(); } }}
            placeholder="Insurance member number"
          />
          <Button variant="secondary" size="sm" className="h-10 shrink-0" disabled={atLimit || !normalizedCustom} onClick={addCustomField}><Plus /> Add</Button>
        </div>
      </Field>
    </div>
  );
}

export function normalizeBookingField(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^[^a-z]+|_+$/g, "").slice(0, 64);
}

export function bookingFieldLabel(field: string) {
  const aliases: Record<string, string> = {
    caller_name: "Customer name",
    caller_phone: "Phone number",
    caller_email: "Email address",
    service_type: "Service",
    appointment_at: "Date and time",
    patient_date_of_birth: "Patient date of birth",
  };
  return aliases[field] ?? field.split("_").map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
}
