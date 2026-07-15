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

    const motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const featureStory = document.querySelector<HTMLElement>("[data-feature-story]");
    const storyPanels = featureStory
      ? Array.from(featureStory.querySelectorAll<HTMLElement>("[data-story-panel]"))
      : [];
    const storyCounter = featureStory?.querySelector<HTMLElement>("[data-story-current]");
    let animationFrame = 0;

    const resetFeatureStory = () => {
      featureStory?.style.removeProperty("--story-progress");
      storyPanels.forEach((panel) => {
        panel.style.removeProperty("--chapter-y");
        panel.style.removeProperty("--chapter-scale");
        panel.style.removeProperty("--chapter-opacity");
        panel.style.removeProperty("--chapter-copy-y");
        panel.style.removeProperty("--chapter-view-y");
        panel.style.removeProperty("--chapter-orbit-y");
        panel.removeAttribute("data-story-active");
        panel.removeAttribute("aria-hidden");
        panel.inert = false;
      });
      if (storyCounter) storyCounter.textContent = "01";
    };

    const updateFeatureStory = () => {
      animationFrame = 0;

      if (!featureStory || storyPanels.length === 0) return;

      if (motionQuery.matches || window.innerWidth <= 900) {
        resetFeatureStory();
        return;
      }

      const viewportHeight = window.innerHeight;
      const rect = featureStory.getBoundingClientRect();
      const scrollDistance = Math.max(featureStory.offsetHeight - viewportHeight, 1);
      const progress = Math.max(0, Math.min(1, -rect.top / scrollDistance));
      const rawPanelPosition = progress * (storyPanels.length - 1);
      const segment = Math.min(Math.floor(rawPanelPosition), storyPanels.length - 2);
      const segmentProgress = rawPanelPosition - segment;
      const transitionProgress = Math.max(0, Math.min(1, (segmentProgress - 0.16) / 0.68));
      let easedProgress: number;
      if (transitionProgress < 0.72) {
        const movementProgress = transitionProgress / 0.72;
        easedProgress = movementProgress * movementProgress * (3 - 2 * movementProgress);
      } else {
        const bounceProgress = (transitionProgress - 0.72) / 0.28;
        easedProgress = 1
          + Math.sin(bounceProgress * Math.PI * 2) * (1 - bounceProgress) * 0.055;
      }
      const panelPosition = progress === 1 ? storyPanels.length - 1 : segment + easedProgress;
      const activePanel = Math.round(panelPosition);
      const settledPanel = progress === 1 || transitionProgress >= 0.72
        ? segment + 1
        : transitionProgress === 0
          ? segment
          : -1;
      const slideDistance = Math.min(viewportHeight * 0.62, 620);

      featureStory.style.setProperty("--story-progress", `${progress * 100}%`);
      if (storyCounter) {
        storyCounter.textContent = String(activePanel + 1).padStart(2, "0");
      }

      storyPanels.forEach((panel, index) => {
        const delta = index - panelPosition;
        const distance = Math.abs(delta);
        panel.style.setProperty("--chapter-y", `${delta * slideDistance}px`);
        panel.style.setProperty("--chapter-scale", String(1 - Math.min(distance * 0.045, 0.09)));
        panel.style.setProperty("--chapter-opacity", String(Math.max(0, 1 - distance)));
        panel.style.setProperty("--chapter-copy-y", `${Math.max(-1, Math.min(1, delta)) * -12}px`);
        panel.style.setProperty("--chapter-view-y", `${Math.max(-1, Math.min(1, delta)) * 10}px`);
        panel.style.setProperty("--chapter-orbit-y", `${Math.max(-1, Math.min(1, delta)) * 30}px`);
        if (index === settledPanel) panel.setAttribute("data-story-active", "true");
        else panel.removeAttribute("data-story-active");
        panel.setAttribute("aria-hidden", String(index !== activePanel));
        panel.inert = index !== activePanel;
      });
    };

    const requestStoryUpdate = () => {
      if (!animationFrame) {
        animationFrame = window.requestAnimationFrame(updateFeatureStory);
      }
    };

    updateFeatureStory();
    window.addEventListener("scroll", requestStoryUpdate, { passive: true });
    window.addEventListener("resize", requestStoryUpdate);
    motionQuery.addEventListener("change", requestStoryUpdate);

    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", requestStoryUpdate);
      window.removeEventListener("resize", requestStoryUpdate);
      motionQuery.removeEventListener("change", requestStoryUpdate);
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
    };
  }, []);
}
