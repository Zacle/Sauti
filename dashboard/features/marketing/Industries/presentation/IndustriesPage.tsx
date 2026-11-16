"use client";

import type { ComponentType } from "react";
import Image from "next/image";
import Link from "next/link";
import { motion, useReducedMotion } from "motion/react";
import {
  ArrowRight,
  ArrowUpRight,
  AudioLines,
  BellRing,
  BriefcaseBusiness,
  Building2,
  CalendarCheck2,
  Check,
  CheckCircle2,
  Clock3,
  GraduationCap,
  Headphones,
  House,
  KeyRound,
  Languages,
  MapPin,
  MessageSquareText,
  Phone,
  Route,
  Scissors,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  UserRoundCheck,
  UsersRound,
  Wrench,
  X,
} from "lucide-react";
import {
  industries,
  industryFor,
  type IndustryContent,
  type IndustryIconKey,
} from "@/features/marketing/Industries/domain/industry-content";
import { Button } from "@/components/ui/button";
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

const icons: Record<IndustryIconKey, IconComponent> = {
  bell: BellRing,
  briefcase: BriefcaseBusiness,
  building: Building2,
  calendar: CalendarCheck2,
  check: CheckCircle2,
  clock: Clock3,
  education: GraduationCap,
  headphones: Headphones,
  home: House,
  key: KeyRound,
  languages: Languages,
  map: MapPin,
  message: MessageSquareText,
  phone: Phone,
  route: Route,
  scissors: Scissors,
  shield: ShieldCheck,
  sparkles: Sparkles,
  stethoscope: Stethoscope,
  user: UserRoundCheck,
  users: UsersRound,
  wrench: Wrench,
};

const industryIcons: Record<string, IconComponent> = {
  "clinics-healthcare": Stethoscope,
  "salons-beauty": Scissors,
  "real-estate": Building2,
  "professional-services": BriefcaseBusiness,
  education: GraduationCap,
  "local-businesses": Wrench,
};

const ease = [0.16, 1, 0.3, 1] as const;

/** "#32d7ee" → "50 215 238" for rgb(var(--accent)/x) and PixelGlow palettes. */
function hexToRgb(hex: string) {
  const value = hex.replace("#", "");
  const n = Number.parseInt(value.length === 3 ? value.replace(/./g, "$&$&") : value, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255] as const;
}

/* ---------- detail ---------- */

function LiveCallPanel({ industry }: { industry: IndustryContent }) {
  return (
    <aside
      aria-label={`Example ${industry.label} call handled by Sauti`}
      className="w-full overflow-hidden rounded-3xl border border-white/12 bg-black/70 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.95)] backdrop-blur-xl"
    >
      <div className="flex items-center justify-between border-b border-white/10 px-5 py-3.5">
        <span className="flex items-center gap-2 text-sm text-fg">
          <span className="size-1.5 animate-pulse rounded-full bg-[rgb(var(--accent))]" />
          {industry.call.heading}
        </span>
        <span className="flex items-center gap-1.5 font-mono text-[0.68rem] text-fg-soft">
          01:24 <AudioLines className="size-3.5" aria-hidden="true" />
        </span>
      </div>
      <div className="grid gap-3 p-5">
        <div className="max-w-[88%]">
          <p className="mb-1 font-mono text-[0.6rem] uppercase tracking-[0.14em] text-fg-muted">{industry.call.callerLabel}</p>
          <p className="rounded-2xl rounded-tl-md bg-white/[0.08] px-4 py-2.5 text-[0.92rem] leading-snug text-fg">{industry.call.caller}</p>
        </div>
        <div className="ml-auto max-w-[88%] text-right">
          <p className="mb-1 font-mono text-[0.6rem] uppercase tracking-[0.14em] text-fg-muted">Sauti</p>
          <p className="rounded-2xl rounded-tr-md bg-white px-4 py-2.5 text-left text-[0.92rem] leading-snug text-black">{industry.call.agent}</p>
        </div>
        <div className="mt-1 flex items-center gap-3 rounded-2xl border border-[rgb(var(--accent)/0.35)] bg-[rgb(var(--accent)/0.08)] px-4 py-3">
          <CheckCircle2 className="size-5 shrink-0 text-[rgb(var(--accent))]" aria-hidden="true" />
          <span>
            <span className="block text-sm font-medium text-white">{industry.call.outcome}</span>
            <span className="block font-mono text-[0.68rem] text-fg-soft">{industry.call.outcomeDetail}</span>
          </span>
        </div>
      </div>
      <div className="border-t border-white/10 bg-white/[0.02] px-5 py-4">
        <p className="font-mono text-[0.6rem] uppercase tracking-[0.14em] text-fg-muted">{industry.call.secondaryHeading}</p>
        <p className="mt-1.5 text-sm text-fg-soft">&ldquo;{industry.call.secondaryCaller}&rdquo;</p>
        <p className="mt-2 flex items-center gap-1.5 text-xs text-[rgb(var(--accent))]">
          <Route className="size-3.5" aria-hidden="true" /> {industry.call.secondaryOutcome}
        </p>
      </div>
    </aside>
  );
}

