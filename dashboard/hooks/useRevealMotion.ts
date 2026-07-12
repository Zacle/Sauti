"use client";

import { useEffect } from "react";

export function useRevealMotion() {
  useEffect(() => {
    const elements = document.querySelectorAll<HTMLElement>("[data-reveal]");
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("in-view");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.18, rootMargin: "-4% 0px -8% 0px" },
    );
    elements.forEach((element) => observer.observe(element));

    const progressElements = document.querySelectorAll<HTMLElement>("[data-scroll-progress]");
    const parallaxElements = document.querySelectorAll<HTMLElement>("[data-parallax]");
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let frame = 0;
    const updateScrollMotion = () => {
      frame = 0;
      if (reducedMotion) return;
      const viewportHeight = window.innerHeight;
      progressElements.forEach((element) => {
        const rect = element.getBoundingClientRect();
        const progress = Math.max(0, Math.min(1, (viewportHeight * 0.82 - rect.top) / (rect.height + viewportHeight * 0.35)));
        element.style.setProperty("--scroll-progress", progress.toFixed(3));
        element.style.setProperty("--scroll-offset", `${((1 - progress) * 34).toFixed(1)}px`);
        element.style.setProperty("--scroll-scale", (0.965 + progress * 0.035).toFixed(3));
      });
      parallaxElements.forEach((element) => {
        const rect = element.getBoundingClientRect();
        const offset = Math.max(-1, Math.min(1, (viewportHeight / 2 - (rect.top + rect.height / 2)) / viewportHeight));
        element.style.setProperty("--parallax", offset.toFixed(3));
      });
    };
    const scheduleScrollMotion = () => {
      if (!frame) frame = requestAnimationFrame(updateScrollMotion);
    };
    updateScrollMotion();
    window.addEventListener("scroll", scheduleScrollMotion, { passive: true });
    window.addEventListener("resize", scheduleScrollMotion);
    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", scheduleScrollMotion);
      window.removeEventListener("resize", scheduleScrollMotion);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);
}
