"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Check, LoaderCircle, Plus, Save, ShieldCheck, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/field";
import { toast } from "@/components/ui/sonner";
import { useAuth } from "@/hooks/useAuth";
import { getBusinessProfile, saveBusinessProfile } from "@/lib/api/business-profile";
import {
  EMPTY_FAQ,
  EMPTY_SERVICE,
  formFromProfile,
  requestFromForm,
  type BusinessProfileForm,
  type FaqRow,
  type ServiceRow,
} from "../domain/business-profile";
import styles from "./WorkspaceSettings.module.css";

/**
 * Settings → Business: facts entered once and used by every agent, unless an agent sets its own
 * value for the same detail.
 */
export function BusinessProfileSection() {
  const { session } = useAuth();
  const canEdit = session?.role === "OWNER" || session?.role === "ADMIN";
  const [form, setForm] = useState<BusinessProfileForm | null>(null);
  const [workspaceName, setWorkspaceName] = useState("");
  const [savedKey, setSavedKey] = useState("");
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState("");

  useEffect(() => {
    getBusinessProfile()
      .then((profile) => {
        const loaded = formFromProfile(profile);
        setWorkspaceName(profile.workspaceName);
        setForm(loaded);
        setSavedKey(JSON.stringify(loaded));
      })
      .catch((caught) => setFailure(caught instanceof Error ? caught.message : "Unable to load the business profile."));
  }, []);

  const dirty = form !== null && JSON.stringify(form) !== savedKey;
  const defaultCurrency = useMemo(
    () => form?.services.find((service) => service.currency.trim())?.currency.trim() ?? "",
    [form],
  );

  if (!form) {
    return (
      <section className={styles.panel}>
        <Header />
        {failure ? <div className={styles.inlineError} role="alert">{failure}</div>
          : <div className="m-5 h-48 animate-pulse rounded-[var(--radius-control)] bg-tint/[0.04]" aria-label="Loading business profile" />}
      </section>
    );
  }

  const set = <K extends keyof BusinessProfileForm>(key: K, value: BusinessProfileForm[K]) =>
    setForm((current) => (current ? { ...current, [key]: value } : current));
  const setService = (index: number, change: Partial<ServiceRow>) =>
    set("services", form.services.map((row, at) => (at === index ? { ...row, ...change } : row)));
  const setFaq = (index: number, change: Partial<FaqRow>) =>
    set("faqs", form.faqs.map((row, at) => (at === index ? { ...row, ...change } : row)));

  async function save() {
    if (!form) return;
    const result = requestFromForm(form, defaultCurrency);
    if ("error" in result) {
      setFailure(result.error);
      return;
    }
    setFailure("");
    setSaving(true);
    try {
      const saved = formFromProfile(await saveBusinessProfile(result.request));
      setForm(saved);
      setSavedKey(JSON.stringify(saved));
      toast.success("Business profile saved", { description: "Your agents use it from their next call." });
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to save the business profile.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className={styles.panel}>
      <Header status={dirty ? "Unsaved changes" : undefined} />
      {failure && <div className={styles.inlineError} role="alert">{failure}</div>}
      <fieldset disabled={!canEdit || saving} className="m-0 grid gap-6 border-0 p-5">
        <Group title="About the business">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Business name" hint={`Leave empty to use “${workspaceName}”.`}>
              <Input maxLength={160} value={form.displayName} placeholder={workspaceName} onChange={(event) => set("displayName", event.target.value)} />
            </Field>
            <Field label="Phone for callers" hint="Saved in international format.">
              <Input type="tel" autoComplete="tel" maxLength={40} value={form.phone} onChange={(event) => set("phone", event.target.value)} />
            </Field>
            <Field label="Email">
              <Input type="email" autoComplete="email" maxLength={254} value={form.email} onChange={(event) => set("email", event.target.value)} />
            </Field>
            <Field label="Website">
              <Input inputMode="url" maxLength={300} value={form.website} placeholder="brightsmile.co.ke" onChange={(event) => set("website", event.target.value)} />
            </Field>
          </div>
          <Field label="Address or locations">
            <Textarea rows={2} maxLength={500} value={form.address} onChange={(event) => set("address", event.target.value)} />
          </Field>
          <Field label="What the business does" hint="One or two sentences agents can use to describe you.">
            <Textarea rows={2} maxLength={500} value={form.description} onChange={(event) => set("description", event.target.value)} />
          </Field>
        </Group>

        <Group title="Services" hint="Agents quote these exactly, with price and duration together.">
          {form.services.length > 0 && (
            <div className="hidden grid-cols-[minmax(0,2fr)_minmax(0,1fr)_5rem_6rem_2.25rem] gap-2 text-[0.74rem] font-medium text-fg-muted sm:grid">
              <span>Service</span><span>Price</span><span>Currency</span><span>Minutes</span><span />
            </div>
          )}
          {form.services.map((service, index) => (
            <div key={index} className="grid gap-2 border-b border-line pb-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_5rem_6rem_2.25rem] sm:border-0 sm:pb-0">
              <Input aria-label={`Service ${index + 1} name`} maxLength={120} placeholder="Check-up and cleaning" value={service.name} onChange={(event) => setService(index, { name: event.target.value })} />
              <Input aria-label={`Service ${index + 1} price`} inputMode="decimal" placeholder="3,500" value={service.price} onChange={(event) => setService(index, { price: event.target.value })} />
              <Input aria-label={`Service ${index + 1} currency`} maxLength={3} placeholder={defaultCurrency || "KES"} className="uppercase" value={service.currency} onChange={(event) => setService(index, { currency: event.target.value })} />
              <Input aria-label={`Service ${index + 1} duration in minutes`} inputMode="numeric" placeholder="30" value={service.duration} onChange={(event) => setService(index, { duration: event.target.value })} />
              <RemoveButton label={`Remove ${service.name || `service ${index + 1}`}`} onClick={() => set("services", form.services.filter((_, at) => at !== index))} />
            </div>
          ))}
          <AddButton onClick={() => set("services", [...form.services, { ...EMPTY_SERVICE }])}>Add service</AddButton>
        </Group>

        <Group title="Frequently asked questions" hint="Approved answers agents may give word for word.">
          {form.faqs.map((faq, index) => (
            <div key={index} className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)_2.25rem]">
              <Input aria-label={`Question ${index + 1}`} maxLength={300} placeholder="Is there parking?" value={faq.question} onChange={(event) => setFaq(index, { question: event.target.value })} />
              <Input aria-label={`Answer ${index + 1}`} maxLength={1000} placeholder="Yes, free parking behind the building." value={faq.answer} onChange={(event) => setFaq(index, { answer: event.target.value })} />
              <RemoveButton label={`Remove question ${index + 1}`} onClick={() => set("faqs", form.faqs.filter((_, at) => at !== index))} />
            </div>
          ))}
          <AddButton onClick={() => set("faqs", [...form.faqs, { ...EMPTY_FAQ }])}>Add question</AddButton>
        </Group>

        <Group title="Policies">
          <Field label="Cancellation policy">
            <Textarea rows={3} maxLength={2000} value={form.cancellationPolicy} onChange={(event) => set("cancellationPolicy", event.target.value)} />
          </Field>
        </Group>
      </fieldset>
      <div className={styles.explainer}>
        <ShieldCheck size={18} />
        <p>An agent’s own business details always win. These fill in anything an agent leaves empty, so you only enter shared facts once.</p>
      </div>
      <div className={styles.actionBar}>
        <small>{canEdit ? "Saving refreshes every agent in this workspace." : "Only owners and admins can change the business profile."}</small>
        {canEdit && (
          <Button size="xs" disabled={saving || !dirty} onClick={() => void save()} type="button">
            {saving ? <LoaderCircle className={styles.spin} size={17} /> : dirty ? <Save size={17} /> : <Check size={17} />}
            {saving ? "Saving…" : dirty ? "Save business profile" : "Saved"}
          </Button>
        )}
      </div>
    </section>
  );
}

