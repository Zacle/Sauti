"use client";

import Link from "next/link";
import { ArrowRight, ArrowUpRight, CheckCircle2 } from "lucide-react";
import { groupFor, pageFor, sectionPathFor } from "@/features/marketing/site-map";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { SolutionExperience, SolutionVisual } from "./SolutionExperience";
import { isSolutionSlug } from "./walkthrough-content";
import {
  CtaBand,
  IconTile,
  MarketingPage,
  PageHero,
  Reveal,
  Section,
  SectionHeading,
  SpotlightCard,
} from "@/features/marketing/shared/primitives";

type SolutionDetail = {
  proof: string[];
  title: string;
  accent: string;
  text: string;
  caller: string;
};

const solutionDetails: Record<string, SolutionDetail> = {
  "appointment-booking": {
    proof: ["Availability before confirmation", "Calendar event creation", "Confirmation messages"],
    title: "Caller intent,",
    accent: "turned into a reserved slot.",
    text: "The agent gathers the service need, checks real availability, presents valid times, books the chosen slot, and triggers confirmations without double-booking.",
    caller: "Can I book a cleaning on Friday morning?",
  },
  "customer-service": {
    proof: ["Policy-grounded answers", "Escalation when needed", "After-call summaries"],
    title: "Routine calls resolved,",
    accent: "humans kept for exceptions.",
    text: "Add approved business information, define escalation instructions, and review the answers in each call transcript.",
    caller: "What time do you close on Saturdays?",
  },
  "lead-qualification": {
    proof: ["Guided discovery", "Caller intent", "Useful call summaries"],
    title: "Capture the details",
    accent: "missed calls used to lose.",
    text: "Define your discovery questions, capture budget and timing in the conversation, and review the summary before following up.",
    caller: "I'm looking for a three-bedroom to rent next month.",
  },
  "human-transfer": {
    proof: ["Explicit transfer instructions", "Your team's number", "Reviewable call context"],
    title: "The right person,",
    accent: "with the full context.",
    text: "Enable live transfer to your team's number, explain when to escalate, and define what the agent should say when nobody is available.",
    caller: "I need to speak to someone about my bill.",
  },
  "multilingual-call-handling": {
    proof: ["Language detection", "Provider routing", "Natural interruption handling"],
    title: "Callers speak naturally,",
    accent: "in their own language.",
    text: "The call pipeline detects language, selects speech and voice behavior, and keeps the conversation natural across supported languages and accents.",
    caller: "Habari, naomba kuongea na daktari.",
  },
  "after-hours-answering": {
    proof: ["Weekly operating hours", "Three response policies", "Messages for follow-up"],
    title: "Still responsive",
    accent: "when the team is offline.",
    text: "Choose whether to keep answering, collect a message, or announce closure outside your weekly operating hours.",
    caller: "Are you open? My tooth has been hurting all night.",
  },
};

const solutionAccents = ["94 234 212", "96 165 250", "167 139 250", "251 191 36", "94 234 212", "96 165 250"];

/* ---------- /solutions/[slug] ---------- */

export function SolutionDetailPage({ slug }: { slug: string }) {
  const destination = pageFor("solutions", slug);
  if (!destination || !isSolutionSlug(destination.slug)) return null;
  const detail = solutionDetails[destination.slug];
  if (!detail) return null;
  const Icon = destination.icon;
  const group = destination.group;
  const related = group.items.filter(([itemSlug]) => itemSlug !== slug).slice(0, 3);

  return (
    <MarketingPage>
      <PageHero
        eyebrow={<span className="inline-flex items-center gap-2"><Icon className="size-3.5" aria-hidden="true" /> Solutions · {destination.title}</span>}
        title={detail.title}
        accent={detail.accent}
        description={destination.description}
        actions={
          <>
            <Button asChild size="lg">
              <Link href="/request-demo">
                Book a demo <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <a href="#product-walkthrough">Explore the walkthrough</a>
            </Button>
          </>
        }
        glowSeed={20 + slug.length}
      >
        <div className="mx-auto w-full max-w-[480px] lg:mr-0"><SolutionVisual slug={destination.slug} /><p className="mt-3 text-center font-mono text-[0.6rem] uppercase tracking-wider text-fg-muted">Illustrative preview · Sample data</p></div>
      </PageHero>

      <section aria-label="Highlights" className="border-y border-line">
        <div className="mx-auto grid max-w-[1240px] gap-px bg-line sm:grid-cols-3">
          {detail.proof.map((item) => (
            <p key={item} className="flex items-center gap-3 bg-canvas px-6 py-6 text-sm text-fg sm:px-8">
              <CheckCircle2 className="size-4 shrink-0 text-signal" aria-hidden="true" />
              {item}
            </p>
          ))}
        </div>
      </section>

      <SolutionExperience key={destination.slug} slug={destination.slug} />

      <Section>
        <SectionHeading eyebrow="Keep exploring" title="More" accent="solutions." action={<ArrowAll />} />
        <div className="mt-12 grid gap-4 md:grid-cols-3">
          {related.map(([itemSlug, title, description, RelatedIcon], index) => (
            <Reveal key={itemSlug} delay={index * 0.06} className="h-full">
              <Link href={`/solutions/${itemSlug}`} className="block h-full">
                <SpotlightCard accent={solutionAccents[index]}>
                  <div className="flex items-start justify-between">
                    <IconTile accent={solutionAccents[index]}>
                      <RelatedIcon aria-hidden="true" />
                    </IconTile>
                    <ArrowUpRight className="size-5 text-fg-muted transition-colors group-hover:text-white" aria-hidden="true" />
                  </div>
                  <h3 className="mt-8 text-lg font-medium text-white">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-fg-soft">{description}</p>
                </SpotlightCard>
              </Link>
            </Reveal>
          ))}
        </div>
      </Section>

      <CtaBand title="Launch one" accent="workflow," titleEnd="measure it, then expand." seed={21} />
    </MarketingPage>
  );
}

