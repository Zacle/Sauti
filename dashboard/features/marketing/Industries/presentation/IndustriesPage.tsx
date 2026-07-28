import type { CSSProperties, ComponentType } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  ArrowRight,
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
} from "lucide-react";
import {
  industries,
  industryFor,
  type IndustryContent,
  type IndustryIconKey,
} from "@/features/marketing/Industries/domain/industry-content";
import styles from "./IndustriesPage.module.css";

const icons: Record<IndustryIconKey, ComponentType<{ size?: number; strokeWidth?: number }>> = {
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

const industryIcons: Record<string, ComponentType<{ size?: number; strokeWidth?: number }>> = {
  "clinics-healthcare": Stethoscope,
  "salons-beauty": Scissors,
  "real-estate": Building2,
  "professional-services": BriefcaseBusiness,
  education: GraduationCap,
  "local-businesses": Wrench,
};

function IconFor({ name, size = 20 }: { name: IndustryIconKey; size?: number }) {
  const Icon = icons[name];
  return <Icon size={size} strokeWidth={1.8} />;
}

function LiveCallPanel({ industry }: { industry: IndustryContent }) {
  return (
    <aside className={styles.livePanel} aria-label={`Example ${industry.label} call handled by Sauti`}>
      <div className={styles.liveHeader}>
        <span>
          <CheckCircle2 size={12} fill="currentColor" />
          Live call
        </span>
        <span>01:24 <AudioLines size={14} /></span>
      </div>

      <div className={styles.callBlock}>
        <strong>{industry.call.heading}</strong>
        <div className={styles.speech}>
          <span><AudioLines size={14} /> {industry.call.callerLabel}</span>
          <p>{industry.call.caller}</p>
        </div>
        <div className={`${styles.speech} ${styles.sautiSpeech}`}>
          <span><Headphones size={14} /> Sauti</span>
          <p>{industry.call.agent}</p>
        </div>
        <div className={styles.callOutcome}>
          <CheckCircle2 size={20} />
          <span>
            <strong>{industry.call.outcome}</strong>
            <small>{industry.call.outcomeDetail}</small>
          </span>
        </div>
      </div>

      <div className={styles.secondaryCall}>
        <strong>{industry.call.secondaryHeading}</strong>
        <p>{industry.call.secondaryCaller}</p>
        <span><Route size={14} /> {industry.call.secondaryOutcome}</span>
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
  return (
    <div className={`${styles.transformationColumn} ${styles[tone]}`}>
      <div className={styles.columnHeading}>
        <span>{tone === "before" ? "Without Sauti" : "With Sauti"}</span>
        <p>{lead}</p>
      </div>
      <div className={styles.stepRow}>
        {steps.map((step, index) => (
          <article key={step.title}>
            <div className={styles.stepIcon}><IconFor name={step.icon} size={24} /></div>
            <strong>{step.title}</strong>
            <small>{step.detail}</small>
            {index < steps.length - 1 ? <ArrowRight className={styles.stepArrow} size={17} /> : null}
          </article>
        ))}
      </div>
    </div>
  );
}

export function IndustryDetailPage({ slug }: { slug: string }) {
  const industry = industryFor(slug);
  if (!industry) return null;

  const Icon = industryIcons[industry.slug] ?? Sparkles;
  const related = industries.filter((item) => item.slug !== industry.slug).slice(0, 3);
  const theme = { "--industry-accent": industry.accent } as CSSProperties;

  return (
    <main
      className={`${styles.page} ${styles.detailPage} ${styles[`variant_${industry.variant}`]}`}
      style={theme}
    >
      <section className={styles.hero}>
        <Image
          className={styles.heroImage}
          src={industry.image}
          alt={industry.imageAlt}
          fill
          priority
          sizes="100vw"
        />
        <div className={styles.heroShade} />

        <div className={styles.heroCopy}>
          <div className={styles.eyebrow}><Icon size={16} /> {industry.label}</div>
          <h1>
            {industry.heroPrefix}
            <span>{industry.heroHighlight}</span>
            {industry.heroSuffix}
          </h1>
          <p>{industry.description}</p>
          <div className={styles.heroActions}>
            <Link className={styles.primaryButton} href="https://cal.com/sauti/demo" target="_blank">
              Book a demo <ArrowRight size={17} />
            </Link>
            <Link className={styles.textButton} href="#industry-workflow">
              See how it works <ArrowRight size={16} />
            </Link>
          </div>
          <div className={styles.proofRow}>
            {industry.proof.map((item) => (
              <span key={item}><Check size={15} /> {item}</span>
            ))}
          </div>
        </div>

        <LiveCallPanel industry={industry} />
      </section>

      <section className={styles.transformation} id="industry-workflow">
        <div className={styles.transformationTitle}>
          <h2>
            {industry.transformation.titlePrefix}
            <span>{industry.transformation.titleHighlight}</span>
          </h2>
        </div>
        <div className={styles.transformationGrid}>
          <TransformationColumn tone="before" lead={industry.transformation.beforeLead} steps={industry.transformation.before} />
          <div className={styles.transformationPivot}><ArrowRight size={20} /></div>
          <TransformationColumn tone="after" lead={industry.transformation.afterLead} steps={industry.transformation.after} />
        </div>
        <div className={styles.trustLine}>
          <ShieldCheck size={25} />
          <span>
            <strong>Designed around clear operating boundaries.</strong>
            <small>Your team decides what the agent answers, completes, and escalates.</small>
          </span>
        </div>
      </section>

      <section className={styles.operatingSection}>
        <header className={styles.sectionIntro}>
          <span>{industry.sectionEyebrow}</span>
          <h2>{industry.sectionTitle}</h2>
          <p>{industry.sectionDescription}</p>
        </header>

        <div className={styles.pillarList}>
          {industry.pillars.map((pillar, index) => (
            <article key={pillar.title}>
              <span>{String(index + 1).padStart(2, "0")} · {pillar.eyebrow}</span>
              <h3>{pillar.title}</h3>
              <p>{pillar.description}</p>
            </article>
          ))}
        </div>

        <div className={styles.systemStrip}>
          <span>Connect the workflow</span>
          <div>
            {industry.systems.map((system) => <strong key={system}>{system}</strong>)}
          </div>
        </div>
      </section>

      <section className={styles.relatedSection}>
        <header>
          <span>Other industries</span>
          <h2>The workflow changes when the work changes.</h2>
        </header>
        <div className={styles.relatedGrid}>
          {related.map((item) => {
            const RelatedIcon = industryIcons[item.slug] ?? Sparkles;
            return (
              <Link href={`/industries/${item.slug}`} key={item.slug}>
                <Image src={item.image} alt="" fill sizes="(max-width: 760px) 100vw, 33vw" />
                <span className={styles.relatedShade} />
                <div>
                  <RelatedIcon size={20} />
                  <strong>{item.label}</strong>
                  <small>{item.cardDescription}</small>
                  <b>Explore <ArrowRight size={14} /></b>
                </div>
              </Link>
            );
          })}
        </div>
      </section>

      <section className={styles.finalCta}>
        <span>{industry.label}</span>
        <h2>{industry.finalTitle}</h2>
        <p>{industry.finalDescription}</p>
        <Link className={styles.primaryButton} href="https://cal.com/sauti/demo" target="_blank">
          Plan your pilot <ArrowRight size={17} />
        </Link>
      </section>
    </main>
  );
}

export function IndustriesOverviewPage() {
  return (
    <main className={`${styles.page} ${styles.overviewPage}`}>
      <section className={styles.overviewHero}>
        <div className={styles.overviewCopy}>
          <div className={styles.eyebrow}><Sparkles size={16} /> Built for the work behind the call</div>
          <h1>One voice platform. <span>Six very different front doors.</span></h1>
          <p>
            A patient, salon client, property buyer, prospective student, and emergency customer do not need the same
            conversation. Sauti shapes the agent around the rules, language, systems, and handoffs of each industry.
          </p>
          <div className={styles.heroActions}>
            <Link className={styles.primaryButton} href="#industry-directory">
              Find your industry <ArrowRight size={17} />
            </Link>
            <Link className={styles.textButton} href="https://cal.com/sauti/demo" target="_blank">
              Book a demo <ArrowRight size={16} />
            </Link>
          </div>
        </div>

        <div className={styles.overviewMosaic} aria-label="Teams using Sauti across different industries">
          {industries.slice(0, 3).map((industry, index) => (
            <Link
              className={index === 0 ? styles.mosaicLead : ""}
              href={`/industries/${industry.slug}`}
              key={industry.slug}
            >
              <Image src={industry.image} alt={industry.imageAlt} fill priority={index === 0} sizes="(max-width: 900px) 100vw, 42vw" />
              <span>{industry.label} <ArrowRight size={14} /></span>
            </Link>
          ))}
        </div>
      </section>

      <section className={styles.overviewPrinciples}>
        <article><Languages size={22} /><strong>Speak like the caller</strong><span>Language and tone belong to the agent, not a hard-coded workflow.</span></article>
        <article><Route size={22} /><strong>Follow the operating rules</strong><span>Each industry decides what gets answered, completed, or handed to a person.</span></article>
        <article><CheckCircle2 size={22} /><strong>Finish with a visible outcome</strong><span>Bookings, callbacks, leads, and transfers are recorded instead of merely promised.</span></article>
      </section>

      <section className={styles.directory} id="industry-directory">
        <header className={styles.directoryHeader}>
          <span>Choose your operating environment</span>
          <h2>Start with the calls your team already receives.</h2>
          <p>Every page below has its own caller journey, workflow boundaries, integrations, and proof—not recycled feature copy.</p>
        </header>

        <div className={styles.directoryGrid}>
          {industries.map((industry, index) => {
            const Icon = industryIcons[industry.slug] ?? Sparkles;
            const theme = { "--industry-accent": industry.accent } as CSSProperties;
            return (
              <Link
                className={styles.directoryCard}
                href={`/industries/${industry.slug}`}
                key={industry.slug}
                style={theme}
              >
                <Image src={industry.image} alt={industry.imageAlt} fill sizes="(max-width: 760px) 100vw, 50vw" />
                <span className={styles.directoryShade} />
                <span className={styles.directoryIndex}>{String(index + 1).padStart(2, "0")}</span>
                <div>
                  <Icon size={23} />
                  <h3>{industry.label}</h3>
                  <p>{industry.cardDescription}</p>
                  <strong>Explore the workflow <ArrowRight size={15} /></strong>
                </div>
              </Link>
            );
          })}
        </div>
      </section>

      <section className={styles.overviewCta}>
        <div>
          <span>Not seeing your exact industry?</span>
          <h2>Model the calls, rules, and outcomes that make your business different.</h2>
        </div>
        <Link className={styles.primaryButton} href="https://cal.com/sauti/demo" target="_blank">
          Design your workflow <ArrowRight size={17} />
        </Link>
      </section>
    </main>
  );
}
