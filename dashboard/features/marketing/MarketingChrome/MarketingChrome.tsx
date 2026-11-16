"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import * as Dialog from "@radix-ui/react-dialog";
import { AnimatePresence, motion } from "motion/react";
import { ArrowRight, ArrowUp, ChevronDown, Github, Linkedin, Mail } from "lucide-react";
import { primaryNav, sectionPathFor } from "@/features/marketing/site-map";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import { PixelGlow, glowPalettes } from "@/features/marketing/VoiceHome/PixelGlow";

export function MarketingNav() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="nav-shell">
      <Link className="brand" href="/" onClick={() => setMobileOpen(false)}>
        <BrandLogo />
        <span>Sauti</span>
      </Link>
      <DesktopNav />
      <div className="nav-actions">
        <Link className="login-link" href="/login">Log in</Link>
        <Link className="solid-button" href="/request-demo">
          Request a demo <ArrowRight size={15} />
        </Link>
      </div>
      <button
        className="mobile-menu-button"
        type="button"
        aria-label={mobileOpen ? "Close navigation menu" : "Open navigation menu"}
        aria-expanded={mobileOpen}
        onClick={() => setMobileOpen((open) => !open)}
      >
        <MenuGlyph open={mobileOpen} />
      </button>
      <MobileMenu open={mobileOpen} onOpenChange={setMobileOpen} />
    </header>
  );
}

const navLink =
  "inline-flex h-9 items-center gap-1 rounded-full px-3 text-[0.875rem] text-fg-soft transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-signal";

/**
 * Desktop navigation: plain links plus compact dropdown panels that open on
 * hover or keyboard focus. Hidden at the same breakpoint the mobile menu appears.
 */
function DesktopNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="Main navigation" className="mk-v2 flex items-center justify-center gap-0.5 max-[980px]:hidden">
      {primaryNav.map((entry) => {
        if (entry.kind === "link") {
          const active = pathname.startsWith(entry.href);
          return (
            <Link key={entry.href} href={entry.href} className={`${navLink} ${active ? "text-white" : ""}`}>
              {entry.label}
            </Link>
          );
        }
        const { group } = entry;
        const path = sectionPathFor(group);
        const active = pathname.startsWith(`/${path}`);
        return (
          <div key={group.label} className="group/nav relative">
            <Link href={group.href} className={`${navLink} ${active ? "text-white" : ""}`} aria-haspopup="true">
              {group.label}
              <ChevronDown className="size-3.5 text-fg-muted transition-transform duration-200 group-hover/nav:rotate-180 group-focus-within/nav:rotate-180" aria-hidden="true" />
            </Link>
            {/* pt-3 bridges the gap so the pointer can travel into the panel */}
            <div className="invisible absolute left-1/2 top-full z-40 -translate-x-1/2 pt-3 opacity-0 transition-[opacity,visibility,transform] duration-200 group-focus-within/nav:visible group-focus-within/nav:opacity-100 group-hover/nav:visible group-hover/nav:opacity-100">
              <div className="w-[34rem] translate-y-1 overflow-hidden rounded-2xl border border-white/[0.1] bg-[#0b0b0b] shadow-[var(--shadow-pop)] transition-transform duration-200 group-hover/nav:translate-y-0 group-focus-within/nav:translate-y-0">
                <ul className="grid grid-cols-2 gap-1 p-2">
                  {group.items.map(([slug, title, text, Icon]) => (
                    <li key={slug}>
                      <Link
                        href={`/${path}/${slug}`}
                        className="group/item flex gap-3 rounded-xl p-3 transition-colors hover:bg-white/[0.05] focus-visible:bg-white/[0.05] focus-visible:outline-none"
                      >
                        <span className="grid size-9 shrink-0 place-items-center rounded-lg border border-line bg-white/[0.03] text-fg-soft transition-colors group-hover/item:border-signal/30 group-hover/item:bg-signal/10 group-hover/item:text-signal">
                          <Icon size={17} />
                        </span>
                        <span className="min-w-0">
                          <span className="block text-sm font-medium text-white">{title}</span>
                          <span className="mt-0.5 line-clamp-2 block text-xs leading-snug text-fg-muted">{text}</span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
                <Link
                  href={group.href}
                  className="flex items-center justify-between border-t border-line bg-white/[0.02] px-5 py-3 text-xs text-fg-soft transition-colors hover:text-white"
                >
                  <span>{group.headline}</span>
                  <span className="inline-flex shrink-0 items-center gap-1 text-white">
                    View all <ArrowRight className="size-3.5" aria-hidden="true" />
                  </span>
                </Link>
              </div>
            </div>
          </div>
        );
      })}
    </nav>
  );
}

/** Two bars that rotate into an X. */
function MenuGlyph({ open }: { open: boolean }) {
  return (
    <span aria-hidden="true" className="relative block h-3 w-[18px]">
      <motion.span
        className="absolute left-0 top-0 h-[1.5px] w-full rounded-full bg-current"
        animate={open ? { top: 5, rotate: 45 } : { top: 0, rotate: 0 }}
        transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      />
      <motion.span
        className="absolute bottom-0 left-0 h-[1.5px] w-full rounded-full bg-current"
        animate={open ? { bottom: 5, rotate: -45 } : { bottom: 0, rotate: 0 }}
        transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      />
    </span>
  );
}

const menuEase = [0.16, 1, 0.3, 1] as const;

/**
 * Full-screen mobile navigation (Radix Dialog for focus trap, Escape and scroll
 * lock). Sections expand as an accordion; links stagger in.
 */
function MobileMenu({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const pathname = usePathname();
  const [section, setSection] = useState<string>("");

  // Close when the route changes (e.g. after tapping a link).
  useEffect(() => {
    onOpenChange(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  // Non-modal so the header's menu button stays clickable; lock page scroll ourselves.
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  const close = () => onOpenChange(false);

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange} modal={false}>
      <AnimatePresence>
        {open ? (
          <Dialog.Portal forceMount>
            <Dialog.Content
              forceMount
              aria-describedby={undefined}
              onOpenAutoFocus={(event) => {
                event.preventDefault();
                (event.currentTarget as HTMLElement | null)?.focus();
              }}
              onInteractOutside={(event) => {
                // The hamburger toggles the menu itself; don't also treat it as an outside click.
                if ((event.target as HTMLElement | null)?.closest(".mobile-menu-button")) event.preventDefault();
              }}
              className="mk-v2 fixed inset-x-0 bottom-0 top-16 z-[29] flex flex-col bg-black outline-none"
            >
              <Dialog.Title className="sr-only">Navigation</Dialog.Title>
              <motion.div
                className="flex min-h-0 flex-1 flex-col"
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8, transition: { duration: 0.15 } }}
                transition={{ duration: 0.35, ease: menuEase }}
              >
                <PixelGlow
                  seed={70}
                  palette={glowPalettes.signal}
                  intensity={0.45}
                  className="pointer-events-none absolute -bottom-24 left-1/2 -z-10 h-[26rem] w-[44rem] -translate-x-1/2 [mask-image:radial-gradient(closest-side,black_15%,transparent)]"
                />
                <nav aria-label="Mobile navigation" className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-6 pt-2">
                  <ul className="grid">
                    {primaryNav.map((entry, index) => {
                      if (entry.kind === "link") {
                        return (
                          <motion.li
                            key={entry.href}
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.4, delay: 0.05 + index * 0.05, ease: menuEase }}
                            className="border-b border-line"
                          >
                            <Link href={entry.href} onClick={close} className="flex items-center justify-between rounded-lg py-5 text-2xl font-medium tracking-tight text-white outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal">
                              {entry.label} <ArrowRight className="size-5 text-fg-muted" aria-hidden="true" />
                            </Link>
                          </motion.li>
                        );
                      }
                      const { group } = entry;
                      const expanded = section === group.label;
                      return (
                        <motion.li
                          key={group.label}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ duration: 0.4, delay: 0.05 + index * 0.05, ease: menuEase }}
                          className="border-b border-line"
                        >
                          <button
                            type="button"
                            aria-expanded={expanded}
                            onClick={() => setSection(expanded ? "" : group.label)}
                            className="flex w-full items-center justify-between rounded-lg py-5 text-left text-2xl font-medium tracking-tight text-white outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal"
                          >
                            {group.label}
                            <ChevronDown
                              className={`size-5 text-fg-muted transition-transform duration-300 ${expanded ? "rotate-180 text-white" : ""}`}
                              aria-hidden="true"
                            />
                          </button>
                          <AnimatePresence initial={false}>
                            {expanded ? (
                              <motion.div
                                key="items"
                                initial={{ height: 0, opacity: 0 }}
                                animate={{ height: "auto", opacity: 1 }}
                                exit={{ height: 0, opacity: 0 }}
                                transition={{ duration: 0.35, ease: menuEase }}
                                className="overflow-hidden"
                              >
                                <ul className="grid gap-1 pb-4">
                                  {group.items.map(([slug, title, text, Icon]) => (
                                    <li key={slug}>
                                      <Link
                                        href={`/${sectionPathFor(group)}/${slug}`}
                                        onClick={close}
                                        className="flex items-start gap-3 rounded-2xl px-3 py-3 transition-colors active:bg-white/[0.06]"
                                      >
                                        <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-signal/20 bg-signal/10 text-signal">
                                          <Icon size={17} />
                                        </span>
                                        <span className="min-w-0">
                                          <span className="block text-[0.95rem] font-medium text-white">{title}</span>
                                          <span className="mt-0.5 block text-sm leading-snug text-fg-soft">{text}</span>
                                        </span>
                                      </Link>
                                    </li>
                                  ))}
                                  <li>
                                    <Link
                                      href={group.href}
                                      onClick={close}
                                      className="mt-1 inline-flex items-center gap-1.5 px-3 py-2 text-sm text-fg-soft"
                                    >
                                      All {group.label.toLowerCase()} <ArrowRight className="size-4" aria-hidden="true" />
                                    </Link>
                                  </li>
                                </ul>
                              </motion.div>
                            ) : null}
                          </AnimatePresence>
                        </motion.li>
                      );
                    })}
                  </ul>
                </nav>
                <motion.div
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.45, delay: 0.25, ease: menuEase }}
                  className="grid gap-2 border-t border-line bg-black/80 px-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-4 backdrop-blur-xl"
                >
                  <Link
                    href="/request-demo"
                    onClick={close}
                    className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-white text-[0.95rem] font-medium text-black"
                  >
                    Request a demo <ArrowRight className="size-4" aria-hidden="true" />
                  </Link>
                  <Link
                    href="/login"
                    onClick={close}
                    className="inline-flex h-12 items-center justify-center rounded-full border border-line-strong text-[0.95rem] font-medium text-fg"
                  >
                    Log in
                  </Link>
                </motion.div>
              </motion.div>
            </Dialog.Content>
          </Dialog.Portal>
        ) : null}
      </AnimatePresence>
    </Dialog.Root>
  );
}

