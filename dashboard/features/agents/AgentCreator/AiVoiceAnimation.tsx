"use client";

import { useEffect, useRef } from "react";

export type VoiceAnimationActivity = "calm" | "listening" | "thinking" | "speaking";

type AiVoiceAnimationProps = {
  activity: VoiceAnimationActivity;
};

const SIZE = 240;
const CENTER = SIZE / 2;
const POINT_COUNT = 120;

const ACTIVITY_MOTION: Record<
  VoiceAnimationActivity,
  { amplitude: number; speed: number; energy: number }
> = {
  calm: { amplitude: 7.2, speed: 0.95, energy: 0.82 },
  listening: { amplitude: 9.2, speed: 1.08, energy: 0.88 },
  thinking: { amplitude: 11.5, speed: 1.22, energy: 0.94 },
  speaking: { amplitude: 14.5, speed: 1.42, energy: 1 },
};

function drawClosedCurve(
  context: CanvasRenderingContext2D,
  points: Array<[number, number]>,
) {
  const first = points[0];
  const last = points[points.length - 1];
  context.beginPath();
  context.moveTo((last[0] + first[0]) / 2, (last[1] + first[1]) / 2);

  for (let index = 0; index < points.length; index += 1) {
    const current = points[index];
    const next = points[(index + 1) % points.length];
    context.quadraticCurveTo(
      current[0],
      current[1],
      (current[0] + next[0]) / 2,
      (current[1] + next[1]) / 2,
    );
  }

  context.closePath();
}

function drawVoiceRing(
  context: CanvasRenderingContext2D,
  frame: number,
  activity: VoiceAnimationActivity,
) {
  const { amplitude, speed, energy } = ACTIVITY_MOTION[activity];
  const time = frame * 0.06 * speed;
  context.clearRect(0, 0, SIZE, SIZE);
  context.globalCompositeOperation = "lighter";

  for (let layer = 0; layer < 7; layer += 1) {
    const layerPhase = layer * 0.82;
    const baseRadius = 68 + layer * 0.55;
    const points: Array<[number, number]> = [];

    for (let index = 0; index < POINT_COUNT; index += 1) {
      const angle = (index / POINT_COUNT) * Math.PI * 2;
      const ripple =
        Math.sin(angle * 3 + time * 1.05 + layerPhase) * amplitude * 0.58 +
        Math.sin(angle * 5 - time * 1.42 + layerPhase * 0.62) * amplitude * 0.31 +
        Math.cos(angle * 7 + time * 0.74 - layerPhase * 0.8) * amplitude * 0.18;
      const breathing = Math.sin(time * 0.82 + layerPhase) * 1.8;
      const radius = baseRadius + ripple + breathing;
      const orbit = time * 0.08 * (layer % 2 === 0 ? 1 : -1);

      points.push([
        CENTER + Math.cos(angle + orbit) * radius,
        CENTER + Math.sin(angle + orbit) * radius,
      ]);
    }

    drawClosedCurve(context, points);
    context.strokeStyle = layer % 3 === 0 ? "#6ffff0" : layer % 2 === 0 ? "#35eadb" : "#1dc9d1";
    context.globalAlpha = (0.32 + (6 - layer) * 0.045) * energy;
    context.lineWidth = 1.25 + layer * 0.27;
    context.shadowColor = layer % 2 === 0 ? "#25f1df" : "#16bfc9";
    context.shadowBlur = 7 + energy * 7;
    context.stroke();
  }

  context.globalAlpha = 0.58 * energy;
  context.lineWidth = 1.15;
  context.shadowColor = "#8afff3";
  context.shadowBlur = 12;
  context.strokeStyle = "#79fff0";

  const highlightPoints: Array<[number, number]> = [];
  for (let index = 0; index < POINT_COUNT; index += 1) {
    const angle = (index / POINT_COUNT) * Math.PI * 2;
    const radius =
      67 +
      Math.sin(angle * 4 - time * 1.18) * amplitude * 0.28 +
      Math.cos(angle * 6 + time * 0.9) * amplitude * 0.12;
    highlightPoints.push([
      CENTER + Math.cos(angle) * radius,
      CENTER + Math.sin(angle) * radius,
    ]);
  }
  drawClosedCurve(context, highlightPoints);
  context.stroke();
  context.globalCompositeOperation = "source-over";
  context.globalAlpha = 1;
}

export function AiVoiceAnimation({ activity }: AiVoiceAnimationProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;

    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = SIZE * pixelRatio;
    canvas.height = SIZE * pixelRatio;
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);

    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let animationFrame = 0;
    let startedAt = 0;
    let lastDrawnAt = 0;
    let lastMotionTick = -1;

    const draw = (timestamp: number) => {
      if (!startedAt) startedAt = timestamp;
      const reducedMotion = motionPreference.matches;
      const targetFps = reducedMotion ? 12 : 30;
      if (timestamp - lastDrawnAt < 1000 / targetFps) {
        animationFrame = window.requestAnimationFrame(draw);
        return;
      }
      lastDrawnAt = timestamp;
      const elapsedFrames = ((timestamp - startedAt) / 1000) * 30;
      const frame = reducedMotion ? elapsedFrames * 0.24 : elapsedFrames;
      drawVoiceRing(context, frame, activity);
      const motionTick = Math.floor(frame / 8);
      if (motionTick !== lastMotionTick) {
        canvas.dataset.motionTick = String(motionTick);
        lastMotionTick = motionTick;
      }
      animationFrame = window.requestAnimationFrame(draw);
    };

    const restart = () => {
      window.cancelAnimationFrame(animationFrame);
      startedAt = 0;
      lastDrawnAt = 0;
      lastMotionTick = -1;
      animationFrame = window.requestAnimationFrame(draw);
    };

    motionPreference.addEventListener("change", restart);
    animationFrame = window.requestAnimationFrame(draw);
    return () => {
      window.cancelAnimationFrame(animationFrame);
      motionPreference.removeEventListener("change", restart);
    };
  }, [activity]);

  return (
    <div className={`test-voice-animation ${activity}`} aria-hidden="true">
      <canvas ref={canvasRef} width={SIZE} height={SIZE} />
    </div>
  );
}
