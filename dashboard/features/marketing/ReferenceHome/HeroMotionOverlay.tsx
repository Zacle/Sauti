"use client";

import { Player } from "@remotion/player";
import { Easing, interpolate, useCurrentFrame } from "remotion";
import { useEffect, useRef, useState } from "react";

const FPS = 30;
const DURATION_IN_FRAMES = 240;

type HeroMotionSceneProps = {
  scrollProgress: number;
};

function HeroMotionScene({ scrollProgress }: HeroMotionSceneProps) {
  const frame = useCurrentFrame();
  const cycle = frame % DURATION_IN_FRAMES;
  const scrollLift = interpolate(scrollProgress, [0, 1], [0, 260], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const outcomeOpacity = interpolate(scrollProgress, [0.58, 0.78], [0, 1], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const outcomeLift = interpolate(scrollProgress, [0.58, 0.78], [24, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const stageStyles = [
    {
      opacity: interpolate(scrollProgress, [0, 0.28, 0.48], [1, 1, 0.4], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }),
      progress: interpolate(scrollProgress, [0, 0.28, 0.48], [1, 1, 0], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }),
    },
    {
      opacity: interpolate(scrollProgress, [0.18, 0.42, 0.72, 0.88], [0.46, 1, 1, 0.46], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }),
      progress: interpolate(scrollProgress, [0.18, 0.42, 0.72, 0.88], [0, 1, 1, 0], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }),
    },
    {
      opacity: interpolate(scrollProgress, [0.5, 0.74], [0.46, 1], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }),
      progress: interpolate(scrollProgress, [0.5, 0.74], [0, 1], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }),
    },
  ];

  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        position: "relative",
        overflow: "hidden",
        background: "transparent",
        translate: `0 ${scrollLift}px`,
      }}
    >
      <div
        style={{
          position: "absolute",
          width: 560,
          height: 560,
          right: -10,
          top: 36,
          borderRadius: 160,
          opacity: 0.55,
          background:
            "radial-gradient(circle, rgba(45,230,205,.22) 0%, rgba(55,132,239,.12) 36%, transparent 72%)",
          filter: "blur(30px)",
        }}
      />
      <div
        style={{
          position: "absolute",
          top: 76,
          right: 86,
          width: 410,
          minHeight: 520,
          boxSizing: "border-box",
          padding: "26px 26px 24px",
          border: "1px solid rgba(147,246,231,.26)",
          borderRadius: 30,
          opacity: 1,
          color: "#effffc",
          background:
            "linear-gradient(145deg, rgba(3,18,31,.91), rgba(5,27,43,.79))",
          boxShadow: "0 28px 90px rgba(0,0,0,.4), inset 0 1px 0 rgba(255,255,255,.08)",
          backdropFilter: "blur(20px)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div>
            <div style={{ color: "#78f2df", fontSize: 12, fontWeight: 800, letterSpacing: ".14em" }}>
              LIVE CONVERSATION
            </div>
            <div style={{ marginTop: 6, fontSize: 24, fontWeight: 760, letterSpacing: "-.03em" }}>
              Sauti is handling it
            </div>
          </div>
          <div
            style={{
              width: 12,
              height: 12,
              borderRadius: "50%",
              background: "#65f1d7",
              boxShadow: "0 0 0 7px rgba(101,241,215,.1), 0 0 24px rgba(101,241,215,.8)",
            }}
          />
        </div>

        <div
          style={{
            display: "flex",
            height: 50,
            margin: "22px 0 18px",
            padding: "0 14px",
            alignItems: "center",
            justifyContent: "space-between",
            borderRadius: 16,
            background: "rgba(89,224,206,.08)",
          }}
        >
          {Array.from({ length: 28 }).map((_, index) => {
            const height = 8 + Math.abs(Math.sin((cycle + index * 7) * 0.115)) * 25;
            return (
              <div
                key={index}
                style={{
                  width: 3,
                  height,
                  borderRadius: 4,
                  opacity: 0.55 + (index % 4) * 0.1,
                  background: index < 16 ? "#6fead8" : "#7c8ff4",
                }}
              />
            );
          })}
        </div>

        <div style={{ position: "relative", display: "grid", gap: 11 }}>
          <div
            style={{
              position: "absolute",
              top: 42,
              bottom: 42,
              left: 22,
              width: 2,
              borderRadius: 4,
              background: "rgba(120,235,220,.12)",
            }}
          >
            <div
              style={{
                width: "100%",
                height: `${Math.max(4, scrollProgress * 100)}%`,
                borderRadius: 4,
                background: "linear-gradient(#64edda, #8680ee)",
                boxShadow: "0 0 12px rgba(100,237,218,.55)",
              }}
            />
          </div>

          {[
            ["01 · LISTEN", "Friday appointment requested"],
            ["02 · UNDERSTAND", "Intent and availability matched"],
            ["03 · ACT", "Booking confirmed · CRM updated"],
          ].map(([label, message], index) => (
            <div
              key={label}
              style={{
                position: "relative",
                display: "grid",
                minHeight: 68,
                padding: "13px 14px 13px 57px",
                boxSizing: "border-box",
                alignContent: "center",
                border: `1px solid rgba(111,234,216,${0.1 + stageStyles[index].progress * 0.28})`,
                borderRadius: 18,
                opacity: stageStyles[index].opacity,
                scale: 0.97 + stageStyles[index].progress * 0.03,
                translate: `${(1 - stageStyles[index].progress) * 12}px 0`,
                background: `rgba(70,216,198,${0.035 + stageStyles[index].progress * 0.09})`,
              }}
            >
              <div
                style={{
                  position: "absolute",
                  top: 23,
                  left: 15,
                  width: 17,
                  height: 17,
                  border: "2px solid #6ee9d7",
                  borderRadius: "50%",
                  background: stageStyles[index].progress > 0.72 ? "#6ee9d7" : "#071b2a",
                  boxShadow: `0 0 ${stageStyles[index].progress * 20}px rgba(103,235,216,.8)`,
                }}
              />
              <div style={{ color: "#70e8d7", fontSize: 10, fontWeight: 850, letterSpacing: ".13em" }}>
                {label}
              </div>
              <div style={{ marginTop: 5, fontSize: 14, fontWeight: 650 }}>{message}</div>
            </div>
          ))}
        </div>

        <div
          style={{
            position: "absolute",
            top: 82,
            right: 26,
            left: 26,
            zIndex: 4,
            display: "flex",
            minHeight: 112,
            padding: "18px 18px",
            boxSizing: "border-box",
            alignItems: "center",
            justifyContent: "space-between",
            border: "1px solid rgba(111,240,218,.52)",
            borderRadius: 20,
            opacity: outcomeOpacity,
            translate: `0 ${outcomeLift}px`,
            background: "linear-gradient(120deg, rgba(12,66,68,.96), rgba(35,44,92,.96))",
            boxShadow: "0 18px 44px rgba(0,0,0,.34), 0 0 32px rgba(95,234,213,.14)",
          }}
        >
          <div>
            <div style={{ color: "#8ff6e7", fontSize: 10, fontWeight: 850, letterSpacing: ".12em" }}>
              APPOINTMENT BOOKED
            </div>
            <div style={{ marginTop: 6, fontSize: 20, fontWeight: 780 }}>Friday · 10:00 AM</div>
            <div style={{ marginTop: 5, color: "rgba(231,255,251,.68)", fontSize: 11 }}>Calendar confirmed · CRM synced</div>
          </div>
          <div style={{ display: "grid", width: 44, height: 44, placeItems: "center", color: "#061b20", borderRadius: "50%", fontSize: 21, fontWeight: 900, background: "#6ef0dc" }}>
            ✓
          </div>
        </div>
      </div>
    </div>
  );
}

