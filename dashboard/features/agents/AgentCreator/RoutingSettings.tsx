"use client";


import { Textarea } from "@/components/ui/field";
import { Checkbox, RadioCard, RadioGroup, Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import {
Field,
Meta,
RowList,
StudioGroup,
StudioInput,
StudioSection,
SwitchRow
} from "./studio-kit";

import { AfterHoursBehavior, DayHours, OPERATING_WEEK_DAYS, WeekDayKey, WeeklyHours } from "./studio-model";

export function RoutingSettings(props: {
  transferEnabled: boolean;
  transferNumber: string;
  escalationPhrases: string;
  operatingHours: string;
  timezone: string;
  afterHoursBehavior: AfterHoursBehavior;
  afterHoursMessage: string;
  onTransferEnabled: (value: boolean) => void;
  onTransferNumber: (value: string) => void;
  onEscalationPhrases: (value: string) => void;
  onOperatingHours: (value: string) => void;
  onAfterHoursBehavior: (value: AfterHoursBehavior) => void;
  onAfterHoursMessage: (value: string) => void;
}) {
  const alwaysOpen = props.operatingHours === "always" || props.operatingHours === "workspace";
  return (
    <StudioSection eyebrow="Routing" title="Escalation and availability" description="Control when calls stay with the agent and when they move to your team.">
      <StudioGroup title="Human transfer" description="Hand the call to your team when the caller asks for a person, or the agent can’t safely continue.">
        <RowList>
          <SwitchRow title="Allow live transfer" description="The agent tells the caller it’s connecting them, then transfers the call." checked={props.transferEnabled} onChange={props.onTransferEnabled}>
            <Field label="Transfer to" htmlFor="transfer-number" hint="Include the country code.">
              <StudioInput id="transfer-number" inputMode="tel" className="sm:max-w-[calc(50%-0.5rem)]" value={props.transferNumber} onChange={(event) => props.onTransferNumber(event.target.value)} placeholder="+254 7XX XXX XXX" />
            </Field>
            <Field label="Phrases that trigger a transfer" htmlFor="escalation-phrases" hint="Separate phrases with commas.">
              <Textarea id="escalation-phrases" rows={3} className="min-h-0 text-[0.88rem]" value={props.escalationPhrases} onChange={(event) => props.onEscalationPhrases(event.target.value)} placeholder="speak to a person, manager, complaint" />
            </Field>
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup
        title="Operating hours"
        description={<>Checked against <span className="font-medium text-fg-soft">{props.timezone}</span>. Overnight ranges such as 20:00–02:00 work.</>}
      >
        <WeeklyHoursEditor value={props.operatingHours} onChange={props.onOperatingHours} />
      </StudioGroup>

      <StudioGroup
        title="Outside operating hours"
        description={alwaysOpen ? "Only used once you set a weekly schedule above." : "What callers experience when your team is closed."}
      >
        <RadioGroup
          aria-label="After-hours behaviour"
          value={props.afterHoursBehavior}
          onValueChange={(value) => props.onAfterHoursBehavior(value as AfterHoursBehavior)}
          className="sm:grid-cols-3"
        >
          {([
            ["answer", "Answer normally", "The agent and its tools work as usual."],
            ["take_message", "Take a message", "Collect the caller’s name, number and reason for calling."],
            ["closed", "Say you’re closed", "Play a short message, thank the caller and end the call."],
          ] as Array<[AfterHoursBehavior, string, string]>).map(([value, title, detail]) => (
            <RadioCard key={value} value={value} title={title} description={detail} />
          ))}
        </RadioGroup>
        {props.afterHoursBehavior !== "answer" && (
          <Field
            label="What callers hear"
            htmlFor="after-hours-message"
            badge={<Meta>{props.afterHoursMessage.length} / 1000</Meta>}
            hint="Leave empty to use Sauti’s default message in the caller’s language."
          >
            <Textarea
              id="after-hours-message"
              rows={3}
              maxLength={1000}
              className="min-h-0 text-[0.88rem]"
              value={props.afterHoursMessage}
              onChange={(event) => props.onAfterHoursMessage(event.target.value)}
              placeholder={props.afterHoursBehavior === "closed"
                ? "Thank you for calling. We’re closed right now. Please call again during business hours."
                : "We’re closed right now, but I can take your details and ask the team to call you back."}
            />
          </Field>
        )}
      </StudioGroup>
    </StudioSection>
  );
}

export function WeeklyHoursEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const custom = value !== "always" && value !== "workspace";
  const schedule = parseWeeklyHours(value);

  function setMode(mode: string) {
    onChange(mode === "always" ? "always" : JSON.stringify(schedule));
  }

  function updateDay(day: WeekDayKey, update: Partial<DayHours>) {
    onChange(JSON.stringify({ ...schedule, [day]: { ...schedule[day], ...update } }));
  }

  return (
    <div className="grid gap-4">
      <Tabs value={custom ? "custom" : "always"} onValueChange={setMode}>
        <TabsList aria-label="Operating-hours mode">
          <TabsTrigger value="always">Always open</TabsTrigger>
          <TabsTrigger value="custom">Weekly schedule</TabsTrigger>
        </TabsList>
      </Tabs>
      {custom ? (
        <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">
          {OPERATING_WEEK_DAYS.map(({ key, label }) => {
            const hours = schedule[key];
            return (
              <div className="grid min-h-[3.25rem] grid-cols-[9rem_minmax(0,1fr)] items-center gap-4 px-4 py-2 max-sm:grid-cols-1 max-sm:gap-2" key={key}>
                <label className="flex cursor-pointer items-center gap-2.5 text-[0.86rem] font-medium text-ink">
                  <Checkbox checked={hours.enabled} onCheckedChange={(checked) => updateDay(key, { enabled: checked === true })} aria-label={`Open on ${label}`} />
                  {label}
                </label>
                {hours.enabled ? (
                  <div className="flex items-center gap-2">
                    <StudioInput aria-label={`${label} opening time`} type="time" className="h-9 w-[9rem] tabular-nums" value={hours.start} onChange={(event) => updateDay(key, { start: event.target.value })} />
                    <span className="text-[0.8rem] text-fg-muted">to</span>
                    <StudioInput aria-label={`${label} closing time`} type="time" className="h-9 w-[9rem] tabular-nums" value={hours.end} onChange={(event) => updateDay(key, { end: event.target.value })} />
                  </div>
                ) : <span className="text-[0.84rem] text-fg-muted">Closed</span>}
              </div>
            );
          })}
        </div>
      ) : (
        <p className="m-0 text-[0.82rem] text-fg-muted">The agent answers at any hour, every day. Choose a weekly schedule to set opening times.</p>
      )}
    </div>
  );
}

export function parseWeeklyHours(value: string): WeeklyHours {
  const defaults = Object.fromEntries(OPERATING_WEEK_DAYS.map(({ key }) => [
    key,
    { enabled: !["saturday", "sunday"].includes(key), start: "09:00", end: "17:00" },
  ])) as WeeklyHours;
  if (!value || value === "always" || value === "workspace" || value === "weekdays") return defaults;
  try {
    const parsed = JSON.parse(value) as Partial<WeeklyHours>;
    return Object.fromEntries(OPERATING_WEEK_DAYS.map(({ key }) => [
      key,
      { ...defaults[key], ...(parsed[key] ?? {}) },
    ])) as WeeklyHours;
  } catch {
    return defaults;
  }
}
