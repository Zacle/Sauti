"use client";

import styles from "./AgentVariablesPage.module.css";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, CalendarCheck, Check, CircleAlert, Clock3, Globe2, LoaderCircle, PhoneCall, Route, Sparkles, Trash2 } from "lucide-react";
import { activateAgent, createAgentVariable, deleteAgent, getAgent, getAgentReadiness, listAgentVariables, updateAgentVariables } from "@/lib/api/agents";
import type { Agent, AgentReadiness, AgentVariable, CreateAgentVariable } from "@/types/api";
import { AddVariableForm } from "./AddVariableForm";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";

const structuredChoices = {
  calendar_provider: {
    title: "Calendar destination",
    description: "Choose where confirmed bookings should be sent. Credentials are connected separately.",
    icon: CalendarCheck,
    options: [
      ["Google Calendar", "Sync availability and events after connecting Google."],
      ["Calendly", "Use your Calendly event types after connecting it."],
      ["Custom webhook", "Send booking requests to your own scheduling API."],
      ["Set up later", "Keep using test availability while designing the agent."],
    ],
  },
  routing_policy: {
    title: "Meeting routing",
    description: "Routing decides which calendar receives a booking when an agent can use more than one.",
    icon: Route,
    options: [
      ["Fixed calendar", "Send every booking to one selected calendar."],
      ["Set up later", "Choose routing after connecting your calendar or team."],
    ],
  },
} as const;