function TransformationColumn({
  tone,
  lead,
  steps,
}: {
  tone: "before" | "after";
  lead: string;
  steps: IndustryContent["transformation"]["before"];
}) {
  const after = tone === "after";
  return (
    <div
      className={cn(
        "h-full rounded-3xl border p-6 sm:p-8",
        after
          ? "border-[rgb(var(--accent)/0.35)] bg-[radial-gradient(120%_70%_at_100%_0%,rgb(var(--accent)/0.12),transparent_60%),linear-gradient(180deg,#0e0e0e,#060606)]"
          : "border-line bg-[linear-gradient(180deg,#0b0b0b,#060606)]",
      )}
    >
      <p className={cn("inline-flex items-center gap-2 rounded-full px-3 py-1 font-mono text-[0.62rem] uppercase tracking-[0.14em]", after ? "bg-[rgb(var(--accent)/0.15)] text-[rgb(var(--accent))]" : "bg-white/[0.05] text-fg-muted")}>
        {after ? <Check className="size-3" aria-hidden="true" /> : <X className="size-3" aria-hidden="true" />}
        {after ? "With Sauti" : "Without Sauti"}
      </p>
      <p className={cn("mt-5 text-xl font-medium tracking-tight", after ? "text-white" : "text-fg-soft")}>{lead}</p>
      <ol className="mt-8 grid gap-3">
        {steps.map((step, index) => {
          const Icon = icons[step.icon];
          return (
            <li key={step.title} className="flex items-start gap-4">
              <span
                className={cn(
                  "grid size-10 shrink-0 place-items-center rounded-xl border",
                  after ? "border-[rgb(var(--accent)/0.3)] bg-[rgb(var(--accent)/0.1)] text-[rgb(var(--accent))]" : "border-line bg-white/[0.03] text-fg-muted",
                )}
              >
                <Icon className="size-[18px]" aria-hidden />
              </span>
              <span className="pt-0.5">
                <span className={cn("block text-[0.95rem] font-medium", after ? "text-white" : "text-fg-soft")}>
                  <span className="mr-2 font-mono text-[0.65rem] text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
                  {step.title}
                </span>
                <span className="mt-0.5 block text-sm text-fg-muted">{step.detail}</span>
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

export function IndustryDetailPage({ slug }: { slug: string }) {
  const reduce = useReducedMotion();
  const industry = industryFor(slug);
  if (!industry) return null;

  const Icon = industryIcons[industry.slug] ?? Sparkles;
  const related = industries.filter((item) => item.slug !== industry.slug).slice(0, 3);
  const rgb = hexToRgb(industry.accent);
  const accent = rgb.join(" ");
  const palette = [rgb, [59, 130, 246], [139, 92, 246]] as const;
  const enter = (delay: number) =>
    reduce ? {} : { initial: { opacity: 0, y: 14 }, animate: { opacity: 1, y: 0 }, transition: { duration: 0.6, delay, ease } };

  return (
    <MarketingPage>
      <div style={{ ["--accent" as string]: accent }}>
        {/* Hero */}
        <section className="relative isolate overflow-hidden pb-20 pt-32 sm:pt-40">
          <PixelGlow
            seed={30 + industry.slug.length}
            palette={palette}
            intensity={0.75}
            className="pointer-events-none absolute -top-24 left-[35%] -z-10 h-[44rem] w-[72%] [mask-image:radial-gradient(closest-side,black_35%,transparent)] max-lg:left-[-20%] max-lg:w-[140%]"
          />
          <Container className="grid gap-14 lg:grid-cols-[1fr_1fr] lg:items-center [&>*]:min-w-0">
            <div>
              <motion.div {...enter(0)}>
                <Eyebrow>
                  <span className="inline-flex items-center gap-2">
                    <Icon className="size-3.5" aria-hidden /> Industries · {industry.label}
                  </span>
                </Eyebrow>
              </motion.div>
              <motion.div {...enter(0.07)} className="mt-6">
                <Headline as="h1" accent={industry.heroHighlight}>
                  {industry.heroPrefix.trim()}
                  {industry.heroSuffix ? ` ${industry.heroSuffix}` : ""}
                </Headline>
              </motion.div>
              <motion.p {...enter(0.14)} className="mt-6 max-w-[34rem] text-[1.06rem] leading-relaxed text-fg-soft">
                {industry.description}
              </motion.p>
              <motion.div {...enter(0.21)} className="mt-9 flex flex-wrap gap-3">
                <Button asChild size="lg">
                  <Link href="/request-demo">
                    Book a demo <ArrowRight aria-hidden="true" />
                  </Link>
                </Button>
                <Button asChild size="lg" variant="secondary">
                  <a href="#industry-workflow">See how it works</a>
                </Button>
              </motion.div>
              <motion.ul {...enter(0.28)} className="mt-10 flex flex-wrap gap-2">
                {industry.proof.map((item) => (
                  <li key={item} className="inline-flex items-center gap-1.5 rounded-full border border-line bg-white/[0.03] px-3 py-1.5 text-xs text-fg-soft">
                    <Check className="size-3.5 text-[rgb(var(--accent))]" aria-hidden="true" /> {item}
                  </li>
                ))}
              </motion.ul>
            </div>

            <motion.div
              initial={reduce ? false : { opacity: 0, y: 24, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.9, delay: 0.25, ease }}
              className="relative"
            >
              <div className="relative aspect-[4/5] overflow-hidden rounded-[2rem] border border-line sm:aspect-[5/5]">
                <Image src={industry.image} alt={industry.imageAlt} fill priority sizes="(max-width: 1024px) 100vw, 45vw" className="object-cover" />
                <div className="absolute inset-0 bg-gradient-to-t from-black via-black/30 to-transparent" />
              </div>
              <div className="relative -mt-56 px-4 sm:-mt-64 sm:px-8">
                <LiveCallPanel industry={industry} />
              </div>
            </motion.div>
          </Container>
        </section>

        {/* Before / after */}
        <Section id="industry-workflow">
          <SectionHeading
            eyebrow="The shift"
            title={industry.transformation.titlePrefix.trim()}
            accent={industry.transformation.titleHighlight}
          />
          <div className="mt-14 grid items-stretch gap-4 lg:grid-cols-[1fr_auto_1fr]">
            <Reveal className="h-full">
              <TransformationColumn tone="before" lead={industry.transformation.beforeLead} steps={industry.transformation.before} />
            </Reveal>
            <div className="hidden place-items-center lg:grid" aria-hidden="true">
              <span className="grid size-11 place-items-center rounded-full border border-line-strong bg-black text-[rgb(var(--accent))]">
                <ArrowRight className="size-5" />
              </span>
            </div>
            <Reveal delay={0.1} className="h-full">
              <TransformationColumn tone="after" lead={industry.transformation.afterLead} steps={industry.transformation.after} />
            </Reveal>
          </div>
          <Reveal className="mt-6">
            <p className="flex items-center justify-center gap-2 text-center text-sm text-fg-soft">
              <ShieldCheck className="size-4 text-[rgb(var(--accent))]" aria-hidden="true" />
              Your team decides what the agent answers, completes and escalates.
            </p>
          </Reveal>
        </Section>

        {/* Pillars + systems */}
        <Section>
          <SectionHeading eyebrow={industry.sectionEyebrow} title={industry.sectionTitle} description={industry.sectionDescription} />
          <div className="mt-12 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {industry.pillars.map((pillar, index) => (
              <Reveal key={pillar.title} delay={index * 0.05} className="h-full">
                <SpotlightCard accent={accent}>
                  <p className="font-mono text-[0.65rem] uppercase tracking-[0.16em] text-[rgb(var(--accent))]">
                    {String(index + 1).padStart(2, "0")} · {pillar.eyebrow}
                  </p>
                  <h3 className="mt-6 text-lg font-medium tracking-tight text-white">{pillar.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-fg-soft">{pillar.description}</p>
                </SpotlightCard>
              </Reveal>
            ))}
          </div>
          <Reveal className="mt-10">
            <div className="flex flex-wrap items-center gap-3 rounded-3xl border border-line bg-white/[0.02] p-5 sm:p-6">
              <span className="mr-2 font-mono text-[0.65rem] uppercase tracking-[0.16em] text-fg-muted">Connect the workflow</span>
              {industry.systems.map((system) => (
                <span key={system} className="rounded-full border border-line bg-black px-3 py-1.5 text-sm text-fg">
                  {system}
                </span>
              ))}
            </div>
          </Reveal>
        </Section>

        {/* Related */}
        <Section>
          <SectionHeading
            eyebrow="Other industries"
            title="The workflow changes"
            accent="when the work changes."
            action={
              <Link href="/industries" className="group inline-flex items-center gap-1.5 rounded-full border border-line px-4 py-2 text-sm text-fg-soft transition-colors hover:border-line-strong hover:text-fg">
                All industries <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
              </Link>
            }
          />
          <div className="mt-12 grid gap-4 md:grid-cols-3">
            {related.map((item, index) => (
              <Reveal key={item.slug} delay={index * 0.06}>
                <IndustryImageCard industry={item} />
              </Reveal>
            ))}
          </div>
        </Section>

        <CtaBand title={industry.finalTitle} accent="" titleEnd="" description={industry.finalDescription} seed={31} />
      </div>
    </MarketingPage>
  );
}

/* ---------- overview ---------- */

function IndustryImageCard({ industry, index, tall = false }: { industry: IndustryContent; index?: number; tall?: boolean }) {
  const Icon = industryIcons[industry.slug] ?? Sparkles;
  return (
    <Link
      href={`/industries/${industry.slug}`}
      className={cn("group relative block overflow-hidden rounded-3xl border border-line bg-surface", tall ? "aspect-[4/5]" : "aspect-[4/3]")}
    >
      <Image
        src={industry.image}
        alt={industry.imageAlt}
        fill
        sizes="(max-width: 768px) 100vw, 33vw"
        className="object-cover opacity-80 transition duration-1000 ease-out-expo group-hover:scale-[1.05] group-hover:opacity-100"
      />
      <div className="absolute inset-0 bg-[linear-gradient(to_top,#000_5%,rgb(0_0_0/0.55)_45%,transparent_80%)]" />
      {index !== undefined ? (
        <span className="absolute left-5 top-5 font-mono text-[0.68rem] text-fg-soft">{String(index + 1).padStart(2, "0")}</span>
      ) : null}
      <ArrowUpRight className="absolute right-5 top-5 size-5 text-white opacity-0 transition duration-300 group-hover:opacity-100" aria-hidden="true" />
      <div className="absolute inset-x-0 bottom-0 p-6">
        <Icon className="size-5 text-signal" aria-hidden />
        <h3 className="mt-3 text-xl font-medium tracking-tight text-white">{industry.label}</h3>
        <p className="mt-1.5 max-w-sm text-sm leading-relaxed text-fg-soft">{industry.cardDescription}</p>
      </div>
    </Link>
  );
}

export function IndustriesOverviewPage() {
  return (
    <MarketingPage>
      <PageHero
        eyebrow="Industries"
        title="One voice platform."
        accent="Six very different front doors."
        description="A patient, a salon client, a property buyer and a prospective student don't need the same conversation. Sauti shapes the agent around each industry's rules, language, systems and handoffs."
        actions={
          <>
            <Button asChild size="lg">
              <a href="#industry-directory">
                Find your industry <ArrowRight aria-hidden="true" />
              </a>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <Link href="/request-demo">Book a demo</Link>
            </Button>
          </>
        }
        glowSeed={33}
      >
        <div className="grid grid-cols-2 gap-3">
          {industries.slice(0, 3).map((industry, index) => (
            <Link
              key={industry.slug}
              href={`/industries/${industry.slug}`}
              className={cn("group relative overflow-hidden rounded-3xl border border-line", index === 0 ? "col-span-2 aspect-[16/9]" : "aspect-square")}
            >
              <Image src={industry.image} alt={industry.imageAlt} fill priority={index === 0} sizes="(max-width: 1024px) 100vw, 45vw" className="object-cover transition duration-1000 ease-out-expo group-hover:scale-[1.05]" />
              <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-transparent to-transparent" />
              <span className="absolute bottom-4 left-4 inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-black/50 px-3 py-1.5 text-xs text-white backdrop-blur-md">
                {industry.label} <ArrowUpRight className="size-3.5" aria-hidden="true" />
              </span>
            </Link>
          ))}
        </div>
      </PageHero>

      <section aria-label="Principles" className="border-y border-line">
        <div className="mx-auto grid max-w-[1240px] gap-px bg-line md:grid-cols-3">
          {[
            [Languages, "Speak like the caller", "Language and tone belong to the agent, not a hard-coded workflow."],
            [Route, "Follow the operating rules", "Each industry decides what gets answered, completed, or handed to a person."],
            [CheckCircle2, "Finish with a visible outcome", "Bookings, callbacks, leads and transfers are recorded, not just promised."],
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

      <Section id="industry-directory" bordered={false}>
        <SectionHeading
          eyebrow="Choose your environment"
          title="Start with the calls"
          accent="your team already gets."
          description="Each page has its own caller journey, workflow boundaries, integrations and proof."
        />
        <div className="mt-12 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {industries.map((industry, index) => (
            <Reveal key={industry.slug} delay={(index % 3) * 0.06}>
              <IndustryImageCard industry={industry} index={index} tall />
            </Reveal>
          ))}
        </div>
      </Section>

      <CtaBand title="Not seeing your" accent="industry?" titleEnd="" description="Model the calls, rules and outcomes that make your business different." seed={34} />
    </MarketingPage>
  );
}
