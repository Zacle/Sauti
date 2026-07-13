"use client";

import { useEffect } from "react";

export function useRevealMotion() {
  useEffect(() => {
    const selector =
      "[data-reveal],[data-reveal-left],[data-reveal-right],[data-reveal-scale]";
    const elements = document.querySelectorAll<HTMLElement>(selector);
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("in-view");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.14, rootMargin: "-2% 0px -7% 0px" },
    );
    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, []);
}
