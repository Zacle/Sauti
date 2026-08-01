"use client";

import type { ComponentType, FormEvent } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useMemo, useState } from "react";
import {
  Activity,
  ArrowRight,
  BarChart3,
  BookOpenCheck,
  Braces,
  CalendarCheck2,
  Check,
  CheckCircle2,
  Clock3,
  Code2,
  Database,
  FileQuestion,
  FileText,
  Globe2,
  KeyRound,
  LockKeyhole,
  MessageSquareText,
  PhoneCall,
  Route,
  Search,
  ShieldCheck,
  Sparkles,
  UsersRound,
  Webhook,
  X,
} from "lucide-react";
import {
  audienceOptions,
  launchSteps,
  popularJourneys,
  resourceFor,
  resourceNavItems,
  resourcePages,
  searchSuggestions,
  trustLinks,
  type AudienceId,
  type ResourceIconKey,
  type ResourcePageContent,
  type ResourceSlug,
} from "@/features/marketing/Resources/domain/resource-content";
import styles from "./MarketingResourcesPage.module.css";

const ResourceSearchMotion = dynamic(
  () => import("./ResourceSearchMotion").then((module) => module.ResourceSearchMotion),
  { ssr: false },
);

const iconByKey: Record<ResourceIconKey, ComponentType<{ size?: number; strokeWidth?: number }>> = {
  activity: Activity,
  analytics: BarChart3,
  api: Braces,
  article: MessageSquareText,
  book: BookOpenCheck,
  calendar: CalendarCheck2,
  calls: PhoneCall,
  check: CheckCircle2,
  clock: Clock3,
  code: Code2,
  database: Database,
  document: FileText,
  faq: FileQuestion,
  globe: Globe2,
  key: KeyRound,
  lock: LockKeyhole,
  route: Route,
  search: Search,
  shield: ShieldCheck,
  sparkles: Sparkles,
  users: UsersRound,
  webhook: Webhook,
};

function ResourceIcon({ name, size = 20 }: { name: ResourceIconKey; size?: number }) {
  const Icon = iconByKey[name];
  return <Icon size={size} strokeWidth={1.8} />;
}

function BrowseRail({ active }: { active?: ResourceSlug }) {
  return (
    <aside className={styles.browseRail} aria-label="Browse resources">
      <strong className={styles.railTitle}>Browse</strong>
      <nav>
        {resourceNavItems.map((item) => (
          <Link
            className={item.slug === active ? styles.activeResource : ""}
            href={`/resources/${item.slug}`}
            key={item.slug}
          >
            <ResourceIcon name={item.icon} size={22} />
            <span>
              <strong>{item.label}</strong>
              <small>{item.shortDescription}</small>
            </span>
          </Link>
        ))}
      </nav>
      <div className={styles.supportCard}>
        <MessageSquareText size={25} />
        <div>
          <strong>Can’t find what you need?</strong>
          <span>Ask our team for help.</span>
        </div>
        <a href="mailto:support@sauti.uk">Contact support <ArrowRight size={14} /></a>
      </div>
    </aside>
  );
}

function TrustRail({ compact = false }: { compact?: boolean }) {
  return (
    <aside className={`${styles.trustRail} ${compact ? styles.compactTrustRail : ""}`} aria-label="Sauti trust center">
      <header>
        <strong>Trust center</strong>
        <p>Proof points and controls that keep workspace data and call operations safe.</p>
      </header>
      <div className={styles.trustList}>
        {trustLinks.map((item) => (
          <Link href={item.href} key={item.title}>
            <ResourceIcon name={item.icon} size={20} />
            <span><strong>{item.title}</strong><small>{item.description}</small></span>
            <ArrowRight size={15} />
          </Link>
        ))}
      </div>
      <Link className={styles.trustCta} href="/resources/security">Go to Security <ArrowRight size={14} /></Link>
      {!compact ? (
        <div className={styles.builderCard}>
          <Braces size={23} />
          <strong>For builders</strong>
          <span>Go deeper with REST APIs, webhook contracts, and implementation guidance.</span>
          <Link href="/resources/api-reference">Explore API Reference <ArrowRight size={14} /></Link>
        </div>
      ) : null}
    </aside>
  );
}

function AudienceSelector({ audience, onChange }: { audience: AudienceId; onChange: (value: AudienceId) => void }) {
  return (
    <div className={styles.audienceSelector} aria-label="Choose your resource perspective">
      {audienceOptions.map((option) => (
        <button
          aria-pressed={audience === option.id}
          className={audience === option.id ? styles.activeAudience : ""}
          key={option.id}
          onClick={() => onChange(option.id)}
          type="button"
        >
          <ResourceIcon name={option.icon} size={17} />
          {option.label}
        </button>
      ))}
    </div>
  );
}

