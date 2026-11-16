import type { LucideIcon } from "lucide-react";
import { Check, Mail, ShieldCheck } from "lucide-react";
import { Eyebrow } from "@/features/marketing/VoiceHome/motion";
import { PixelGlow } from "@/features/marketing/VoiceHome/PixelGlow";
import { CopySectionLink } from "./CopySectionLink";

export type LegalSection = { id: string; title: string; paragraphs?: string[]; bullets?: string[] };
type Props = { eyebrow: string; title: string; summary: string; effectiveDate: string; icon: LucideIcon; highlights: string[]; sections: LegalSection[] };

export function LegalDocument({ eyebrow, title, summary, effectiveDate, icon: Icon, highlights, sections }: Props) {
  return (
    <main className="mk-v2 overflow-x-clip">
      <section className="relative isolate overflow-hidden pb-14 pt-32 sm:pt-40">
        <PixelGlow
          seed={60}
          palette={[[56, 189, 248], [37, 99, 235], [94, 234, 212]]}
          intensity={0.5}
          className="pointer-events-none absolute -top-24 right-[-10%] -z-10 h-[34rem] w-[60%] [mask-image:radial-gradient(closest-side,black_25%,transparent)] max-lg:w-[120%]"
        />
        <div className="mx-auto w-full max-w-[1240px] px-4 sm:px-8">
          <span className="grid size-12 place-items-center rounded-2xl border border-signal/25 bg-signal/10 text-signal">
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <div className="mt-8"><Eyebrow>{eyebrow}</Eyebrow></div>
          <h1 className="mt-5 max-w-3xl text-4xl font-medium leading-[1.05] tracking-[-0.035em] text-white sm:text-[3.5rem]">{title}</h1>
          <p className="mt-6 max-w-2xl text-[1.06rem] leading-relaxed text-fg-soft">{summary}</p>
          <p className="mt-6 inline-flex items-center gap-2 rounded-full border border-line px-3 py-1.5 text-xs text-fg-soft">
            <ShieldCheck className="size-3.5 text-signal" aria-hidden="true" /> Effective {effectiveDate}
          </p>
        </div>
      </section>

      <section aria-label="Document highlights" className="border-y border-line">
        <div className="mx-auto grid max-w-[1240px] gap-px bg-line sm:grid-cols-2 lg:grid-cols-4">
          {highlights.map((item) => (
            <p key={item} className="flex gap-3 bg-canvas px-6 py-6 text-sm leading-relaxed text-fg sm:px-8">
              <Check className="mt-0.5 size-4 shrink-0 text-signal" aria-hidden="true" />
              {item}
            </p>
          ))}
        </div>
      </section>

      <div className="mx-auto grid w-full max-w-[1240px] px-4 sm:px-8 gap-12 py-16 lg:grid-cols-[16rem_1fr] lg:gap-20 [&>*]:min-w-0">
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <p className="font-mono text-[0.62rem] uppercase tracking-[0.18em] text-fg-muted">On this page</p>
          <nav aria-label={`${title} sections`} className="mt-4 grid border-l border-line">
            {sections.map((section, index) => (
              <a
                key={section.id}
                href={`#${section.id}`}
                className="-ml-px border-l border-transparent py-1.5 pl-4 text-sm text-fg-soft transition-colors hover:border-signal hover:text-white"
              >
                <span className="mr-2 font-mono text-[0.65rem] text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
                {section.title}
              </a>
            ))}
          </nav>
          <a
            href="mailto:support@sauti.uk"
            className="mt-8 flex items-center gap-3 rounded-2xl border border-line bg-white/[0.02] p-4 text-sm transition-colors hover:border-line-strong"
          >
            <Mail className="size-4 text-signal" aria-hidden="true" />
            <span>
              <span className="block font-medium text-white">Questions?</span>
              <span className="text-fg-soft">support@sauti.uk</span>
            </span>
          </a>
        </aside>

        <article className="max-w-3xl">
          {sections.map((section, index) => (
            <section key={section.id} id={section.id} className="group/section scroll-mt-28 border-b border-line py-10 first:pt-0 last:border-0">
              <div className="flex items-start justify-between gap-4">
                <h2 className="flex items-baseline gap-4 text-2xl font-medium tracking-tight text-white">
                  <span className="font-mono text-sm text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
                  {section.title}
                </h2>
                <CopySectionLink id={section.id} title={section.title} />
              </div>
              <div className="mt-5 grid gap-4 pl-0 sm:pl-9">
                {section.paragraphs?.map((text) => (
                  <p key={text} className="leading-relaxed text-fg-soft">{text}</p>
                ))}
                {section.bullets ? (
                  <ul className="grid gap-2.5">
                    {section.bullets.map((text) => (
                      <li key={text} className="flex gap-3 leading-relaxed text-fg-soft">
                        <span className="mt-2.5 size-1.5 shrink-0 rounded-full bg-signal" aria-hidden="true" />
                        {text}
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            </section>
          ))}

          <div className="mt-6 flex flex-col gap-4 rounded-3xl border border-line bg-[linear-gradient(180deg,#0d0d0d,#060606)] p-6 sm:flex-row sm:items-center">
            <ShieldCheck className="size-6 shrink-0 text-signal" aria-hidden="true" />
            <div className="flex-1">
              <p className="font-medium text-white">Built for responsible conversations</p>
              <p className="mt-1 text-sm text-fg-soft">Privacy, tenant isolation, explicit integrations and safe call handling are part of Sauti&apos;s architecture.</p>
            </div>
          </div>
        </article>
      </div>
    </main>
  );
}
