"use client";

import Link from "next/link";
import { FormEvent, useMemo, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { ArrowRight, Check, Globe2, LoaderCircle, MessageCircleMore, Monitor, Phone, ShieldCheck, Smartphone } from "lucide-react";
import { COUNTRIES } from "@/lib/countries";
import { createDemoRequest } from "@/lib/api/demo-requests";
import { trackPublicAnalyticsEvent } from "@/lib/api/public-analytics";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label, Textarea } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { toast } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { Container, Eyebrow, Headline, MarketingPage, PixelGlow, glowPalettes } from "@/features/marketing/shared/primitives";

const CHANNELS = [
  ["voice", "Phone calls", Phone],
  ["browser", "Browser voice", Monitor],
  ["whatsapp", "WhatsApp", MessageCircleMore],
  ["sms", "SMS", Smartphone],
] as const;

const INDUSTRIES = ["Healthcare", "Professional services", "Home services", "Retail and ecommerce", "Real estate", "Education", "Hospitality", "Other"];
const VOLUMES = [
  { value: "under-100", label: "Under 100" },
  { value: "100-500", label: "100–500" },
  { value: "500-2000", label: "500–2,000" },
  { value: "2000-plus", label: "More than 2,000" },
  { value: "not-sure", label: "Not sure yet" },
];

const steps = [
  ["We review the fit", "We read your use case and check languages, channels and integrations."],
  ["We configure a demo agent", "Built around one real customer journey from your business."],
  ["You hear it live", "A short walkthrough, then you decide whether to start a pilot."],
];

const ease = [0.16, 1, 0.3, 1] as const;
const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({ value: country.code, label: country.name, keywords: [country.code] }));

