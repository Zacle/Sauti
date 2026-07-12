"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowRight, Menu, X } from "lucide-react";
import { menuGroups, sectionPathFor } from "@/features/marketing/site-map";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";

export function MarketingNav() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="nav-shell">
      <Link className="brand" href="/" onClick={() => setMobileOpen(false)}>
        <BrandLogo />
        <span>Sauti</span>
      </Link>
      <nav className="nav-links" aria-label="Main navigation">
        {menuGroups.map((group) => (
          <div className="nav-dropdown" key={group.label}>
            <Link href={group.href} className="nav-trigger">{group.label}</Link>
            <div className="product-menu" role="menu" aria-label={`${group.label} destinations`}>
              <div className="product-menu-copy">
                <span>{group.eyebrow}</span>
                <strong>{group.headline}</strong>
                <p>{group.description}</p>
              </div>
              <div className="product-menu-grid">
                {group.items.map(([slug, title, text, Icon]) => (
                  <Link href={`/${sectionPathFor(group)}/${slug}`} role="menuitem" key={slug}>
                    <Icon size={22} />
                    <div>
                      <strong>{title}</strong>
                      <span>{text}</span>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          </div>
        ))}
        <Link href="/pricing">Pricing</Link>
      </nav>
      <div className="nav-actions">
        <Link className="login-link" href="/login">Log in</Link>
        <Link className="solid-button" href="https://cal.com/sauti/demo" target="_blank">
          Start pilot <ArrowRight size={15} />
        </Link>
      </div>
      <button
        className="mobile-menu-button"
        type="button"
        aria-label={mobileOpen ? "Close navigation menu" : "Open navigation menu"}
        aria-expanded={mobileOpen}
        onClick={() => setMobileOpen((open) => !open)}
      >
        {mobileOpen ? <X size={22} /> : <Menu size={22} />}
      </button>
      <div className={`mobile-nav-panel ${mobileOpen ? "open" : ""}`} aria-hidden={!mobileOpen}>
        <div className="mobile-nav-scroll">
          {menuGroups.map((group) => (
            <section key={group.label}>
              <Link className="mobile-nav-heading" href={group.href} onClick={() => setMobileOpen(false)}>
                <span>{group.label}</span>
                <ArrowRight size={16} />
              </Link>
              <div>
                {group.items.map(([slug, title, text, Icon]) => (
                  <Link href={`/${sectionPathFor(group)}/${slug}`} key={slug} onClick={() => setMobileOpen(false)}>
                    <Icon size={19} />
                    <span>
                      <strong>{title}</strong>
                      <small>{text}</small>
                    </span>
                  </Link>
                ))}
              </div>
            </section>
          ))}
          <Link className="mobile-nav-pricing" href="/pricing" onClick={() => setMobileOpen(false)}>
            Pricing <ArrowRight size={16} />
          </Link>
          <Link className="solid-button large mobile-nav-cta" href="https://cal.com/sauti/demo" target="_blank" onClick={() => setMobileOpen(false)}>
            Start pilot <ArrowRight size={17} />
          </Link>
        </div>
      </div>
    </header>
  );
}

export function MarketingFooter() {
  return (
    <footer className="footer product-footer">
      <div>
        <Link className="brand" href="/">
          <BrandLogo />
          <span>Sauti</span>
        </Link>
        <p>AI voice agents that turn conversations into conversions.</p>
      </div>
      <div className="footer-links">
        <Link href="/solutions">Solutions</Link>
        <Link href="/integrations">Integrations</Link>
      </div>
      <div className="footer-links">
        <Link href="/industries">Industries</Link>
        <Link href="/resources/security">Security</Link>
        <Link href="/pricing">Pricing</Link>
      </div>
      <div className="footer-links">
        <Link href="/privacy">Privacy</Link>
        <Link href="/terms">Terms</Link>
        <a href="mailto:support@sauti.uk">Contact</a>
      </div>
      <small>© 2025 Sauti. All rights reserved.</small>
    </footer>
  );
}
