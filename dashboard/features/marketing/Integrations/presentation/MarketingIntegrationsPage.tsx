"use client";

import type { ComponentType, CSSProperties } from "react";
import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  Bot,
  BriefcaseBusiness,
  CalendarCheck2,
  Check,
  CheckCircle2,
  Code2,
  Database,
  LockKeyhole,
  MessageSquareText,
  PhoneCall,
  Route,
  ShieldCheck,
  Sparkles,
  Webhook,
  Waves,
} from "lucide-react";
import {
  marketingIntegrationFor,
  marketingIntegrations,
  type IntegrationIconKey,
  type MarketingIntegration,
} from "@/features/marketing/Integrations/domain/integration-content";
import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import { cn } from "@/lib/utils";
import {
  Container,
  CtaBand,
  Eyebrow,
  Headline,
  IconTile,
  MarketingPage,
  PageHero,
  PixelGlow,
  Reveal,
  Section,
  SectionHeading,
  SpotlightCard,
} from "@/features/marketing/shared/primitives";

type IconComponent = ComponentType<{ className?: string; "aria-hidden"?: boolean }>;

const iconByKey: Record<IntegrationIconKey, IconComponent> = {
  activity: Activity,
  bot: Bot,
  briefcase: BriefcaseBusiness,
  calendar: CalendarCheck2,
  code: Code2,
  database: Database,
  lock: LockKeyhole,
  message: MessageSquareText,
  phone: PhoneCall,
  route: Route,
  shield: ShieldCheck,
  sparkles: Sparkles,
  waveform: Waves,
  webhook: Webhook,
};

const ease = [0.16, 1, 0.3, 1] as const;

// Brand-colour versions live in /logos/color; the rest are single-colour marks
// that we render white so they stay visible on black.
const colorLogos = new Set(["calendly", "google-calendar", "google-sheets", "hubspot", "mpesa", "pipedrive", "salesforce", "slack", "whatsapp", "zapier"]);

function logoSource(logo: string) {
  const name = logo.split("/").pop()?.replace(".svg", "") ?? "";
  return colorLogos.has(name) ? { src: `/logos/color/${name}.svg`, mono: false } : { src: logo, mono: true };
}

function hexToRgb(hex: string) {
  const n = Number.parseInt(hex.replace("#", ""), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255] as const;
}

function ProviderChips({ providers, className }: { providers: MarketingIntegration["providers"]; className?: string }) {
  return (
    <ul className={cn("flex flex-wrap gap-2", className)} aria-label="Supported providers">
      {providers.map((provider) => {
        const logo = provider.logo ? logoSource(provider.logo) : null;
        return (
          <li key={provider.name} className="inline-flex items-center gap-2 rounded-full border border-line bg-white/[0.03] py-1 pl-1 pr-3 text-sm text-fg-soft">
            <span className="grid size-7 place-items-center rounded-full bg-white/[0.06]">
              {logo ? (
                <Image src={logo.src} alt="" width={16} height={16} className={cn("size-4 object-contain", logo.mono && "brightness-0 invert")} />
              ) : (
                <Bot className="size-4 text-fg-soft" aria-hidden="true" />
              )}
            </span>
            {provider.name}
          </li>
        );
      })}
    </ul>
  );
}

/* ---------- animated flow card ---------- */