export function HeroMotionOverlay() {
  const [reducedMotion, setReducedMotion] = useState(true);
  const [scrollProgress, setScrollProgress] = useState(0);
  const overlayRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const updatePreference = () => setReducedMotion(query.matches);
    updatePreference();
    query.addEventListener("change", updatePreference);
    return () => query.removeEventListener("change", updatePreference);
  }, []);

  useEffect(() => {
    if (reducedMotion) return;

    let animationFrame = 0;

    const updateScrollProgress = () => {
      animationFrame = 0;
      const hero = overlayRef.current?.closest<HTMLElement>("[data-hero-parallax]");
      if (!hero) return;

      const rect = hero.getBoundingClientRect();
      const storyDistance = Math.max(rect.height * 0.38, 1);
      const progress = Math.max(0, Math.min(1, -rect.top / storyDistance));
      setScrollProgress((current) => (
        Math.abs(current - progress) > 0.002 ? progress : current
      ));
    };

    const requestScrollUpdate = () => {
      if (!animationFrame) {
        animationFrame = window.requestAnimationFrame(updateScrollProgress);
      }
    };

    updateScrollProgress();
    window.addEventListener("scroll", requestScrollUpdate, { passive: true });
    window.addEventListener("resize", requestScrollUpdate);

    return () => {
      window.removeEventListener("scroll", requestScrollUpdate);
      window.removeEventListener("resize", requestScrollUpdate);
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
    };
  }, [reducedMotion]);

  if (reducedMotion) return null;

  return (
    <div
      ref={overlayRef}
      aria-hidden="true"
      data-hero-remotion
      data-scroll-progress={scrollProgress.toFixed(3)}
    >
      <Player
        component={HeroMotionScene}
        inputProps={{ scrollProgress }}
        durationInFrames={DURATION_IN_FRAMES}
        fps={FPS}
        compositionWidth={1600}
        compositionHeight={720}
        autoPlay
        loop
        muted
        controls={false}
        acknowledgeRemotionLicense
        style={{ width: "100%", height: "100%" }}
      />
    </div>
  );
}