type FooterLink = { label: string; href: string; external?: boolean };

const footerGroups: Array<{ title: string; links: FooterLink[] }> = [
  {
    title: "Product",
    links: [
      { label: "Overview", href: "/" },
      { label: "Integrations", href: "/integrations" },
      { label: "Pricing", href: "/pricing" },
    ],
  },
  {
    title: "Solutions",
    links: [
      { label: "Appointment booking", href: "/solutions/appointment-booking" },
      { label: "Customer support", href: "/solutions/customer-service" },
      { label: "Lead qualification", href: "/solutions/lead-qualification" },
    ],
  },
  {
    title: "Industries",
    links: [
      { label: "All industries", href: "/industries" },
      { label: "Healthcare", href: "/industries/clinics-healthcare" },
      { label: "Property", href: "/industries/real-estate" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "Support", href: "mailto:support@sauti.uk", external: true },
      { label: "Status", href: "/health", external: true },
      { label: "Privacy", href: "/privacy" },
      { label: "Terms", href: "/terms" },
    ],
  },
];

const socials = [
  { label: "GitHub", href: "https://github.com/Zacle/Sauti", icon: Github },
  { label: "LinkedIn", href: "https://www.linkedin.com", icon: Linkedin },
  { label: "Email", href: "mailto:support@sauti.uk", icon: Mail },
];