function ArrowAll() {
  return (
    <Link href="/solutions" className="group inline-flex items-center gap-1.5 rounded-full border border-line px-4 py-2 text-sm text-fg-soft transition-colors hover:border-line-strong hover:text-fg">
      All solutions <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
    </Link>
  );
}

/* ---------- /solutions ---------- */

export function SolutionsOverviewPage() {
  const group = groupFor("solutions");
  if (!group) return null;
  const path = sectionPathFor(group);
  const [first, ...rest] = group.items;
  const FirstIcon = first[3];
  const firstDetail = solutionDetails[first[0]];

  return (
    <MarketingPage>
      <PageHero
        align="center"
        eyebrow="Solutions"
        title="Business workflows that turn"
        accent="calls into outcomes."
        description="Appointments, support, qualification, safe transfer, multilingual handling and after-hours coverage, each built around the result your team needs."
        actions={
          <>
            <Button asChild size="lg">
              <Link href="/request-demo">
                Book a demo <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <a href="#all-solutions">Browse solutions</a>
            </Button>
          </>
        }
        glowSeed={22}
      />

      <Section id="all-solutions" bordered={false} className="-mt-10">
        <div className="grid gap-4 lg:grid-cols-6">
          {/* Featured */}
          <Reveal className="lg:col-span-6">
            <Link href={`/${path}/${first[0]}`} className="block">
              <SpotlightCard className="grid gap-10 p-7 sm:p-10 lg:grid-cols-[1fr_1fr] lg:items-center">
                <div>
                  <IconTile>
                    <FirstIcon aria-hidden="true" />
                  </IconTile>
                  <p className="mt-8 font-mono text-[0.65rem] uppercase tracking-[0.18em] text-signal">Most requested</p>
                  <h2 className="mt-3 text-3xl font-medium tracking-tight text-white sm:text-4xl">{first[1]}</h2>
                  <p className="mt-3 max-w-md leading-relaxed text-fg-soft">{firstDetail?.text ?? first[2]}</p>
                  <span className="mt-8 inline-flex items-center gap-1.5 text-sm text-white">
                    Explore {first[1].toLowerCase()} <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                  </span>
                </div>
                <SolutionVisual slug="appointment-booking" />
              </SpotlightCard>
            </Link>
          </Reveal>

          {rest.map(([slug, title, description, Icon], index) => (
            <Reveal key={slug} delay={index * 0.05} className={cn("h-full", index < 2 ? "lg:col-span-3" : "lg:col-span-2")}>
              <Link href={`/${path}/${slug}`} className="block h-full">
                <SpotlightCard accent={solutionAccents[index + 1]}>
                  <div className="flex items-start justify-between">
                    <IconTile accent={solutionAccents[index + 1]}>
                      <Icon aria-hidden="true" />
                    </IconTile>
                    <ArrowUpRight className="size-5 text-fg-muted transition-colors group-hover:text-white" aria-hidden="true" />
                  </div>
                  <h3 className="mt-10 text-xl font-medium tracking-tight text-white">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-fg-soft">{description}</p>
                  {isSolutionSlug(slug) && <div className="mt-6"><SolutionVisual slug={slug} /></div>}
                  {solutionDetails[slug] ? (
                    <p className="mt-6 border-t border-line pt-4 font-mono text-[0.68rem] text-fg-muted">
                      &ldquo;{solutionDetails[slug].caller}&rdquo;
                    </p>
                  ) : null}
                </SpotlightCard>
              </Link>
            </Reveal>
          ))}
        </div>
      </Section>

      <CtaBand seed={23} />
    </MarketingPage>
  );
}
