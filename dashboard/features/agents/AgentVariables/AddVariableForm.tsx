"use client";

import styles from "./AddVariableForm.module.css";
import { useState } from "react";
import { Braces, Plus, X } from "lucide-react";
import type { CreateAgentVariable } from "@/types/api";

export function AddVariableForm({
  onAdd,
  disabled = false,
}: {
  onAdd: (variable: CreateAgentVariable) => void | Promise<void>;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [key, setKey] = useState("");
  const [label, setLabel] = useState("");
  const [description, setDescription] = useState("");
  const [value, setValue] = useState("");
  const [required, setRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const normalizedKey = key.trim().toLowerCase().replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "");
  const valid = /^[a-z][a-z0-9_]{0,99}$/.test(normalizedKey) && label.trim().length > 0;

  async function submit() {
    if (!valid) return;
    setBusy(true);
    setError("");
    try {
      await onAdd({ key: normalizedKey, label: label.trim(), description: description.trim(), value: value.trim(), required });
      setKey("");
      setLabel("");
      setDescription("");
      setValue("");
      setRequired(false);
      setOpen(false);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to add this variable.");
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return <button className={styles.trigger} disabled={disabled} type="button" onClick={() => setOpen(true)}><Plus size={15} /> Add variable</button>;
  }

  return (
    <div className={styles.form}>
      <header>
        <span className={styles.icon}><Braces size={18} /></span>
        <div><span>New prompt variable</span><small>Add a reusable detail to this agent.</small></div>
        <button type="button" onClick={() => setOpen(false)} aria-label="Close"><X size={17} /></button>
      </header>
      <div className={styles.tip}>Reference this value anywhere in the prompt using <code>{normalizedKey ? `{{${normalizedKey}}}` : "{{variable_name}}"}</code></div>
      <div className={styles.fields}>
        <label>
          <span>Variable key</span>
          <div className={styles.keyInput}><span>{"{{"}</span><input value={key} onChange={(event) => setKey(event.target.value)} placeholder="parking_instructions" /><span>{"}}"}</span></div>
          <small>Lowercase letters, numbers, and underscores.</small>
        </label>
        <label><span>Display label</span><input value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Parking instructions" /></label>
        <label className={styles.wide}>Description<input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Directions to share with callers" /></label>
        <label className={styles.wide}>Value<textarea value={value} onChange={(event) => setValue(event.target.value)} placeholder="Use the rear entrance after 5 PM" /></label>
      </div>
      <footer>
        <label className={styles.required}><input type="checkbox" checked={required} onChange={(event) => setRequired(event.target.checked)} /><span><i>Required before activation</i><small>The agent cannot go live until this has a value.</small></span></label>
        <div><button className={styles.cancel} type="button" onClick={() => setOpen(false)}>Cancel</button><button className="console-primary-button" disabled={!valid || busy} onClick={() => void submit()} type="button">Add variable</button></div>
      </footer>
      {error && <div className={styles.error}>{error}</div>}
    </div>
  );
}