function GuidePanel({ audience, searchVersion }: { audience: AudienceId; searchVersion: number }) {
  const contextualStep = audience === "builder" ? 2 : audience === "security" ? 4 : 1;
  return (
    <section className={styles.guidePanel} aria-labelledby="featured-guide-title">
      <div className={styles.guideMotion} aria-hidden="true">
        <ResourceSearchMotion audience={audience} searchVersion={searchVersion} />
      </div>
      <div className={styles.guideIntro}>
        <span><Sparkles size={13} /> Featured guide</span>
        <h2 id="featured-guide-title">Launch your first multilingual agent</h2>
        <p>A practical path from idea to a working voice agent that can take calls, answer questions, and book appointments.</p>
        <div className={styles.guideMeta}>
          <span><Route size={14} /> 6 steps</span>
          <span><Clock3 size={14} /> 30–45 min</span>
          <span><UsersRound size={14} /> Business owner</span>
        </div>
        <Link className={styles.primaryButton} href="/resources/documentation#agent-setup">Start guide <ArrowRight size={16} /></Link>
      </div>
      <ol className={styles.launchSteps}>
        {launchSteps.map((step, index) => (
          <li className={index + 1 === contextualStep ? styles.activeStep : ""} key={step}>
            <span>{index + 1}</span><strong>{step}</strong>
          </li>
        ))}
      </ol>
      <div className={styles.popularBlock}>
        <div className={styles.blockHeading}><strong>Popular journeys</strong><Link href="/resources/documentation">View all</Link></div>
        <div className={styles.journeyGrid}>
          {popularJourneys.map((journey) => (
            <Link className={styles[`tone_${journey.tone}`]} href={journey.href} key={journey.title}>
              <ResourceIcon name={journey.icon} size={25} />
              <strong>{journey.title}</strong>
              <p>{journey.description}</p>
              <span>{journey.meta}<ArrowRight size={15} /></span>
            </Link>
          ))}
        </div>
      </div>
      <div className={styles.updatedBlock}>
        <strong>Recently updated</strong>
        <Link href="/resources/security#webhooks">
          <FileText size={19} />
          <span><strong>Webhook signing for event verification</strong><small>Security · Updated for the current provider boundary</small></span>
          <b>Read guide <ArrowRight size={14} /></b>
        </Link>
      </div>
    </section>
  );
}

export function MarketingResourcesOverviewPage() {
  const [audience, setAudience] = useState<AudienceId>("owner");
  const [query, setQuery] = useState(searchSuggestions.owner[0]);
  const [searchVersion, setSearchVersion] = useState(0);
  const [submittedQuery, setSubmittedQuery] = useState(searchSuggestions.owner[0]);

  const resultCount = audience === "security" ? 5 : 3;
  const placeholder = useMemo(() => searchSuggestions[audience][0], [audience]);

  function changeAudience(nextAudience: AudienceId) {
    setAudience(nextAudience);
    const nextQuery = searchSuggestions[nextAudience][0];
    setQuery(nextQuery);
    setSubmittedQuery(nextQuery);
    setSearchVersion((current) => current + 1);
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuery = query.trim() || placeholder;
    setQuery(nextQuery);
    setSubmittedQuery(nextQuery);
    setSearchVersion((current) => current + 1);
  }

  return (
    <main className={`${styles.page} ${styles.overviewPage}`}>
      <section className={styles.overviewHero}>
        <div className={styles.overviewEyebrow}><Sparkles size={15} /> Resources console</div>
        <h1>Find the answer. See the proof. Ship with confidence.</h1>
        <p>Practical guides, references, and proof points to help you plan, build, and operate with Sauti.</p>
        <form className={styles.searchForm} onSubmit={submitSearch} role="search">
          <Search size={21} />
          <label className={styles.srOnly} htmlFor="resource-search">Search Sauti resources</label>
          <input
            id="resource-search"
            onChange={(event) => setQuery(event.target.value)}
            placeholder={placeholder}
            value={query}
          />
          {query ? <button aria-label="Clear search" className={styles.clearSearch} onClick={() => setQuery("")} type="button"><X size={18} /></button> : null}
          <button aria-label="Search resources" className={styles.submitSearch} type="submit"><ArrowRight size={19} /></button>
        </form>
        <AudienceSelector audience={audience} onChange={changeAudience} />
        <div className={styles.searchStatus} aria-live="polite">
          <Sparkles size={16} /> {resultCount} results found for “{submittedQuery}”
        </div>
      </section>

      <section className={styles.consoleGrid}>
        <BrowseRail active="documentation" />
        <GuidePanel audience={audience} searchVersion={searchVersion} />
        <TrustRail />
      </section>
    </main>
  );
}

