"use client";

import { Player } from "@remotion/player";
import { useEffect, useState } from "react";
import { AbsoluteFill, Easing, Interactive, interpolate, useCurrentFrame } from "remotion";

type PricingRecommendationMotionProps = {
  usageRatio: number;
};

export function PricingRecommendationMotion({ usageRatio }: PricingRecommendationMotionProps) {
  const frame = useCurrentFrame();
  const safeRatio = Math.max(0.05, Math.min(1, usageRatio));

  return (
    <AbsoluteFill
      aria-hidden="true"
      style={{
        overflow: "hidden",
        backgroundColor: "transparent",
      }}
    >
      <Interactive.Div
        name="Recommendation glow"
        style={{
          position: "absolute",
          top: 2,
          right: 12,
          width: 160,
          height: 160,
          borderRadius: 999,
          opacity: interpolate(frame, [0, 45, 90], [0.2, 0.52, 0.2], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          scale: interpolate(frame, [0, 45, 90], [0.86, 1.08, 0.86], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            output: "perceptual-scale",
          }),
          backgroundColor: "rgba(57, 228, 216, 0.12)",
          filter: "blur(34px)",
        }}
      />
      <Interactive.Div
        name="Usage track"
        style={{
          position: "absolute",
          right: 22,
          bottom: 12,
          left: 22,
          height: 2,
          borderRadius: 999,
          backgroundColor: "rgba(167, 198, 214, 0.12)",
        }}
      >
        <Interactive.Div
          name="Usage progress"
          style={{
            width: `${safeRatio * 100}%`,
            height: 2,
            borderRadius: 999,
            opacity: interpolate(frame, [0, 24], [0.2, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.16, 1, 0.3, 1),
            }),
            scale: interpolate(frame, [0, 28], [0.04, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.spring({ damping: 180 }),
              output: "perceptual-scale",
            }),
            transformOrigin: "left center",
            backgroundColor: "#45e6da",
            boxShadow: "0 0 16px rgba(69, 230, 218, 0.5)",
          }}
        />
      </Interactive.Div>
    </AbsoluteFill>
  );
}

export function PricingRecommendationPlayer({ usageRatio, motionKey }: PricingRecommendationMotionProps & { motionKey: string }) {
  const [reducedMotion, setReducedMotion] = useState(true);

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReducedMotion(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  if (reducedMotion) return null;

  return (
    <Player
      key={motionKey}
      acknowledgeRemotionLicense
      autoPlay
      component={PricingRecommendationMotion}
      compositionHeight={150}
      compositionWidth={760}
      controls={false}
      durationInFrames={90}
      fps={30}
      inputProps={{ usageRatio }}
      loop
      muted
      style={{ width: "100%", height: "100%" }}
    />
  );
}