function Header({ status }: { status?: string }) {
  return (
    <div className={styles.panelHeader}>
      <div>
        <h2>Business profile</h2>
        <p>Facts every agent may tell callers: who you are, what you offer and what it costs.</p>
      </div>
      <span className={styles.statusNeutral}>{status ?? "Shared by all agents"}</span>
    </div>
  );
}

function Group({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <div className="grid gap-3">
      <div>
        <h3 className="m-0 text-[0.86rem] font-medium text-ink">{title}</h3>
        {hint && <p className="m-0 mt-0.5 text-[0.78rem] text-fg-muted">{hint}</p>}
      </div>
      {children}
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="grid gap-1.5">
      <span className="text-[0.82rem] font-medium text-ink">{label}</span>
      {children}
      {hint && <small className="text-[0.76rem] text-fg-muted">{hint}</small>}
    </label>
  );
}

function AddButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <Button type="button" size="xs" variant="secondary" className="justify-self-start" onClick={onClick}>
      <Plus size={16} /> {children}
    </Button>
  );
}

function RemoveButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="grid size-9 cursor-pointer place-items-center rounded-lg border-0 bg-transparent text-fg-muted transition-colors hover:bg-tint/[0.06] hover:text-danger disabled:cursor-not-allowed disabled:opacity-50"
    >
      <Trash2 size={16} />
    </button>
  );
}
