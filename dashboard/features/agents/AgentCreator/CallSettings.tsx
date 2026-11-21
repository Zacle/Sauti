"use client";


import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import {
Plus,
ShieldCheck,
Trash2,
X
} from "lucide-react";
import {
Chip,
ChipGroup,
Field,
FieldGrid,
Meta,
Note,
NumberField,
RowList,
SliderField,
StudioGroup,
StudioInput,
StudioSection,
SwitchRow,
studioSelectClass,
} from "./studio-kit";

import { bookingFieldLabel } from "./MainSettings";

export function formatSeconds(total: number) {
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  if (!minutes) return `${seconds} s`;
  return seconds ? `${minutes} min ${seconds} s` : `${minutes} min`;
}

export function CallBehaviorSettings(props: {
  maxDuration: string; endCallOnSilenceSeconds: number; reminderAfterSilenceSeconds: number; maxReminders: number;
  detectVoicemail: boolean; handleCallScreening: boolean; dtmfEnabled: boolean; safetyGuardrails: string[];
  dtmfTerminationKey: "#" | "*"; dtmfInputTimeoutSeconds: number; dtmfMaxDigits: number;
  dtmfDigitMappings: Record<string, string>;
  onMaxDuration: (value: string) => void; onEndCallOnSilenceSeconds: (value: number) => void;
  onReminderAfterSilenceSeconds: (value: number) => void; onMaxReminders: (value: number) => void;
  onDetectVoicemail: (value: boolean) => void; onHandleCallScreening: (value: boolean) => void;
  onDtmfEnabled: (value: boolean) => void; onSafetyGuardrails: (value: string[]) => void;
  onDtmfTerminationKey: (value: "#" | "*") => void;
  onDtmfInputTimeoutSeconds: (value: number) => void; onDtmfMaxDigits: (value: number) => void;
  onDtmfDigitMappings: (value: Record<string, string>) => void;
}) {
  const guardrails = [["medical diagnosis", "Medical diagnosis"], ["legal advice", "Legal advice"], ["financial advice", "Financial advice"]];
  const mappings = Object.entries(props.dtmfDigitMappings);

  function updateMapping(index: number, digits: string, meaning: string) {
    const next = mappings.map(([currentDigits, currentMeaning], currentIndex) => (
      currentIndex === index ? [digits.replace(/[^0-9*#]/g, "").slice(0, props.dtmfMaxDigits), meaning] : [currentDigits, currentMeaning]
    ));
    props.onDtmfDigitMappings(Object.fromEntries(next.filter(([key]) => key)));
  }

  function addMapping() {
    const available = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]
      .find((digit) => !Object.prototype.hasOwnProperty.call(props.dtmfDigitMappings, digit));
    if (available) props.onDtmfDigitMappings({ ...props.dtmfDigitMappings, [available]: "" });
  }

  function removeMapping(digits: string) {
    props.onDtmfDigitMappings(Object.fromEntries(mappings.filter(([key]) => key !== digits)));
  }

  return (
    <StudioSection eyebrow="Call behaviour" title="Silence, screening, and safety" description="Control how the agent handles unattended calls, long pauses, and sensitive topics.">
      <StudioGroup title="Call handling" description="How the agent deals with voicemail, call screeners and keypad presses.">
        <RowList>
          <SwitchRow title="Voicemail detection" description="Hang up when the call reaches a voicemail greeting." checked={props.detectVoicemail} onChange={props.onDetectVoicemail} />
          <SwitchRow title="Call-screen handling" description="Answer screening prompts such as “Who is calling?”" checked={props.handleCallScreening} onChange={props.onHandleCallScreening} />
          <SwitchRow title="Keypad input" description="Understand numbers callers type on their keypad." checked={props.dtmfEnabled} onChange={props.onDtmfEnabled}>
            <FieldGrid columns={3}>
              <Field label="Finish key" hint="Sends the digits straight away.">
                <Select size="md" className={studioSelectClass} ariaLabel="DTMF completion key" value={props.dtmfTerminationKey} onValueChange={(value) => props.onDtmfTerminationKey(value as "#" | "*")}
                  options={[{ value: "#", label: "# Hash" }, { value: "*", label: "* Star" }]} />
              </Field>
              <Field label="Wait for input" hint="Sends after the caller stops typing.">
                <NumberField ariaLabel="Input timeout in seconds" value={props.dtmfInputTimeoutSeconds} min={1} max={30} unit="sec" onChange={props.onDtmfInputTimeoutSeconds} />
              </Field>
              <Field label="Maximum digits" hint="For codes and reference numbers.">
                <NumberField ariaLabel="Maximum digits" value={props.dtmfMaxDigits} min={1} max={32} onChange={props.onDtmfMaxDigits} />
              </Field>
            </FieldGrid>
            <div className="grid gap-3">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="m-0 text-[0.84rem] font-medium text-ink">Keypad menu</p>
                  <p className="m-0 mt-0.5 text-[0.76rem] leading-relaxed text-fg-muted">Tell the agent what each key means, for example “1 = confirm the appointment”.</p>
                </div>
                <Button variant="secondary" size="xs" className="shrink-0" onClick={addMapping} disabled={mappings.length >= 10}><Plus /> Add key</Button>
              </div>
              {mappings.length === 0 ? (
                <p className="m-0 rounded-[var(--radius-control)] border border-dashed border-line-strong px-4 py-5 text-center text-[0.8rem] text-fg-muted">
                  No keys mapped yet. Anything the caller types is still passed to the agent.
                </p>
              ) : (
                <div className="grid gap-2">
                  <div className="grid grid-cols-[5.5rem_minmax(0,1fr)_2rem] gap-2 text-[0.74rem] font-medium text-fg-muted">
                    <span>Keys</span><span>What it means</span><span />
                  </div>
                  {mappings.map(([digits, meaning], index) => (
                    <div className="grid grid-cols-[5.5rem_minmax(0,1fr)_2rem] items-center gap-2" key={`${index}-${digits}`}>
                      <StudioInput aria-label="Keys" inputMode="numeric" className="text-center font-mono" maxLength={props.dtmfMaxDigits} value={digits} onChange={(event) => updateMapping(index, event.target.value, meaning)} placeholder="1" />
                      <StudioInput aria-label="What it means" value={meaning} onChange={(event) => updateMapping(index, digits, event.target.value)} placeholder="Confirm the appointment" />
                      <Button variant="ghost" size="icon-xs" aria-label={`Remove key ${digits}`} className="hover:bg-danger/[0.08] hover:text-danger" onClick={() => removeMapping(digits)}><Trash2 /></Button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup title="Duration and silence" description="Limits that stop calls running on.">
        <SliderField
          label="Maximum call length"
          description="The agent politely wraps up and ends the call at this point."
          value={Number(props.maxDuration)}
          min={1}
          max={60}
          step={1}
          format={(value) => `${value} min`}
          marks={[{ value: 1, label: "1 min" }, { value: 15, label: "15" }, { value: 30, label: "30" }, { value: 45, label: "45" }, { value: 60, label: "60 min" }]}
          onChange={(value) => props.onMaxDuration(String(value))}
        />
        <SliderField
          label="End call after silence"
          description="Hang up when nobody has spoken for this long."
          value={props.endCallOnSilenceSeconds}
          min={60}
          max={900}
          step={30}
          format={formatSeconds}
          marks={[{ value: 60, label: "1 min" }, { value: 300, label: "5" }, { value: 600, label: "10" }, { value: 900, label: "15 min" }]}
          onChange={props.onEndCallOnSilenceSeconds}
        />
        <FieldGrid>
          <Field label="Check in after" hint="The agent asks if the caller is still there.">
            <NumberField ariaLabel="Reminder after silence in seconds" value={props.reminderAfterSilenceSeconds} min={30} max={600} step={5} unit="sec" onChange={props.onReminderAfterSilenceSeconds} />
          </Field>
          <Field label="Check-ins before hanging up" hint="Set 0 to end without checking in.">
            <NumberField ariaLabel="Maximum reminders" value={props.maxReminders} min={0} max={10} onChange={props.onMaxReminders} />
          </Field>
        </FieldGrid>
      </StudioGroup>

      <StudioGroup
        title="Safety guardrails"
        description="Topics the agent politely declines. These rules are added after your prompt."
        action={<Meta>{props.safetyGuardrails.length} on</Meta>}
      >
        <ChipGroup label="Safety guardrails">
          {guardrails.map(([value, label]) => {
            const selected = props.safetyGuardrails.includes(value);
            return (
              <Chip key={value} selected={selected} onClick={() => props.onSafetyGuardrails(selected ? props.safetyGuardrails.filter((item) => item !== value) : [...props.safetyGuardrails, value])}>
                {label}
              </Chip>
            );
          })}
        </ChipGroup>
      </StudioGroup>
    </StudioSection>
  );
}

export const POST_CALL_FIELDS: Array<[string, string, string]> = [
  ["summary", "Call summary", "A short paragraph describing what happened on the call."],
  ["successful", "Call outcome", "Whether the caller got what they needed."],
  ["sentiment", "Caller sentiment", "Positive, neutral or negative, from the caller’s words and tone."],
  ["intent", "Reason for calling", "The main thing the caller wanted."],
];

export function PostCallSettings({ fields, onFields }: { fields: string[]; onFields: (fields: string[]) => void }) {
  const extraFields = fields.filter((field) => !POST_CALL_FIELDS.some(([value]) => value === field));
  return (
    <StudioSection eyebrow="Post-call" title="Structured call analysis" description="Choose the fields generated after the call for reporting and downstream workflows.">
      <StudioGroup
        title="Fields to extract"
        description="Filled in from the transcript after every call, then shown on the call and sent to connected tools."
        action={<Meta>{POST_CALL_FIELDS.filter(([value]) => fields.includes(value)).length} of {POST_CALL_FIELDS.length} on</Meta>}
      >
        <RowList>
          {POST_CALL_FIELDS.map(([value, title, description]) => (
            <SwitchRow
              key={value}
              title={title}
              description={description}
              checked={fields.includes(value)}
              onChange={(enabled) => onFields(enabled ? [...fields, value] : fields.filter((item) => item !== value))}
            />
          ))}
        </RowList>
        {extraFields.length > 0 && (
          <Field label="Other fields" hint="Added for this agent elsewhere. Click one to stop extracting it.">
            <ChipGroup label="Other extracted fields">
              {extraFields.map((field) => (
                <Chip key={field} selected onClick={() => onFields(fields.filter((item) => item !== field))} trailing={<X className="text-fg-muted" />}>
                  {bookingFieldLabel(field)}
                </Chip>
              ))}
            </ChipGroup>
          </Field>
        )}
      </StudioGroup>
      <div className="border-t border-line pt-6">
        <Note icon={<ShieldCheck />} title="Analysis follows your transcript setting">
          These fields are only generated when a transcript is saved. Turn on transcripts in Main settings to use them.
        </Note>
      </div>
    </StudioSection>
  );
}
