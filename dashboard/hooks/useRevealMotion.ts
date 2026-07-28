"use client";

import { useEffect } from "react";

export function useRevealMotion() {
  useEffect(() => {
    const selector =
      "[data-reveal],[data-reveal-left],[data-reveal-right],[data-reveal-scale]";
    const elements = Array.from(document.querySelectorAll<HTMLElement>(selector));
    const motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const heroParallax = document.querySelector<HTMLElement>("[data-hero-parallax]");
    const page = document.querySelector<HTMLElement>("[data-motion-page]");
    let animationFrame = 0;

    document.documentElement.classList.add("reveal-motion-ready");

    const observer = "IntersectionObserver" in window
      ? new IntersectionObserver(
          (entries) => {
            entries.forEach((entry) => {
              if (entry.isIntersecting) {
                entry.target.classList.add("in-view");
                observer?.unobserve(entry.target);
              }
            });
          },
          { threshold: 0.11, rootMargin: "0px 0px -8% 0px" },
        )
      : null;

    if (observer) elements.forEach((element) => observer.observe(element));
    else elements.forEach((element) => element.classList.add("in-view"));

    const updateMotion = () => {
      animationFrame = 0;

      if (heroParallax) {
        const heroRect = heroParallax.getBoundingClientRect();
        const heroProgress = Math.max(0, Math.min(1, -heroRect.top / Math.max(heroRect.height, 1)));
        heroParallax.style.setProperty(
          "--hero-shift",
          motionQuery.matches ? "0px" : `${heroProgress * 48}px`,
        );
      }

      if (page) {
        const scrollRange = Math.max(document.documentElement.scrollHeight - window.innerHeight, 1);
        const progress = motionQuery.matches ? 0 : Math.max(0, Math.min(1, window.scrollY / scrollRange));
        page.style.setProperty("--page-progress", String(progress));
      }
    };

    const requestMotionUpdate = () => {
      if (!animationFrame) {
        animationFrame = window.requestAnimationFrame(updateMotion);
      }
    };

    updateMotion();
    window.addEventListener("scroll", requestMotionUpdate, { passive: true });
    window.addEventListener("resize", requestMotionUpdate);
    motionQuery.addEventListener("change", requestMotionUpdate);

    return () => {
      observer?.disconnect();
      document.documentElement.classList.remove("reveal-motion-ready");
      window.removeEventListener("scroll", requestMotionUpdate);
      window.removeEventListener("resize", requestMotionUpdate);
      motionQuery.removeEventListener("change", requestMotionUpdate);
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
    };
  }, []);
}