function FlowCard({ integration }: { integration: MarketingIntegration }) {
  const reduce = useReducedMotion();
  const steps = integration.flow.steps;
  const [step, setStep] = useState(reduce ? steps.length : 0);

  useEffect(() => {
    if (reduce) return;
    const done = step >= steps.length;
    const timer = window.setTimeout(() => setStep(done ? 0 : step + 1), done ? 3000 : 1200);
    return () => window.clearTimeout(timer);
  }, [reduce, step, steps.length]);

  return (
    <aside
      aria-label={`${integration.label} workflow`}
      className="mx-auto w-full max-w-[480px] overflow-hidden rounded-3xl border border-line-strong bg-[linear-gradient(180deg,#0e0e0e,#070707)] shadow-[0_40px_120px_-40px_rgba(0,0,0,0.9)] lg:mr-0"
    >
      <div className="flex items-center justify-between border-b border-line px-5 py-4 text-sm text-fg">
        <span className="flex items-center gap-2">
          <span className="size-1.5 animate-pulse rounded-full bg-[rgb(var(--accent))]" /> Connected workflow
        </span>
        <span className="font-mono text-[0.62rem] uppercase tracking-[0.14em] text-fg-muted">Live</span>
      </div>
      <ol className="relative grid gap-2 p-5">
        {steps.map((item, index) => {
          const Icon = iconByKey[item.icon];
          const state = index < step ? "done" : index === step ? "active" : "idle";
          return (
            <li
              key={item.label}
              className={cn(
                "flex gap-3.5 rounded-2xl border p-3.5 transition-colors duration-500",
                state === "active" ? "border-[rgb(var(--accent)/0.45)] bg-[rgb(var(--accent)/0.08)]" : state === "done" ? "border-line bg-white/[0.02]" : "border-transparent",
              )}
            >
              <span
                className={cn(
                  "grid size-9 shrink-0 place-items-center rounded-xl border transition-colors duration-500",
                  state === "idle" ? "border-line text-fg-muted" : "border-[rgb(var(--accent)/0.35)] bg-[rgb(var(--accent)/0.12)] text-[rgb(var(--accent))]",
                )}
              >
                {state === "done" ? <Check className="size-4" aria-hidden="true" /> : <Icon className="size-4" aria-hidden />}
              </span>
              <span className="min-w-0">
                <span className={cn("block text-sm font-medium", state === "idle" ? "text-fg-muted" : "text-white")}>{item.label}</span>
                <span className="mt-0.5 block text-xs leading-relaxed text-fg-muted">{item.detail}</span>
              </span>
            </li>
          );
        })}
      </ol>
      <div className="h-[4.5rem] px-5 pb-5">
        <AnimatePresence>
          {step >= steps.length ? (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              className="flex items-center gap-3 rounded-2xl border border-[rgb(var(--accent)/0.35)] bg-[rgb(var(--accent)/0.08)] px-4 py-2.5"
            >
              <CheckCircle2 className="size-5 shrink-0 text-[rgb(var(--accent))]" aria-hidden="true" />
              <span>
                <span className="block text-sm font-medium text-white">{integration.flow.result}</span>
                <span className="block text-xs text-fg-soft">{integration.flow.resultDetail}</span>
              </span>
            </motion.div>
          ) : null}
        </AnimatePresence>
      </div>
    </aside>
  );
}

/* ---------- detail ---------- */

