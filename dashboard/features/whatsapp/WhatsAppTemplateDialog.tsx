"use client";

import { useEffect, useMemo, useState } from "react";
import { LoaderCircle, Send } from "lucide-react";
import { listWhatsAppTemplates, sendWhatsAppTemplate, type WhatsAppMessage, type WhatsAppTemplate } from "@/lib/api/whatsapp";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/field";
import { Dialog, DialogContent } from "@/components/ui/overlays";
import { Select } from "@/components/ui/select";
import styles from "./WhatsAppInboxPage.module.css";

/**
 * Picks one of the business's approved WhatsApp templates, fills in its placeholders and sends it.
 * WhatsApp accepts templates after the 24-hour reply window has closed.
 */
export function WhatsAppTemplateDialog({ conversationId, open, onOpenChange, onSent }: {
  conversationId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSent: (message: WhatsAppMessage) => void;
}) {
  const [templates, setTemplates] = useState<WhatsAppTemplate[] | null>(null);
  const [chosen, setChosen] = useState("");
  const [values, setValues] = useState<Record<string, string>>({});
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    setTemplates(null);
    setError("");
    listWhatsAppTemplates(conversationId)
      .then((items) => {
        setTemplates(items);
        setChosen(items[0] ? templateKey(items[0]) : "");
        setValues({});
      })
      .catch((caught) => {
        setTemplates([]);
        setError(caught instanceof Error ? caught.message : "Unable to load your WhatsApp templates.");
      });
  }, [conversationId, open]);

  const template = templates?.find((item) => templateKey(item) === chosen) ?? null;
  const preview = useMemo(() => template ? render(template, values) : "", [template, values]);
  const missing = template?.fields.some((field) => !values[field.key]?.trim()) ?? true;

  async function send() {
    if (!template || missing) return;
    setSending(true);
    setError("");
    try {
      const message = await sendWhatsAppTemplate(conversationId, template, values);
      onSent(message);
      onOpenChange(false);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to send the template.");
    } finally { setSending(false); }
  }

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent title="Send a template"
      description="WhatsApp only accepts approved templates once 24 hours have passed since the customer last wrote. When they reply, you can write freely again.">
      {templates === null ? <div className={styles.templateLoading}><LoaderCircle className="spin" size={16} /> Loading templates…</div>
        : templates.length === 0 ? <p className={styles.templateEmpty}>{error || "This WhatsApp number has no approved templates yet. Create one in WhatsApp Manager, then try again."}</p>
          : <div className={styles.templateForm}>
            <div><Label htmlFor="wa-template">Template</Label>
              <Select id="wa-template" value={chosen} onValueChange={(value) => { setChosen(value); setValues({}); }}
                options={templates.map((item) => ({ value: templateKey(item), label: item.name.replace(/_/g, " "),
                  description: `${item.language} · ${item.category.toLowerCase()}` }))} /></div>
            {template?.fields.map((field) => <div key={field.key}>
              <Label htmlFor={`wa-field-${field.key}`}>{fieldLabel(field)}</Label>
              <Input id={`wa-field-${field.key}`} value={values[field.key] ?? ""}
                onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} />
            </div>)}
            {preview && <div className={styles.templatePreview}><small>The customer will see</small><p>{preview}</p></div>}
            {error && <p className={styles.templateError}>{error}</p>}
            <div className={styles.templateActions}>
              <Button variant="secondary" size="sm" onClick={() => onOpenChange(false)}>Cancel</Button>
              <Button size="sm" disabled={sending || missing} onClick={() => void send()}>
                {sending ? <LoaderCircle className="spin" /> : <Send />} Send template
              </Button>
            </div>
          </div>}
    </DialogContent>
  </Dialog>;
}

const templateKey = (template: Pick<WhatsAppTemplate, "name" | "language">) => `${template.name}:${template.language}`;

function fieldLabel(field: WhatsAppTemplate["fields"][number]) {
  const where = field.component === "header" ? "Heading" : "Message";
  return /^\d+$/.test(field.name) ? `${where} ${field.placeholder}` : `${where}: ${field.name.replace(/_/g, " ")}`;
}

/** What the customer reads, as the server builds it: the heading, a blank line, then the body. */
function render(template: WhatsAppTemplate, values: Record<string, string>) {
  const fill = (text: string, component: "header" | "body") => template.fields
    .filter((field) => field.component === component)
    .reduce((result, field) => result.split(field.placeholder).join(values[field.key]?.trim() || field.placeholder), text)
    .trim();
  const header = fill(template.headerText, "header");
  const body = fill(template.bodyText, "body");
  return [header, body].filter(Boolean).join("\n\n");
}