export function MarketingFooter() {
  return (
    <footer className="mk-v2 relative isolate overflow-hidden border-t border-line bg-canvas">
      <div className="mx-auto max-w-[1240px] px-4 pt-20 sm:px-8">
        <div className="grid grid-cols-2 gap-x-6 gap-y-12 sm:grid-cols-3 lg:grid-cols-[1.5fr_repeat(4,1fr)]">
          <div className="col-span-2 sm:col-span-3 lg:col-span-1 lg:pr-8">
            <Link href="/" className="inline-flex items-center gap-2.5 text-lg font-semibold tracking-tight text-white">
              <BrandLogo size={30} />
              Sauti
            </Link>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-fg-soft">
              AI voice agents that answer every call, in your customer&rsquo;s language, and turn conversations into
              bookings.
            </p>
            <div className="mt-6 flex gap-2">
              {socials.map(({ label, href, icon: Icon }) => (
                <a
                  key={label}
                  href={href}
                  aria-label={label}
                  target={href.startsWith("http") ? "_blank" : undefined}
                  rel={href.startsWith("http") ? "noreferrer" : undefined}
                  className="grid size-9 place-items-center rounded-full border border-line text-fg-soft transition-colors hover:border-line-strong hover:bg-white/[0.04] hover:text-white"
                >
                  <Icon className="size-4" aria-hidden="true" />
                </a>
              ))}
            </div>
            <Link
              href="/request-demo"
              className="group mt-8 inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-sm font-medium text-black transition-colors hover:bg-white/85"
            >
              Book a demo
              <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
            </Link>
          </div>

          {footerGroups.map((group) => (
            <nav key={group.title} aria-label={group.title}>
              <p className="font-mono text-[0.65rem] uppercase tracking-[0.18em] text-fg-muted">{group.title}</p>
              <ul className="mt-5 grid gap-3">
                {group.links.map((link) => (
                  <li key={link.label}>
                    {link.external ? (
                      <a href={link.href} className="text-sm text-fg-soft transition-colors hover:text-white">
                        {link.label}
                      </a>
                    ) : (
                      <Link href={link.href} className="text-sm text-fg-soft transition-colors hover:text-white">
                        {link.label}
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="mt-20 flex flex-col-reverse gap-4 border-t border-line py-6 text-xs text-fg-muted sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} Sauti. All rights reserved.</p>
          <button
            type="button"
            onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
            className="group inline-flex items-center gap-1.5 self-start transition-colors hover:text-white sm:self-auto"
          >
            Back to top
            <ArrowUp className="size-3.5 transition-transform group-hover:-translate-y-0.5" aria-hidden="true" />
          </button>
        </div>
      </div>

      {/* Oversized wordmark with the pixel glow rising behind it */}
      <div aria-hidden="true" className="pointer-events-none relative -mt-2 h-[clamp(5rem,17vw,15rem)] select-none overflow-hidden">
        <PixelGlow
          seed={9}
          palette={glowPalettes.signal}
          intensity={0.7}
          block={20}
          className="absolute inset-x-0 bottom-0 -z-10 mx-auto h-[200%] w-full max-w-[1400px] [mask-image:radial-gradient(50%_60%_at_50%_100%,black_15%,transparent)]"
        />
        <p className="absolute inset-x-0 top-0 text-center text-[clamp(7rem,26vw,24rem)] font-semibold leading-[0.8] tracking-[-0.07em] text-transparent [-webkit-background-clip:text] [background-clip:text] [background-image:linear-gradient(180deg,rgb(255_255_255/0.16),rgb(255_255_255/0.02)_75%)]">
          sauti
        </p>
      </div>
    </footer>
  );
}