export function DemoRequestPage() {
  const reduce = useReducedMotion();
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
      toast.success("Request received", { description: "We'll be in touch by email." });
      trackPublicAnalyticsEvent("demo_request_submitted");
    } catch (caught) {
      const text = caught instanceof Error ? caught.message : "We could not submit your request. Please try again.";
      setError(text);
      toast.error("Request not sent", { description: text });
    } finally {
      setBusy(false);
    }
  }

  return (
    <MarketingPage>
      <section className="relative isolate overflow-hidden pb-24 pt-32 sm:pt-40">
        <PixelGlow
          seed={14}
          palette={glowPalettes.signal}
          intensity={0.6}
          className="pointer-events-none absolute -left-60 top-56 -z-10 h-[44rem] w-[60rem] max-w-[160%] [mask-image:radial-gradient(closest-side,black_30%,transparent)]"
        />
        <Container className="grid gap-14 lg:grid-cols-[0.9fr_1.1fr] lg:gap-16 [&>*]:min-w-0">
          {/* Intro */}
          <motion.div
            initial={reduce ? false : { opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease }}
            className="lg:sticky lg:top-28 lg:self-start"
          >
            <Eyebrow>Tailored demo</Eyebrow>
            <Headline as="h1" accent="for your business." accentBlock className="mt-6">
              See Sauti working
            </Headline>
            <p className="mt-6 max-w-md text-[1.05rem] leading-relaxed text-fg-soft">
              Tell us about the conversations you want to handle. We review the fit before creating any workspace or
              provisioning paid services.
            </p>

            <ol className="relative mt-12 grid gap-8 before:absolute before:bottom-3 before:left-[15px] before:top-3 before:w-px before:bg-line">
              {steps.map(([title, text], index) => (
                <li key={title} className="relative flex gap-5">
                  <span className="relative grid size-8 shrink-0 place-items-center rounded-full border border-signal/30 bg-black font-mono text-xs text-signal">
                    {index + 1}
                  </span>
                  <span>
                    <span className="block font-medium text-white">{title}</span>
                    <span className="mt-1 block text-sm leading-relaxed text-fg-soft">{text}</span>
                  </span>
                </li>
              ))}
            </ol>

            <div className="mt-12 flex flex-wrap gap-x-6 gap-y-2 text-sm text-fg-soft">
              <span className="flex items-center gap-2"><Globe2 className="size-4 text-signal" aria-hidden="true" /> Your markets and languages</span>
              <span className="flex items-center gap-2"><ShieldCheck className="size-4 text-signal" aria-hidden="true" /> No automatic account creation</span>
            </div>
          </motion.div>

          {/* Form */}
          <motion.section
            initial={reduce ? false : { opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.12, ease }}
            aria-live="polite"
            className="rounded-3xl border border-line-strong bg-[linear-gradient(180deg,#0e0e0e,#070707)] p-6 shadow-[0_40px_120px_-40px_rgba(0,0,0,0.9)] backdrop-blur-xl sm:p-9"
          >
            <AnimatePresence mode="wait" initial={false}>
              {complete ? (
                <motion.div
                  key="done"
                  initial={{ opacity: 0, scale: 0.97 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.5, ease }}
                  className="flex min-h-[32rem] flex-col items-center justify-center text-center"
                >
                  <motion.span
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    transition={{ type: "spring", bounce: 0.5, delay: 0.1 }}
                    className="grid size-16 place-items-center rounded-full bg-signal text-black shadow-[0_0_60px_rgb(94_234_212/0.45)]"
                  >
                    <Check className="size-7" aria-hidden="true" />
                  </motion.span>
                  <p className="mt-8 font-mono text-[0.65rem] uppercase tracking-[0.18em] text-signal">Request received</p>
                  <h2 className="mt-3 text-3xl font-medium tracking-tight text-white">Thanks for telling us about your business.</h2>
                  <p className="mt-3 max-w-sm text-fg-soft">{message}</p>
                  <Button asChild variant="secondary" className="mt-8">
                    <Link href="/">
                      Back to Sauti <ArrowRight aria-hidden="true" />
                    </Link>
                  </Button>
                </motion.div>
              ) : (
                <motion.form key="form" onSubmit={submit} exit={{ opacity: 0, y: -8 }} className="grid gap-5">
                  <header className="mb-2">
                    <h2 className="text-2xl font-medium tracking-tight text-white">Help us prepare the right conversation</h2>
                    <p className="mt-1.5 text-sm text-fg-muted">Fields marked with * are required.</p>
                  </header>

                  <div className="grid gap-5 sm:grid-cols-2">
                    <Label>Business name *<Input name="businessName" required maxLength={120} placeholder="Acme Health" autoComplete="organization" /></Label>
                    <Label>Your name *<Input name="contactName" required maxLength={120} placeholder="Alex Morgan" autoComplete="name" /></Label>
                    <Label>Work email *<Input name="email" required maxLength={254} type="email" placeholder="alex@company.com" autoComplete="email" /></Label>
                    <Label>Phone number<Input name="phone" maxLength={40} type="tel" placeholder="+254 700 000 000" autoComplete="tel" /></Label>
                    <Label>
                      Country *
                      <Select name="countryCode" required ariaLabel="Country" value={countryCode} onValueChange={setCountryCode} searchPlaceholder="Search countries…" options={COUNTRY_OPTIONS} />
                    </Label>
                    <Label>
                      Industry *
                      <Select name="industry" required ariaLabel="Industry" placeholder="Select an industry" value={industry} onValueChange={setIndustry} options={INDUSTRIES.map((label) => ({ value: label, label }))} />
                    </Label>
                  </div>

                  <Label>
                    Expected monthly conversations *
                    <Select name="monthlyCallVolume" required ariaLabel="Expected monthly conversations" value={monthlyCallVolume} onValueChange={setMonthlyCallVolume} options={VOLUMES} />
                  </Label>

                  <fieldset className="m-0 grid min-w-0 gap-2 border-0 p-0">
                    <legend className="mb-2 text-sm font-medium text-fg">Channels you want to explore *</legend>
                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                      {CHANNELS.map(([value, label, Icon]) => {
                        const selected = selectedChannels.has(value);
                        return (
                          <button
                            key={value}
                            type="button"
                            aria-pressed={selected}
                            onClick={() => toggleChannel(value)}
                            className={cn(
                              "relative flex flex-col items-start gap-3 rounded-2xl border p-3.5 text-left text-sm transition-colors focus-visible:outline-2 focus-visible:outline-signal",
                              selected ? "border-signal/45 bg-signal/[0.08] text-white" : "border-line bg-white/[0.02] text-fg-soft hover:border-line-strong",
                            )}
                          >
                            <Icon className={cn("size-[18px]", selected ? "text-signal" : "")} aria-hidden="true" />
                            {label}
                            <span className={cn("absolute right-3 top-3 grid size-4 place-items-center rounded-full border transition-colors", selected ? "border-signal bg-signal text-black" : "border-line-strong")}>
                              {selected ? <Check className="size-3" aria-hidden="true" /> : null}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </fieldset>

                  <Label>
                    What should the agent help customers accomplish? *
                    <Textarea name="primaryUseCase" required maxLength={500} rows={4} placeholder="For example: answer common questions, qualify enquiries, and book appointments into our shared calendar." />
                  </Label>
                  <Label>
                    Anything else we should know?
                    <Textarea name="notes" maxLength={1000} rows={3} placeholder="Languages, integrations, operating hours, or special requirements." />
                  </Label>
                  <label className="absolute -left-[9999px] h-px w-px overflow-hidden" aria-hidden="true">
                    Website<input name="website" tabIndex={-1} autoComplete="off" />
                  </label>

                  {error ? <Alert tone="error">{error}</Alert> : null}

                  <Button type="submit" size="lg" disabled={busy} className="mt-1 w-full rounded-xl">
                    {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
                    {busy ? "Sending request…" : "Request my demo"}
                    {!busy ? <ArrowRight aria-hidden="true" /> : null}
                  </Button>
                  <p className="flex items-center justify-center gap-2 text-xs text-fg-muted">
                    <ShieldCheck className="size-3.5" aria-hidden="true" /> This request doesn&rsquo;t create an account or start billing.
                  </p>
                </motion.form>
              )}
            </AnimatePresence>
          </motion.section>
        </Container>
      </section>
    </MarketingPage>
  );
}
