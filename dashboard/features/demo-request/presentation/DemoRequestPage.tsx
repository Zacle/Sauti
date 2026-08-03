"use client";

import Link from "next/link";
import { FormEvent, useMemo, useState } from "react";
import { ArrowRight, Check, Globe2, LoaderCircle, MessageCircleMore, Phone, ShieldCheck, Sparkles } from "lucide-react";
import { COUNTRIES } from "@/lib/countries";
import { createDemoRequest } from "@/lib/api/demo-requests";
import { DarkSelect } from "@/components/DarkSelect/DarkSelect";
import styles from "./DemoRequestPage.module.css";

const CHANNELS = [
  ["voice", "Phone calls"],
  ["browser", "Browser voice"],
  ["whatsapp", "WhatsApp"],
  ["sms", "SMS"],
] as const;

export function DemoRequestPage() {
  const [channels, setChannels] = useState<string[]>(["voice"]);
  const [busy, setBusy] = useState(false);
  const [complete, setComplete] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [countryCode, setCountryCode] = useState("KE");
  const [industry, setIndustry] = useState("");
  const [monthlyCallVolume, setMonthlyCallVolume] = useState("not-sure");
  const selectedChannels = useMemo(() => new Set(channels), [channels]);

  function toggleChannel(channel: string) {
    setChannels((current) => current.includes(channel)
      ? current.filter((item) => item !== channel)
      : [...current, channel]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (channels.length === 0) {
      setError("Select at least one channel you want to explore.");
      return;
    }
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const response = await createDemoRequest({
        businessName: String(form.get("businessName") ?? ""),
        contactName: String(form.get("contactName") ?? ""),
        email: String(form.get("email") ?? ""),
        countryCode: String(form.get("countryCode") ?? ""),
        phone: String(form.get("phone") ?? ""),
        industry: String(form.get("industry") ?? ""),
        monthlyCallVolume: String(form.get("monthlyCallVolume") ?? ""),
        channels,
        primaryUseCase: String(form.get("primaryUseCase") ?? ""),
        notes: String(form.get("notes") ?? ""),
        website: String(form.get("website") ?? ""),
      });
      setMessage(response.message);
      setComplete(true);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "We could not submit your request. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.intro}>
        <span className={styles.eyebrow}><Sparkles size={14} /> Tailored Sauti demo</span>
        <h1>See how Sauti would work for your business.</h1>
        <p>Tell us about the conversations you want to handle. We will review the fit before creating any workspace or provisioning paid services.</p>
        <div className={styles.promises}>
          <div><MessageCircleMore size={19} /><span><strong>Built around your use case</strong><small>We focus the demonstration on a real customer journey.</small></span></div>
          <div><Globe2 size={19} /><span><strong>Your markets and languages</strong><small>See the channels and language behavior relevant to you.</small></span></div>
          <div><ShieldCheck size={19} /><span><strong>No automatic account creation</strong><small>Submitting this form does not create a workspace or start billing.</small></span></div>
        </div>
      </section>

      <section className={styles.formPanel} aria-live="polite">
        {complete ? (
          <div className={styles.success}>
            <span><Check size={28} /></span>
            <small>Request received</small>
            <h2>Thanks for telling us about your business.</h2>
            <p>{message}</p>
            <Link href="/">Return to Sauti <ArrowRight size={16} /></Link>
          </div>
        ) : (
          <form onSubmit={submit}>
            <header><small>Demo request</small><h2>Help us prepare the right conversation.</h2><p>Fields marked with * are required.</p></header>
            <div className={styles.twoColumns}>
              <label>Business name *<input name="businessName" required maxLength={120} placeholder="Acme Health" autoComplete="organization" /></label>
              <label>Your name *<input name="contactName" required maxLength={120} placeholder="Alex Morgan" autoComplete="name" /></label>
              <label>Work email *<input name="email" required maxLength={254} type="email" placeholder="alex@company.com" autoComplete="email" /></label>
              <label>Phone number <input name="phone" maxLength={40} type="tel" placeholder="+254 700 000 000" autoComplete="tel" /></label>
              <label>Country *<DarkSelect ariaLabel="Country" name="countryCode" required options={COUNTRIES.map((country) => ({ value: country.code, label: country.name }))} value={countryCode} onValueChange={setCountryCode} /></label>
              <label>Industry *<DarkSelect ariaLabel="Industry" name="industry" placeholder="Select an industry" required options={["Healthcare", "Professional services", "Home services", "Retail and ecommerce", "Real estate", "Education", "Hospitality", "Other"].map((label) => ({ value: label, label }))} value={industry} onValueChange={setIndustry} /></label>
            </div>
            <label>Expected monthly conversations *<DarkSelect ariaLabel="Expected monthly conversations" name="monthlyCallVolume" required options={[{ value: "under-100", label: "Under 100" }, { value: "100-500", label: "100–500" }, { value: "500-2000", label: "500–2,000" }, { value: "2000-plus", label: "More than 2,000" }, { value: "not-sure", label: "Not sure yet" }]} value={monthlyCallVolume} onValueChange={setMonthlyCallVolume} /></label>
            <fieldset><legend>Channels you want to explore *</legend><div className={styles.channels}>{CHANNELS.map(([value, label]) => <button aria-pressed={selectedChannels.has(value)} className={selectedChannels.has(value) ? styles.selected : ""} key={value} onClick={() => toggleChannel(value)} type="button">{value === "voice" ? <Phone size={16} /> : <MessageCircleMore size={16} />}{label}{selectedChannels.has(value) ? <Check size={14} /> : null}</button>)}</div></fieldset>
            <label>What should the agent help customers accomplish? *<textarea name="primaryUseCase" required maxLength={500} rows={4} placeholder="For example: answer common questions, qualify enquiries, and book appointments into our shared calendar." /></label>
            <label>Anything else we should know?<textarea name="notes" maxLength={1000} rows={3} placeholder="Languages, integrations, operating hours, or special requirements." /></label>
            <label className={styles.honeypot} aria-hidden="true">Website<input name="website" tabIndex={-1} autoComplete="off" /></label>
            {error ? <div className={styles.error} role="alert">{error}</div> : null}
            <button className={styles.submit} disabled={busy} type="submit">{busy ? <LoaderCircle className="spin" size={17} /> : <Sparkles size={17} />}{busy ? "Sending request…" : "Request my demo"}<ArrowRight size={16} /></button>
            <p className={styles.disclosure}><ShieldCheck size={14} /> This request does not create an account or provision paid services.</p>
          </form>
        )}
      </section>
    </main>
  );
}
