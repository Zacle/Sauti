"use client";

import { useEffect, useRef } from "react";

/**
 * Pixel orb: a halftone sphere drawn from square "pixels" in the accent colour.
 *  - calm: slow breathing and a drifting light
 *  - listening: rings travel inward, as if drawing sound in
 *  - thinking: a scanning sweep circles the orb
 *  - speaking: rings push outward on a speech-like rhythm and spill past the edge
 * States cross-fade, so switching never jumps. Pixels are quantised into a few
 * brightness levels and drawn as one path per level, which keeps it cheap.
 */

export type VoiceAnimationActivity = "calm" | "listening" | "thinking" | "speaking";

type Props = { activity: VoiceAnimationActivity; size?: number };

const BASE = 240;
const CENTER = BASE / 2;
const RADIUS = 92;
const STEP = 6;
const LEVELS = 6;
const SPILL = 1.22;

type Weights = Record<VoiceAnimationActivity, number>;
const ACTIVITIES: VoiceAnimationActivity[] = ["calm", "listening", "thinking", "speaking"];

function readAccent(fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue("--color-signal").trim() || fallback;
}

/** Syllable-like envelope: bursts with short gaps, never perfectly periodic. */
function speechEnvelope(t: number) {
  const syllables = Math.max(0, Math.sin(t * 8.3) * Math.sin(t * 3.1 + 1.3));
  const phrase = 0.55 + 0.45 * Math.sin(t * 0.9);
  return Math.min(1, syllables * 1.6 * phrase + 0.08);
}

function drawOrb(context: CanvasRenderingContext2D, t: number, w: Weights) {
  const env = speechEnvelope(t);
  const breath = 0.5 + 0.5 * Math.sin(t * 1.1);
  const radius = RADIUS * (1 + w.speaking * 0.045 * env + w.calm * 0.012 * breath + w.listening * 0.02 * Math.sin(t * 2.4));
  const lightAngle = t * 0.35;
  const lx = Math.cos(lightAngle) * 0.55;
  const ly = Math.sin(lightAngle) * 0.4 - 0.35;
  const lz = 0.75;
  const sweepAngle = t * 2.4;
  const paths = Array.from({ length: LEVELS }, () => new Path2D());
  const extent = RADIUS * SPILL + STEP * 2;

  for (let y = -extent; y <= extent; y += STEP) {
    for (let x = -extent; x <= extent; x += STEP) {
      const r = Math.hypot(x, y);
      const nr = r / radius;
      if (nr > SPILL) continue;
      const inside = nr <= 1;
      const z = inside ? Math.sqrt(1 - nr * nr) : 0;

      // Sphere shading plus a slow flowing texture.
      const shade = inside ? Math.max(0, (x / radius) * lx + (y / radius) * ly + z * lz) : 0;
      const flow = 0.5 + 0.5 * Math.sin(x * 0.05 + t * 0.8 + Math.sin(y * 0.045 - t * 0.55) * 1.8);
      // A floor keeps the whole disc readable; the light only adds depth.
      let value = inside ? 0.2 + shade * 0.45 + flow * 0.18 + (1 - nr) * 0.12 : 0;
      value *= 0.82 + 0.18 * (w.calm * breath + (1 - w.calm));

      // Listening: rings travelling inward, strongest near the rim.
      const inward = 0.5 + 0.5 * Math.sin(nr * 15 + t * 5.5);
      value += w.listening * (inside ? inward * (0.15 + nr * 0.35) : 0);

      // Thinking: a bright band sweeping around the orb.
      if (w.thinking > 0.01 && inside) {
        let delta = (Math.atan2(y, x) - sweepAngle) % (Math.PI * 2);
        if (delta < 0) delta += Math.PI * 2;
        value += w.thinking * Math.exp(-delta * 1.8) * (0.25 + nr * 0.45);
      }

      // Speaking: outward rings riding the speech envelope, spilling past the edge.
      if (w.speaking > 0.01) {
        const outward = Math.max(0, Math.sin(nr * 13 - t * 7.5));
        const edge = inside ? 0.35 + nr * 0.65 : Math.max(0, (SPILL - nr) / (SPILL - 1));
        value += w.speaking * outward * env * edge * 0.75;
      }

      if (value < 0.07) continue;
      const level = Math.min(LEVELS - 1, Math.floor(value * LEVELS));
      const size = 1.1 + ((level + 1) / LEVELS) * (STEP - 1.8);
      paths[level].rect(CENTER + x - size / 2, CENTER + y - size / 2, size, size);
    }
  }

  context.clearRect(0, 0, BASE, BASE);
  for (let level = 0; level < LEVELS; level += 1) {
    context.globalAlpha = 0.18 + (level / (LEVELS - 1)) * 0.82;
    context.fill(paths[level]);
  }
  context.globalAlpha = 1;
}

export function AiVoiceAnimation({ activity, size = BASE }: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const activityRef = useRef(activity);
  activityRef.current = activity;

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;

    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = BASE * ratio;
    canvas.height = BASE * ratio;
    context.setTransform(ratio, 0, 0, ratio, 0, 0);

    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
    const weights: Weights = { calm: 1, listening: 0, thinking: 0, speaking: 0 };
    let accent = readAccent("#2d6a55");
    let accentReadAt = 0;
    let frame = 0;
    let last = 0;
    let visible = true;
    const observer = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; });
    observer.observe(canvas);

    const tick = (now: number) => {
      frame = window.requestAnimationFrame(tick);
      const fps = reduced.matches ? 8 : 30;
      if (!visible || now - last < 1000 / fps) return;
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0;
      last = now;

      // Ease each state's weight toward the current activity (≈250ms).
      const target = activityRef.current;
      for (const key of ACTIVITIES) {
        const goal = key === target ? 1 : 0;
        weights[key] += (goal - weights[key]) * Math.min(1, dt * 4.5);
      }

      if (now - accentReadAt > 500) {
        accentReadAt = now;
        accent = readAccent(accent);
      }
      context.fillStyle = accent;
      const t = (now / 1000) * (reduced.matches ? 0.25 : 1);
      drawOrb(context, t, weights);
    };

    frame = window.requestAnimationFrame(tick);
    return () => {
      window.cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, []);

  return (
    <div className={`test-voice-animation ${activity}`} aria-hidden="true">
      <canvas ref={canvasRef} width={BASE} height={BASE} style={{ width: size, height: size, display: "block" }} />
    </div>
  );
}
