"use client";


import { Alert, Textarea } from "@/components/ui/field";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import {
getInstructionsPreview
} from "@/lib/api/agents";
import type { AgentVariableDefinition, InstructionsPreview, ToolRule } from "@/types/api";
import {
CircleAlert,
Eye,
FileText,
PencilLine,
ShieldCheck
} from "lucide-react";
import { useEffect, useState } from "react";
import {
Note,
StudioGroup,
StudioSection
} from "./studio-kit";


export function BehaviorSettings({
  prompt,
  onPrompt,
  template,
  variables,
  values,
  agentId,
  language,
  unsaved,
  savedVersion,
}: {
  prompt: string;
  onPrompt: (value: string) => void;
  template: string;
  variables: AgentVariableDefinition[];
  values: Record<string, string>;
  agentId?: string;
  language: string;
  unsaved: boolean;
  savedVersion: string;
}) {
  const [view, setView] = useState<"edit" | "preview" | "final">(variables.length > 0 ? "preview" : "edit");
  const showPreview = view === "preview" && variables.length > 0;
  const showFinal = view === "final" && Boolean(agentId);
  const hasTabs = variables.length > 0 || Boolean(agentId);
  return (
    <StudioSection eyebrow="Prompt" title="Agent prompt" description="Define what the agent should accomplish and the boundaries it must follow.">
      <StudioGroup
        title="Instructions"
        description={<>Started from <span className="font-medium text-fg-soft">{template}</span>. Use <code className="font-mono text-fg-soft">{"{{variable}}"}</code> to insert business details.</>}
        action={hasTabs ? (
          <Tabs value={view} onValueChange={(next) => setView(next as "edit" | "preview" | "final")}>
            <TabsList aria-label="Prompt view">
              <TabsTrigger value="edit"><PencilLine /> Edit</TabsTrigger>
              {variables.length > 0 && <TabsTrigger value="preview"><Eye /> Preview</TabsTrigger>}
              {agentId && <TabsTrigger value="final"><FileText /> On calls</TabsTrigger>}
            </TabsList>
          </Tabs>
        ) : undefined}
      >
        {showFinal && agentId
          ? <FinalInstructions agentId={agentId} language={language} unsaved={unsaved} savedVersion={savedVersion} />
          : showPreview
          ? <PromptPreview prompt={prompt} values={values} />
          : (
            <Textarea
              aria-label="Agent prompt"
              spellCheck
              className="min-h-[min(64vh,600px)] resize-y font-mono text-[0.84rem] leading-6"
              value={prompt}
              onChange={(event) => onPrompt(event.target.value)}
            />
          )}
        <div className="-mt-2 flex justify-between gap-4 text-[0.74rem] text-fg-muted">
          <span>
            {showFinal
              ? "Exactly what the agent is given on a call, from the last save."
              : showPreview ? "Highlighted text comes from your business details." : "Drag the corner to resize."}
          </span>
          <span className="tabular-nums">{prompt.length.toLocaleString()} characters</span>
        </div>
      </StudioGroup>
      <div className="border-t border-line pt-6">
        <Note tone="signal" icon={<ShieldCheck />} title="Keep instructions testable">
          Spell out the goal, the details to collect, when to use each tool, when to hand over to a person, and the tone to use.
        </Note>
      </div>
    </StudioSection>
  );
}

