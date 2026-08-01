"use client";

import { Player } from "@remotion/player";
import { BookOpenCheck, Braces, ShieldCheck } from "lucide-react";
import { Easing, interpolate, useCurrentFrame } from "remotion";
import { useEffect, useState } from "react";
import type { AudienceId } from "@/features/marketing/Resources/domain/resource-content";

const FPS = 30;
const DURATION = 150;

type ResourceSearchSceneProps = {
  audience: AudienceId;
};

function ResourceSearchScene({ audience }: ResourceSearchSceneProps) {
  const frame = useCurrentFrame();
  const items = audience === "builder"
    ? [[Braces, "API contract"], [BookOpenCheck, "Implementation guide"], [ShieldCheck, "Webhook security"]]
    : audience === "security"
      ? [[ShieldCheck, "Tenant boundary"], [Braces, "Signed callbacks"], [BookOpenCheck, "Control model"]]
      : [[BookOpenCheck, "Booking guide"], [Braces, "Calendar setup"], [ShieldCheck, "Confirmation rules"]];

  return (
    <div style={{ position: "relative", width: "100%", height: "100%", overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          top: 12,
          right: 88,
          width: 1,
          height: 52,
          opacity: interpolate(frame, [0, 28], [0, 0.8], {
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          backgroundColor: "#45e7db",
        }}
      />
      <div
        style={{
          position: "absolute",
          top: 63,
          right: 42,
          width: 47,
          height: 1,
          opacity: interpolate(frame, [18, 42], [0, 0.8], {
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          backgroundColor: "#45e7db",
        }}
      />
      {items.map(([Icon, label], index) => (
        <div
          key={label as string}
          style={{
            position: "absolute",
            top: 74,
            right: 18 + index * 156,
            display: "flex",
            alignItems: "center",
            gap: 7,
            padding: "7px 10px",
            border: "1px solid rgba(77,231,219,.18)",
            borderRadius: 10,
            opacity: interpolate(frame, [20 + index * 8, 42 + index * 8, 120, 145], [0, 1, 1, 0], {
              easing: [Easing.bezier(0.16, 1, 0.3, 1), Easing.linear, Easing.bezier(0.4, 0, 1, 1)],
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
            }),
            translate: interpolate(frame, [20 + index * 8, 42 + index * 8], ["0px 10px", "0px 0px"], {
              easing: Easing.bezier(0.16, 1, 0.3, 1),
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
            }),
            color: index === 0 ? "#58eadf" : "rgba(224,239,246,.72)",
            backgroundColor: index === 0 ? "rgba(55,230,218,.08)" : "rgba(7,22,36,.72)",
            fontFamily: "inherit",
            fontSize: 12,
            whiteSpace: "nowrap",
          }}
        >
          <Icon size={15} strokeWidth={1.8} />
          <span>{label as string}</span>
        </div>
      ))}
    </div>
  );
}

export function ResourceSearchMotion({ audience, searchVersion }: { audience: AudienceId; searchVersion: number }) {
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
      key={`${audience}-${searchVersion}`}
      acknowledgeRemotionLicense
      autoPlay
      component={ResourceSearchScene}
      compositionHeight={120}
      compositionWidth={900}
      controls={false}
      durationInFrames={DURATION}
      fps={FPS}
      inputProps={{ audience }}
      loop
      muted
      style={{ width: "100%", height: "100%" }}
    />
  );
}
