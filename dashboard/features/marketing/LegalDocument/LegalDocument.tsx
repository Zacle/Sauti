import type { LucideIcon } from "lucide-react";
import { ArrowUpRight, CheckCircle2, Mail, ShieldCheck } from "lucide-react";
import Link from "next/link";
import styles from "./LegalDocument.module.css";

export type LegalSection = { id: string; title: string; paragraphs?: string[]; bullets?: string[] };
type Props = { eyebrow: string; title: string; summary: string; effectiveDate: string; icon: LucideIcon; highlights: string[]; sections: LegalSection[] };

export function LegalDocument({ eyebrow, title, summary, effectiveDate, icon: Icon, highlights, sections }: Props) {
  return <main className={styles.page}>
    <div className={styles.glow} aria-hidden="true" />
    <section className={styles.hero}><div className={styles.icon}><Icon size={28} /></div><span className={styles.eyebrow}>{eyebrow}</span><h1>{title}</h1><p>{summary}</p><div className={styles.meta}><ShieldCheck size={17} /> Effective {effectiveDate}</div></section>
    <section className={styles.highlights} aria-label="Document highlights">{highlights.map((item)=><div key={item}><CheckCircle2 size={18}/><span>{item}</span></div>)}</section>
    <div className={styles.layout}>
      <aside className={styles.toc}><span>On this page</span><nav aria-label={`${title} sections`}>{sections.map((section,index)=><a href={`#${section.id}`} key={section.id}>{index+1}. {section.title}</a>)}</nav><div className={styles.contact}><Mail size={18}/><div><strong>Questions?</strong><a href="mailto:support@sauti.uk">support@sauti.uk</a></div></div></aside>
      <article className={styles.document}>{sections.map((section,index)=><section id={section.id} key={section.id}><span>{String(index+1).padStart(2,"0")}</span><h2>{section.title}</h2>{section.paragraphs?.map((text)=><p key={text}>{text}</p>)}{section.bullets&&<ul>{section.bullets.map((text)=><li key={text}>{text}</li>)}</ul>}</section>)}<div className={styles.endNote}><ShieldCheck size={22}/><div><strong>Built for responsible conversations</strong><p>Privacy, tenant isolation, explicit integrations, and safe call handling are part of Sauti&apos;s product architecture.</p></div><Link href="/resources/security">Security <ArrowUpRight size={15}/></Link></div></article>
    </div>
  </main>;
}