export function AgentVariablesPage({ agentId }: { agentId: string }) {
  const router = useRouter();
  const [agent, setAgent] = useState<Agent | null>(null);
  const [variables, setVariables] = useState<AgentVariable[]>([]);
  const [readiness, setReadiness] = useState<AgentReadiness | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [showDelete, setShowDelete] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    Promise.all([getAgent(agentId), listAgentVariables(agentId), getAgentReadiness(agentId)])
      .then(([loadedAgent, loadedVariables, loadedReadiness]) => {
        setAgent(loadedAgent);
        setVariables(loadedVariables);
        setReadiness(loadedReadiness);
        setValues(Object.fromEntries(loadedVariables.map((variable) => [variable.key, variable.value])));
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load this agent."))
      .finally(() => setLoading(false));
  }, [agentId]);

  const missingRequired = useMemo(
    () => variables.filter((variable) => variable.required && !values[variable.key]?.trim()),
    [variables, values],
  );

  async function save() {
    setSaving(true);
    setSaved(false);
    setError("");
    try {
      const updated = await updateAgentVariables(agentId, values);
      setVariables(updated);
      setReadiness(await getAgentReadiness(agentId));
      setSaved(true);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save business details.");
    } finally {
      setSaving(false);
    }
  }

  async function activate() {
    if (missingRequired.length) return;
    setSaving(true);
    setError("");
    try {
      await updateAgentVariables(agentId, values);
      setAgent(await activateAgent(agentId));
      setReadiness(await getAgentReadiness(agentId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to activate this agent.");
    } finally {
      setSaving(false);
    }
  }

  async function addVariable(variable: CreateAgentVariable) {
    setError("");
    const created = await createAgentVariable(agentId, variable);
    setVariables((current) => [...current, created]);
    setValues((current) => ({ ...current, [created.key]: created.value }));
    setSaved(true);
  }

  async function removeAgent() {
    setSaving(true);
    setDeleteError("");
    try {
      await deleteAgent(agentId);
      router.replace("/agents");
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : "Unable to delete this agent.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className={styles.loading}><LoaderCircle className="spin" /> Loading agent studio…</div>;
  if (!agent) return <div className="dashboard-error-state"><CircleAlert /><h1>Agent unavailable</h1><p>{error}</p><Link href="/agents">Back to agents</Link></div>;

  return (
    <div className={styles.page}>
      <header>
        <Link href="/agents"><ArrowLeft size={17} /> Agents</Link>
        <div><span>{agent.name.slice(0, 1).toUpperCase()}</span><section><small>Agent setup</small><h1>{agent.name}</h1></section></div>
        <div className={styles.headerActions}>
          <button className={styles.deleteButton} disabled={saving} onClick={() => {
            setDeleteError("");
            setShowDelete(true);
          }} type="button"><Trash2 size={16} /> Delete</button>
          <button className="console-primary-button" disabled={saving || agent.active || !readiness?.readyToActivate} type="button" onClick={activate}>
            {agent.active ? <><Check size={16} /> Active</> : "Activate agent"}
          </button>
        </div>
      </header>

      <main>
        <div className={styles.heading}><Sparkles size={19} /><div><span>Personalisation</span><h2>Your business details</h2><p>These values are inserted into the agent prompt at call time. Changes apply to the next call without reactivating the agent.</p></div></div>
        <section className={styles.setupSummary}>
          <div className={styles.selectionSummary}>
            <div><span>Business type</span><strong>{agent.businessType ?? "Custom agent"}</strong></div>
            <div><span>Primary use case</span><strong>{agent.primaryUseCase ?? (agent.bookingEnabled ? "Appointment booking" : "Customer calls")}</strong></div>
            <div><span>Timezone</span><strong><Clock3 size={14} /> {agent.timezone}</strong></div>
            <div><span>Languages</span><strong><Globe2 size={14} /> {agent.supportedLanguages.join(", ")}</strong></div>
            <div><span>Voice</span><strong>{agent.voiceProfile ?? (agent.ttsVoiceId ? "Selected voice" : "Provider default")}</strong></div>
          </div>
          {readiness && (
            <div className={styles.readiness}>
              <header><div><span>Activation checklist</span><h3>{readiness.active ? "Agent is active" : readiness.readyToActivate ? "Ready to activate" : "Finish setup"}</h3></div><i>{[readiness.businessDetailsComplete, readiness.calendarConfigured, readiness.channelConfigured, readiness.active].filter(Boolean).length}/4</i></header>
              <SetupCheck label="Business details" done={readiness.businessDetailsComplete} detail={readiness.businessDetailsComplete ? "Required values are complete" : readiness.missingRequiredVariables.join(", ")} />
              <SetupCheck label="Calendar connection" done={readiness.calendarConfigured} detail={readiness.calendarRequired ? (readiness.calendarConfigured ? "Booking destination connected" : "Required for appointment booking") : "Not required for this agent"} href={!readiness.calendarConfigured ? `/dashboard/integrations?provider=google&agentId=${agentId}` : undefined} />
              <SetupCheck
                label="Live channel"
                done={readiness.channelConfigured}
                detail={readiness.webVoiceConfigured
                  ? "Web Voice enabled"
                  : readiness.whatsappConfigured ? "WhatsApp connected"
                  : readiness.phoneNumberConfigured ? agent.twilioPhoneNumber ?? "Phone connected" : "Enable Web Voice or connect a phone number"}
                href={!readiness.channelConfigured ? `/agents/${agentId}` : undefined}
                action={!readiness.channelConfigured ? <button disabled={saving} type="button" onClick={() => router.push(`/agents/${agentId}`)}><PhoneCall size={14} /> Choose channel</button> : undefined}
              />
              <SetupCheck
                label="Activation"
                done={readiness.active}
                detail={readiness.active ? "Agent setup is complete" : readiness.readyToActivate ? "All requirements are complete. Activate the agent to finish setup." : "Complete the steps above first"}
                action={!readiness.active && readiness.readyToActivate
                  ? <button disabled={saving} type="button" onClick={() => void activate()}><Check size={14} /> Activate agent</button>
                  : undefined}
              />
            </div>
          )}
        </section>
        <div className={styles.add}><AddVariableForm onAdd={addVariable} /></div>
        {missingRequired.length > 0 && (
          <div className={styles.warning}><CircleAlert size={18} /><div><strong>{missingRequired.length} required fields remain</strong><p>{missingRequired.map((variable) => variable.label).join(", ")}</p></div></div>
        )}
        {variables.length ? (
          <div className={styles.fields}>
            {variables.map((variable) => {
              const filled = Boolean(values[variable.key]?.trim());
              const structured = structuredChoices[variable.key as keyof typeof structuredChoices];
              if (structured) {
                return (
                  <ChoiceSetting
                    config={structured}
                    key={variable.key}
                    value={values[variable.key] ?? ""}
                    onChange={(value) => setValues((current) => ({ ...current, [variable.key]: value }))}
                  />
                );
              }
              return <label key={variable.key}><span>{variable.label}<i className={filled ? styles.filled : ""}>{filled ? "Complete" : variable.required ? "Required" : "Optional"}</i></span><input value={values[variable.key] ?? ""} onChange={(event) => setValues((current) => ({ ...current, [variable.key]: event.target.value }))} /><small>{variable.description}</small></label>;
            })}
          </div>
        ) : <div className={styles.empty}>This agent has no template variables.</div>}
        {error && <div className="form-alert error">{error}</div>}
        <footer><span>{saved ? "Changes saved." : "Updates apply to future calls."}</span><button className="console-primary-button" disabled={saving || !variables.length} type="button" onClick={save}>{saving && <LoaderCircle className="spin" size={16} />} Save changes</button></footer>
      </main>
      {showDelete && (
        <DeleteAgentDialog
          agent={agent}
          busy={saving}
          error={deleteError}
          onCancel={() => !saving && setShowDelete(false)}
          onConfirm={() => void removeAgent()}
        />
      )}
    </div>
  );
}

function SetupCheck({
  label,
  done,
  detail,
  href,
  action,
}: {
  label: string;
  done: boolean;
  detail: string;
  href?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className={`${styles.setupCheck} ${done ? styles.done : ""}`}>
      <span>{done && <Check size={13} />}</span>
      <div><strong>{label}</strong><small>{detail}</small></div>
      {href && <Link href={href}>Configure</Link>}
      {action}
    </div>
  );
}

function ChoiceSetting({
  config,
  value,
  onChange,
}: {
  config: typeof structuredChoices[keyof typeof structuredChoices];
  value: string;
  onChange: (value: string) => void;
}) {
  const Icon = config.icon;
  return (
    <section className={styles.choiceSetting}>
      <header>
        <span><Icon size={18} /></span>
        <div><h3>{config.title}</h3><p>{config.description}</p></div>
      </header>
      <div className={styles.choiceGrid}>
        {config.options.map(([option, description]) => {
          const selected = value === option;
          return (
            <button
              className={selected ? styles.selectedChoice : ""}
              key={option}
              onClick={() => onChange(option)}
              type="button"
            >
              <span className={styles.choiceRadio}>{selected && <Check size={13} />}</span>
              <span><strong>{option}</strong><small>{description}</small></span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
