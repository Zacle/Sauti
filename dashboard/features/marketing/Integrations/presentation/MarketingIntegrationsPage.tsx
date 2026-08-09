import type { CSSProperties, ComponentType } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  Activity,
  ArrowRight,
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
import styles from "./MarketingIntegrationsPage.module.css";

const iconByKey: Record<IntegrationIconKey, ComponentType<{ size?: number; strokeWidth?: number }>> = {
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

function IconFor({ name, size = 20 }: { name: IntegrationIconKey; size?: number }) {
  const Icon = iconByKey[name];
  return <Icon size={size} strokeWidth={1.8} />;
}

function ProviderRow({ providers }: { providers: MarketingIntegration["providers"] }) {
  return (
    <div className={styles.providerRow} aria-label="Supported providers">
      {providers.map((provider) => (
        <span key={provider.name}>
          {provider.logo ? <Image src={provider.logo} alt="" width={22} height={22} /> : <Bot size={19} />}
          {provider.name}
        </span>
      ))}
    </div>
  );
}

function FlowPanel({ integration }: { integration: MarketingIntegration }) {
  return (
    <aside className={styles.flowPanel} aria-label={`${integration.label} workflow`}>
      <header>
        <span><span className={styles.liveDot} /> Connected workflow</span>
        <small>Live</small>
      </header>
      <div className={styles.flowNodes}>
        {integration.flow.steps.map((step, index) => (
          <div className={styles.flowNode} key={step.label}>
            <span><IconFor name={step.icon} size={18} /></span>
            <div>
              <small>{String(index + 1).padStart(2, "0")}</small>
              <strong>{step.label}</strong>
              <p>{step.detail}</p>
            </div>
            {index < integration.flow.steps.length - 1 ? <ArrowRight className={styles.nodeArrow} size={16} /> : null}
          </div>
        ))}
      </div>
      <div className={styles.flowResult}>
        <CheckCircle2 size={20} />
        <span><strong>{integration.flow.result}</strong><small>{integration.flow.resultDetail}</small></span>
      </div>
    </aside>
  );
}

export function MarketingIntegrationDetailPage({ slug }: { slug: string }) {
  const integration = marketingIntegrationFor(slug);
  if (!integration) return null;

  const Icon = iconByKey[integration.icon];
  const related = marketingIntegrations.filter((item) => item.slug !== slug).slice(0, 3);
  const theme = { "--integration-accent": integration.accent } as CSSProperties;

  return (
    <main className={`${styles.page} ${styles.detailPage}`} style={theme}>
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroCopy}>
          <div className={styles.eyebrow}><Icon size={16} /> {integration.label}</div>
          <h1>{integration.heroPrefix}<span>{integration.heroHighlight}</span>{integration.heroSuffix}</h1>
          <p>{integration.description}</p>
          <div className={styles.heroActions}>
            <Link className={styles.primaryButton} href="/request-demo">
              Book a demo <ArrowRight size={17} />
            </Link>
            <Link className={styles.secondaryButton} href="#integration-flow">
              Follow the workflow <ArrowRight size={16} />
            </Link>
          </div>
          <div className={styles.proofRow}>
            {integration.proof.map((proof) => <span key={proof}><Check size={14} /> {proof}</span>)}
          </div>
        </div>
        <FlowPanel integration={integration} />
      </section>

      <section className={styles.providerBand}>
        <span>Works with</span>
        <ProviderRow providers={integration.providers} />
        <small>Connections are workspace-owned. Agents receive only the tools you enable.</small>
      </section>

      <section className={styles.flowSection} id="integration-flow">
        <header className={styles.sectionIntro}>
          <span>{integration.flow.eyebrow}</span>
          <h2>{integration.flow.title}</h2>
          <p>{integration.flow.description}</p>
        </header>
        <div className={styles.largeFlow}>
          {integration.flow.steps.map((step, index) => (
            <article key={step.label}>
              <div><IconFor name={step.icon} size={24} /></div>
              <small>{String(index + 1).padStart(2, "0")}</small>
              <h3>{step.label}</h3>
              <p>{step.detail}</p>
              {index < integration.flow.steps.length - 1 ? <ArrowRight size={19} /> : null}
            </article>
          ))}
        </div>
      </section>

      <section className={styles.valueSection}>
        <div className={styles.valueCopy}>
          <span>{integration.value.eyebrow}</span>
          <h2>{integration.value.title}</h2>
          <p>{integration.value.description}</p>
          <div className={styles.safeguards}>
            <strong><ShieldCheck size={18} /> Built-in safeguards</strong>
            {integration.safeguards.map((item) => <span key={item}><Check size={14} /> {item}</span>)}
          </div>
        </div>
        <div className={styles.capabilityList}>
          {integration.value.capabilities.map((capability, index) => (
            <article key={capability.title}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <div><h3>{capability.title}</h3><p>{capability.description}</p></div>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.relatedSection}>
        <header><span>Explore the stack</span><h2>Every integration has one clear job.</h2></header>
        <div>
          {related.map((item) => {
            const RelatedIcon = iconByKey[item.icon];
            return (
              <Link href={`/integrations/${item.slug}`} key={item.slug} style={{ "--card-accent": item.accent } as CSSProperties}>
                <RelatedIcon size={22} />
                <strong>{item.label}</strong>
                <p>{item.cardDescription}</p>
                <span>Explore <ArrowRight size={14} /></span>
              </Link>
            );
          })}
        </div>
      </section>

      <section className={styles.finalCta}>
        <span>Start with one connection</span>
        <h2>{integration.finalTitle}</h2>
        <p>{integration.finalDescription}</p>
        <Link className={styles.primaryButton} href="/request-demo">
          Plan your pilot <ArrowRight size={17} />
        </Link>
      </section>
    </main>
  );
}

export function MarketingIntegrationsOverviewPage() {
  const featured = marketingIntegrations.slice(0, 3);
  const remaining = marketingIntegrations.slice(3);

  return (
    <main className={`${styles.page} ${styles.overviewPage}`}>
      <section className={styles.overviewHero}>
        <div className={styles.overviewCopy}>
          <div className={styles.eyebrow}><Sparkles size={16} /> Connected stack</div>
          <h1>Connect the systems that turn a conversation into <span>completed work.</span></h1>
          <p>
            Sauti separates live voice, business decisions, and external delivery—so every provider has a clear role
            and every action remains observable.
          </p>
          <div className={styles.heroActions}>
            <Link className={styles.primaryButton} href="/integrations/calendars">
              Explore a booking flow <ArrowRight size={17} />
            </Link>
            <Link className={styles.secondaryButton} href="/request-demo">Book a demo</Link>
          </div>
        </div>
        <div className={styles.stackMap} aria-label="Sauti connected stack">
          <div className={styles.stackCore}><Sparkles size={27} /><strong>Sauti</strong><small>Orchestrates the outcome</small></div>
          <div className={styles.stackRing}>
            {marketingIntegrations.map((item) => {
              const Icon = iconByKey[item.icon];
              return <span key={item.slug} style={{ "--node-accent": item.accent } as CSSProperties}><Icon size={19} /><small>{item.shortLabel}</small></span>;
            })}
          </div>
        </div>
      </section>

      <section className={styles.principles}>
        <article><ShieldCheck size={23} /><strong>Safe by boundary</strong><p>Models propose; Sauti authorizes; tools report fact.</p></article>
        <article><Database size={23} /><strong>Durable by default</strong><p>Primary records are saved before optional external delivery.</p></article>
        <article><Activity size={23} /><strong>Visible in operation</strong><p>Connection health and delivery outcomes stay inspectable.</p></article>
      </section>

      <section className={styles.directory}>
        <header className={styles.sectionIntro}>
          <span>Integration layers</span>
          <h2>Choose the part of the call stack you need to connect.</h2>
          <p>These are operating layers, not a wall of interchangeable logos.</p>
        </header>
        <div className={styles.featuredGrid}>
          {featured.map((item, index) => {
            const Icon = iconByKey[item.icon];
            return (
              <Link href={`/integrations/${item.slug}`} key={item.slug} style={{ "--card-accent": item.accent } as CSSProperties}>
                <span>{String(index + 1).padStart(2, "0")}</span>
                <Icon size={28} />
                <h3>{item.label}</h3>
                <p>{item.cardDescription}</p>
                <ProviderRow providers={item.providers} />
                <b>Explore layer <ArrowRight size={15} /></b>
              </Link>
            );
          })}
        </div>
        <div className={styles.secondaryGrid}>
          {remaining.map((item) => {
            const Icon = iconByKey[item.icon];
            return (
              <Link href={`/integrations/${item.slug}`} key={item.slug} style={{ "--card-accent": item.accent } as CSSProperties}>
                <Icon size={23} />
                <div><strong>{item.label}</strong><p>{item.cardDescription}</p></div>
                <ArrowRight size={17} />
              </Link>
            );
          })}
        </div>
      </section>

      <section className={styles.finalCta}>
        <span>Connect deliberately</span>
        <h2>Start with the outcome your team needs after the call.</h2>
        <p>Then enable only the providers and agent tools required to complete it.</p>
        <Link className={styles.primaryButton} href="/request-demo">
          Map your workflow <ArrowRight size={17} />
        </Link>
      </section>
    </main>
  );
}