export function MarketingIntegrationDetailPage({ slug }: { slug: string }) {
  const reduce = useReducedMotion();
  const integration = marketingIntegrationFor(slug);
  if (!integration) return null;

  const Icon = iconByKey[integration.icon];
  const related = marketingIntegrations.filter((item) => item.slug !== slug).slice(0, 3);
  const rgb = hexToRgb(integration.accent);
  const accent = rgb.join(" ");
  const enter = (delay: number) =>
    reduce ? {} : { initial: { opacity: 0, y: 14 }, animate: { opacity: 1, y: 0 }, transition: { duration: 0.6, delay, ease } };

  return (
    <MarketingPage>
      <div style={{ "--accent": accent } as CSSProperties}>
        <section className="relative isolate overflow-hidden pb-20 pt-32 sm:pt-40">
          <PixelGlow
            seed={40 + slug.length}
            palette={[rgb, [59, 130, 246], [139, 92, 246]]}
            intensity={0.7}
            className="pointer-events-none absolute -top-24 left-[38%] -z-10 h-[42rem] w-[70%] [mask-image:radial-gradient(closest-side,black_35%,transparent)] max-lg:left-[-20%] max-lg:w-[140%]"
          />
          <Container className="grid gap-14 lg:grid-cols-[1.05fr_0.95fr] lg:items-center [&>*]:min-w-0">
            <div>
              <motion.div {...enter(0)}>
                <Eyebrow>
                  <span className="inline-flex items-center gap-2">
                    <Icon className="size-3.5" aria-hidden /> Integrations · {integration.label}
                  </span>
                </Eyebrow>
              </motion.div>
              <motion.div {...enter(0.07)} className="mt-6">
                <Headline as="h1" accent={integration.heroHighlight}>
                  {integration.heroPrefix.trim()}
                  {integration.heroSuffix ? ` ${integration.heroSuffix}` : ""}
                </Headline>
              </motion.div>
              <motion.p {...enter(0.14)} className="mt-6 max-w-[34rem] text-[1.06rem] leading-relaxed text-fg-soft">
                {integration.description}
              </motion.p>
              <motion.div {...enter(0.21)} className="mt-9 flex flex-wrap gap-3">
                <Button asChild size="lg">
                  <Link href="/request-demo">
                    Book a demo <ArrowRight aria-hidden="true" />
                  </Link>
                </Button>
                <Button asChild size="lg" variant="secondary">
                  <a href="#integration-flow">Follow the workflow</a>
                </Button>
              </motion.div>
              <motion.div {...enter(0.28)} className="mt-10">
                <p className="mb-3 font-mono text-[0.65rem] uppercase tracking-[0.16em] text-fg-muted">Works with</p>
                <ProviderChips providers={integration.providers} />
              </motion.div>
            </div>
            <motion.div
              initial={reduce ? false : { opacity: 0, y: 24, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.9, delay: 0.25, ease }}
            >
              <FlowCard integration={integration} />
            </motion.div>
          </Container>
        </section>

        <section aria-label="Highlights" className="border-y border-line">
          <div className="mx-auto grid max-w-[1240px] gap-px bg-line sm:grid-cols-3">
            {integration.proof.map((item) => (
              <p key={item} className="flex items-center gap-3 bg-canvas px-6 py-6 text-sm text-fg sm:px-8">
                <CheckCircle2 className="size-4 shrink-0 text-[rgb(var(--accent))]" aria-hidden="true" />
                {item}
              </p>
            ))}
          </div>
        </section>

        <Section id="integration-flow" bordered={false}>
          <SectionHeading eyebrow={integration.flow.eyebrow} title={integration.flow.title} description={integration.flow.description} />
          <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {integration.flow.steps.map((step, index) => {
              const StepIcon = iconByKey[step.icon];
              return (
                <Reveal key={step.label} delay={index * 0.06} className="h-full">
                  <SpotlightCard accent={accent}>
                    <div className="flex items-center justify-between">
                      <IconTile accent={accent}>
                        <StepIcon aria-hidden />
                      </IconTile>
                      <span className="font-mono text-xs text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
                    </div>
                    <h3 className="mt-8 text-lg font-medium text-white">{step.label}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-fg-soft">{step.detail}</p>
                  </SpotlightCard>
                </Reveal>
              );
            })}
          </div>
        </Section>

        <Section>
          <div className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:items-start">
            <div>
              <SectionHeading eyebrow={integration.value.eyebrow} title={integration.value.title} description={integration.value.description} />
              <Reveal delay={0.08} className="mt-10 rounded-3xl border border-line bg-white/[0.02] p-6">
                <p className="flex items-center gap-2 text-sm font-medium text-white">
                  <ShieldCheck className="size-4 text-[rgb(var(--accent))]" aria-hidden="true" /> Built-in safeguards
                </p>
                <ul className="mt-4 grid gap-2.5">
                  {integration.safeguards.map((item) => (
                    <li key={item} className="flex gap-2.5 text-sm text-fg-soft">
                      <Check className="mt-0.5 size-4 shrink-0 text-[rgb(var(--accent))]" aria-hidden="true" />
                      {item}
                    </li>
                  ))}
                </ul>
              </Reveal>
            </div>
            <div className="grid gap-3">
              {integration.value.capabilities.map((capability, index) => (
                <Reveal key={capability.title} delay={index * 0.05}>
                  <SpotlightCard accent={accent} className="flex-row gap-5">
                    <span className="font-mono text-xl text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
                    <span>
                      <span className="block text-[1.02rem] font-medium text-white">{capability.title}</span>
                      <span className="mt-1 block text-sm leading-relaxed text-fg-soft">{capability.description}</span>
                    </span>
                  </SpotlightCard>
                </Reveal>
              ))}
            </div>
          </div>
        </Section>

        <Section>
          <SectionHeading
            eyebrow="Explore the stack"
            title="Every integration"
            accent="has one clear job."
            action={
              <Link href="/integrations" className="group inline-flex items-center gap-1.5 rounded-full border border-line px-4 py-2 text-sm text-fg-soft transition-colors hover:border-line-strong hover:text-fg">
                All integrations <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
              </Link>
            }
          />
          <div className="mt-12 grid gap-4 md:grid-cols-3">
            {related.map((item, index) => (
              <Reveal key={item.slug} delay={index * 0.06} className="h-full">
                <IntegrationCard item={item} />
              </Reveal>
            ))}
          </div>
        </Section>

        <CtaBand title={integration.finalTitle} accent="" titleEnd="" description={integration.finalDescription} seed={41} />
      </div>
    </MarketingPage>
  );
}

function IntegrationCard({ item, index, showProviders = false }: { item: MarketingIntegration; index?: number; showProviders?: boolean }) {
  const Icon = iconByKey[item.icon];
  const accent = hexToRgb(item.accent).join(" ");
  return (
    <Link href={`/integrations/${item.slug}`} className="block h-full">
      <SpotlightCard accent={accent}>
        <div className="flex items-start justify-between">
          <IconTile accent={accent}>
            <Icon aria-hidden />
          </IconTile>
          {index !== undefined ? (
            <span className="font-mono text-xs text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
          ) : (
            <ArrowUpRight className="size-5 text-fg-muted transition-colors group-hover:text-white" aria-hidden="true" />
          )}
        </div>
        <h3 className="mt-8 text-xl font-medium tracking-tight text-white">{item.label}</h3>
        <p className="mt-2 text-sm leading-relaxed text-fg-soft">{item.cardDescription}</p>
        {showProviders ? <ProviderChips providers={item.providers} className="mt-6" /> : null}
        <span className="mt-auto inline-flex items-center gap-1.5 pt-8 text-sm text-white">
          Explore <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </span>
      </SpotlightCard>
    </Link>
  );
}