function DetailHero({ resource }: { resource: ResourcePageContent }) {
  return (
    <header className={styles.detailHero}>
      <div className={styles.detailEyebrow}><ResourceIcon name={resource.icon} size={16} /> {resource.eyebrow}</div>
      <h1>{resource.title}</h1>
      <p>{resource.description}</p>
      <div className={styles.detailMeta}>
        <span><Clock3 size={15} /> {resource.readingTime}</span>
        <span><UsersRound size={15} /> {resource.audience}</span>
      </div>
      <Link className={styles.primaryButton} href={resource.primaryHref}>{resource.primaryLabel} <ArrowRight size={16} /></Link>
    </header>
  );
}

function StandardSections({ resource }: { resource: ResourcePageContent }) {
  return (
    <div className={styles.sectionList}>
      {resource.sections.map((section) => (
        <section id={section.id} key={section.id}>
          <div className={styles.sectionIcon}><ResourceIcon name={section.icon} size={24} /></div>
          <div>
            <span>{section.eyebrow}</span>
            <h2>{section.title}</h2>
            <p>{section.description}</p>
            <ul>{section.bullets.map((bullet) => <li key={bullet}><Check size={15} /> {bullet}</li>)}</ul>
          </div>
        </section>
      ))}
    </div>
  );
}

function FaqSections({ resource }: { resource: ResourcePageContent }) {
  return (
    <div className={styles.faqList}>
      {resource.sections.map((section, index) => (
        <details id={section.id} key={section.id} open={index === 0}>
          <summary>
            <span>{section.eyebrow}</span>
            <strong>{section.title}</strong>
            <span className={styles.faqPlus}>+</span>
          </summary>
          <div>
            <p>{section.description}</p>
            <ul>{section.bullets.map((bullet) => <li key={bullet}><Check size={15} /> {bullet}</li>)}</ul>
          </div>
        </details>
      ))}
    </div>
  );
}

function ApiSections({ resource }: { resource: ResourcePageContent }) {
  return (
    <div className={styles.apiList}>
      {resource.sections.map((section) => (
        <section id={section.id} key={section.id}>
          <header><ResourceIcon name={section.icon} size={21} /><span>{section.eyebrow}</span></header>
          <h2>{section.title}</h2>
          <p>{section.description}</p>
          <div>{section.bullets.map((bullet) => <code key={bullet}>{bullet}</code>)}</div>
        </section>
      ))}
    </div>
  );
}

function OnThisPage({ resource }: { resource: ResourcePageContent }) {
  return (
    <aside className={styles.onThisPage}>
      <strong>On this page</strong>
      <nav>{resource.sections.map((section) => <a href={`#${section.id}`} key={section.id}>{section.eyebrow.replace(/^\d+ · /, "")} <ArrowRight size={13} /></a>)}</nav>
      <div>
        <ShieldCheck size={23} />
        <strong>Need implementation context?</strong>
        <p>Use the launch guide to connect this topic to the complete agent workflow.</p>
        <Link href="/resources/documentation">Open documentation <ArrowRight size={14} /></Link>
      </div>
    </aside>
  );
}

function RelatedResources({ resource }: { resource: ResourcePageContent }) {
  return (
    <section className={styles.relatedResources}>
      <span>Continue exploring</span>
      <h2>Move from this answer to the next operating decision.</h2>
      <div>
        {resource.related.map((slug) => {
          const related = resourcePages[slug];
          return (
            <Link href={`/resources/${related.slug}`} key={slug}>
              <ResourceIcon name={related.icon} size={23} />
              <strong>{related.label}</strong>
              <p>{related.description}</p>
              <span>Explore <ArrowRight size={14} /></span>
            </Link>
          );
        })}
      </div>
    </section>
  );
}

export function MarketingResourceDetailPage({ slug }: { slug: string }) {
  const resource = resourceFor(slug);
  if (!resource) return null;

  return (
    <main className={`${styles.page} ${styles.detailPage} ${styles[`accent_${resource.accent}`]}`}>
      <nav className={styles.resourceSubnav} aria-label="Resource sections">
        <Link href="/resources">Overview</Link>
        {resourceNavItems.map((item) => <Link className={item.slug === resource.slug ? styles.activeSubnav : ""} href={`/resources/${item.slug}`} key={item.slug}>{item.label}</Link>)}
      </nav>
      <section className={styles.detailShell}>
        <BrowseRail active={resource.slug} />
        <article className={styles.detailArticle}>
          <DetailHero resource={resource} />
          {resource.slug === "faqs" ? <FaqSections resource={resource} /> : resource.slug === "api-reference" ? <ApiSections resource={resource} /> : <StandardSections resource={resource} />}
          <RelatedResources resource={resource} />
        </article>
        {resource.slug === "security" ? <TrustRail compact /> : <OnThisPage resource={resource} />}
      </section>
    </main>
  );
}
