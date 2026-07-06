"use client";

import styles from "./DeleteAgentDialog.module.css";
import { LoaderCircle, Trash2, X } from "lucide-react";
import type { Agent } from "@/types/api";

export function DeleteAgentDialog({
  agent,
  busy,
  error,
  onCancel,
  onConfirm,
}: {
  agent: Agent;
  busy: boolean;
  error?: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className={styles.backdrop} role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !busy) onCancel();
    }}>
      <section aria-labelledby="delete-agent-title" aria-modal="true" className={styles.dialog} role="dialog">
        <button aria-label="Close" className={styles.close} disabled={busy} onClick={onCancel} type="button">
          <X size={18} />
        </button>
        <span className={styles.icon}><Trash2 size={22} /></span>
        <h2 id="delete-agent-title">Delete {agent.name}?</h2>
        <p>
          This permanently removes the agent configuration, tools, and business variables.
          Agents with call or booking history are protected from deletion.
        </p>
        {agent.active && <div className={styles.notice}>This agent is currently live. Deletion will take it offline immediately.</div>}
        {error && <div className={styles.error}>{error}</div>}
        <footer>
          <button disabled={busy} onClick={onCancel} type="button">Cancel</button>
          <button className={styles.danger} disabled={busy} onClick={onConfirm} type="button">
            {busy ? <LoaderCircle className="spin" size={16} /> : <Trash2 size={16} />}
            Delete agent
          </button>
        </footer>
      </section>
    </div>
  );
}