/* ---------- overview ---------- */

function StackOrbit() {
  const reduce = useReducedMotion();
  const items = marketingIntegrations;
  return (
    <div className="relative mx-auto aspect-square w-full max-w-[460px]" aria-label="Sauti connected stack">
      <div className="absolute inset-[12%] rounded-full border border-dashed border-white/10" />
      <div className="absolute inset-[30%] rounded-full border border-white/[0.06]" />
      <motion.div
        className="absolute inset-0"
        animate={reduce ? undefined : { rotate: 360 }}
        transition={{ duration: 80, repeat: Infinity, ease: "linear" }}
      >
        {items.map((item, index) => {
          const Icon = iconByKey[item.icon];
          const angle = (index / items.length) * Math.PI * 2 - Math.PI / 2;
          const x = 50 + Math.cos(angle) * 38;
          const y = 50 + Math.sin(angle) * 38;
          const accent = hexToRgb(item.accent).join(" ");
          return (
            <motion.div
              key={item.slug}
              className="absolute -translate-x-1/2 -translate-y-1/2"
              style={{ left: `${x}%`, top: `${y}%`, "--accent": accent } as CSSProperties}
              animate={reduce ? undefined : { rotate: -360 }}
              transition={{ duration: 80, repeat: Infinity, ease: "linear" }}
            >
              <Link
                href={`/integrations/${item.slug}`}
                className="flex flex-col items-center gap-2 rounded-2xl border border-[rgb(var(--accent)/0.3)] bg-black/80 px-3 py-2.5 backdrop-blur transition-colors hover:border-[rgb(var(--accent)/0.6)]"
              >
                <Icon className="size-5 text-[rgb(var(--accent))]" aria-hidden />
                <span className="whitespace-nowrap text-[0.7rem] text-fg">{item.shortLabel}</span>
              </Link>
            </motion.div>
          );
        })}
      </motion.div>
      <div className="absolute left-1/2 top-1/2 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-2 rounded-3xl border border-line-strong bg-black/80 px-6 py-5 text-center shadow-[0_0_80px_-10px_rgb(94_234_212/0.4)] backdrop-blur">
        <BrandLogo size={36} />
        <span className="text-sm font-medium text-white">Sauti</span>
        <span className="font-mono text-[0.6rem] uppercase tracking-[0.14em] text-fg-muted">Orchestrates</span>
      </div>
    </div>
  );
}

export function MarketingIntegrationsOverviewPage() {
  return (
    <MarketingPage>
      <PageHero
        eyebrow="Integrations"
        title="Connect the systems that turn a conversation into"
        accent="completed work."
        description="Sauti separates live voice, business decisions and external delivery, so every provider has a clear role and every action stays observable."
        actions={
          <>
            <Button asChild size="lg">
              <Link href="/integrations/calendars">
                Explore a booking flow <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <Link href="/request-demo">Book a demo</Link>
            </Button>
          </>
        }
        glowSeed={42}
      >
        <StackOrbit />
      </PageHero>

      <section aria-label="Principles" className="border-y border-line">
        <div className="mx-auto grid max-w-[1240px] gap-px bg-line md:grid-cols-3">
          {[
            [ShieldCheck, "Safe by boundary", "Models propose; Sauti authorizes; tools report fact."],
            [Database, "Durable by default", "Primary records are saved before optional external delivery."],
            [Activity, "Visible in operation", "Connection health and delivery outcomes stay inspectable."],
          ].map(([I, title, text]) => {
            const PrincipleIcon = I as IconComponent;
            return (
              <div key={title as string} className="bg-canvas px-6 py-10 sm:px-8">
                <IconTile>
                  <PrincipleIcon aria-hidden />
                </IconTile>
                <p className="mt-6 text-lg font-medium text-white">{title as string}</p>
                <p className="mt-1.5 text-sm leading-relaxed text-fg-soft">{text as string}</p>
              </div>
            );
          })}
        </div>
      </section>

      <Section bordered={false}>
        <SectionHeading
          eyebrow="Integration layers"
          title="Choose the part of the call stack"
          accent="you need to connect."
          description="Operating layers, not a wall of interchangeable logos."
        />
        <div className="mt-12 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {marketingIntegrations.map((item, index) => (
            <Reveal key={item.slug} delay={(index % 3) * 0.06} className="h-full">
              <IntegrationCard item={item} index={index} showProviders />
            </Reveal>
          ))}
        </div>
      </Section>

      <CtaBand title="Start with the outcome" accent="your team needs" titleEnd="after the call." description="Then enable only the providers and agent tools required to complete it." seed={43} />
    </MarketingPage>
  );
}