/** The saved agent's final call instructions, built by the server exactly as they are sent to the voice provider. */
export function FinalInstructions({ agentId, language, unsaved, savedVersion }: {
  agentId: string; language: string; unsaved: boolean; savedVersion: string;
}) {
  const [preview, setPreview] = useState<InstructionsPreview | null>(null);
  const [failure, setFailure] = useState("");
  useEffect(() => {
    let cancelled = false;
    setFailure("");
    getInstructionsPreview(agentId, language)
      .then((result) => { if (!cancelled) setPreview(result); })
      .catch((caught) => {
        if (!cancelled) setFailure(caught instanceof Error ? caught.message : "Couldn’t load the call instructions.");
      });
    return () => { cancelled = true; };
  }, [agentId, language, savedVersion]);

  if (failure) return <Alert tone="error">{failure}</Alert>;
  if (!preview) {
    return <div className="min-h-[min(64vh,600px)] animate-pulse rounded-[var(--radius-control)] border border-line bg-tint/[0.03]" aria-label="Loading call instructions" />;
  }
  return (
    <div className="grid gap-3">
      {unsaved && (
        <Note tone="warning" icon={<CircleAlert />} title="You have unsaved changes">
          This shows the last saved version. Save to see your edits here.
        </Note>
      )}
      {preview.warnings.length > 0 && (
        <Note tone="warning" icon={<CircleAlert />} title={`${preview.warnings.length} ${preview.warnings.length === 1 ? "detail needs" : "details need"} attention`}>
          <ul className="m-0 grid gap-1 pl-4">
            {preview.warnings.map((warning) => <li key={`${warning.kind}-${warning.key}`}>{warning.message}</li>)}
          </ul>
        </Note>
      )}
      <div className="grid gap-1.5">
        <span className="text-[0.74rem] font-medium text-fg-muted">Opening line</span>
        <p className="m-0 rounded-[var(--radius-control)] border border-line bg-tint/[0.03] p-3 text-[0.88rem] text-fg">{preview.greeting || "Generated from the agent’s greeting direction."}</p>
      </div>
      <pre className="m-0 max-h-[min(64vh,600px)] overflow-auto whitespace-pre-wrap rounded-[var(--radius-control)] border border-line bg-tint/[0.03] p-3.5 font-mono text-[0.8rem] leading-6 text-fg">
        {preview.instructions}
      </pre>
      <ToolRules rules={preview.toolRules ?? preview.tools.map((name) => ({ name, effect: "read_only", confirmation: "none" }))} />
    </div>
  );
}

const EFFECT_LABEL: Record<ToolRule["effect"], string> = {
  read_only: "Looks things up",
  data_write: "Changes records",
  external_communication: "Sends a message",
  financial: "Requests a payment",
  transfer: "Transfers the call",
  terminal: "Ends the call",
};

const CONFIRMATION_LABEL: Record<ToolRule["confirmation"], { label: string; dot: string }> = {
  none: { label: "No confirmation needed", dot: "bg-line-strong" },
  explicit: { label: "Asks the caller first", dot: "bg-success" },
  verified_review: { label: "Reads the details back first", dot: "bg-success" },
};

/**
 * Every tool the agent can use on a call, what it does and whether the caller approves it first.
 * Anything that changes something always asks; Sauti enforces that whatever is configured.
 */
function ToolRules({ rules }: { rules: ToolRule[] }) {
  const ordered = [...rules].sort((a, b) => Number(a.effect === "read_only") - Number(b.effect === "read_only"));
  return (
    <div className="grid gap-1.5">
      <span className="text-[0.74rem] font-medium text-fg-muted">What the agent can do</span>
      {ordered.length === 0 ? (
        <p className="m-0 text-[0.8rem] text-fg-muted">No tools are on for this agent.</p>
      ) : (
        <ul className="m-0 grid list-none divide-y divide-line rounded-[var(--radius-control)] border border-line p-0">
          {ordered.map((rule) => {
            const confirmation = CONFIRMATION_LABEL[rule.confirmation];
            return (
              <li key={rule.name} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-3 py-2 text-[0.82rem]">
                <span className="min-w-0">
                  <span className="font-mono text-[0.78rem] text-ink">{rule.name}</span>
                  <span className="text-fg-muted"> · {EFFECT_LABEL[rule.effect]}</span>
                </span>
                <span className="inline-flex items-center gap-2 whitespace-nowrap text-[0.76rem] text-fg-muted">
                  <span className={`size-1.5 rounded-full ${confirmation.dot}`} aria-hidden="true" />
                  {confirmation.label}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export function PromptPreview({ prompt, values }: { prompt: string; values: Record<string, string> }) {
  const segments = prompt.split(/(\{\{[a-zA-Z0-9_]+}})/g);
  return (
    <pre className="m-0 min-h-[min(64vh,600px)] overflow-auto whitespace-pre-wrap rounded-[var(--radius-control)] border border-line bg-tint/[0.03] p-3.5 font-mono text-[0.84rem] leading-6 text-fg">
      {segments.map((segment, index) => {
        const match = segment.match(/^\{\{([a-zA-Z0-9_]+)}}$/);
        if (!match) return <span key={`${index}-${segment.slice(0, 8)}`}>{segment}</span>;
        const value = values[match[1]]?.trim();
        return value
          ? <mark key={`${index}-${match[1]}`} className="rounded-[3px] bg-signal/15 px-0.5 text-ink">{value}</mark>
          : <em key={`${index}-${match[1]}`} className="rounded-[3px] bg-warning/15 px-0.5 not-italic text-warning">{match[1]} not provided</em>;
      })}
    </pre>
  );
}
