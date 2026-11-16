"use client";

import { useEffect, useRef } from "react";

const GAP = 9;
const DOT = 2.3;
const BUCKETS = 8;
const FRAME_MS = 1000 / 30;

/**
 * The homepage's one standout effect: a dot-matrix voice field. Columns of
 * dots light up inside a moving speech envelope, so the hero reads as "a voice
 * is talking" without stock imagery.
 *
 * Kept cheap: the dim background grid is pre-rendered once per resize, lit
 * dots are batched into a few alpha buckets (one fill per bucket), the loop is
 * capped at 30fps, pauses off-screen, and reduced-motion users get one still frame.
 */
export function VoiceField({ className }: { className?: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const grid = document.createElement("canvas");
    let width = 0;
    let height = 0;
    let dpr = 1;
    let frame = 0;
    let last = 0;
    let visible = false;
    const start = performance.now();
    const whitePaths: Path2D[] = [];
    const signalPath = { current: new Path2D() };

    const resize = () => {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      const rect = canvas.getBoundingClientRect();
      width = rect.width;
      height = rect.height;
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);

      grid.width = canvas.width;
      grid.height = canvas.height;
      const gridContext = grid.getContext("2d");
      if (!gridContext) return;
      gridContext.setTransform(dpr, 0, 0, dpr, 0, 0);
      gridContext.fillStyle = "rgba(255,255,255,0.06)";
      for (let x = GAP / 2; x < width; x += GAP) {
        for (let y = GAP / 2; y < height; y += GAP) {
          gridContext.fillRect(x - DOT / 2, y - DOT / 2, DOT, DOT);
        }
      }
    };

    // Speech-like envelope: a few drifting sines gated by a slow "syllable" pulse.
    const envelope = (n: number, t: number) => {
      const taper = Math.sin(Math.PI * n) ** 0.8;
      const syllable = 0.55 + 0.45 * Math.sin(t * 1.7 + n * 3.1) * Math.sin(t * 0.63 + n * 1.3);
      const detail =
        0.5 * Math.sin(n * 22 + t * 2.4) + 0.3 * Math.sin(n * 47 - t * 3.3) + 0.2 * Math.sin(n * 91 + t * 5.1);
      return Math.max(0, taper * syllable * (0.55 + 0.45 * Math.abs(detail)));
    };

    const draw = (now: number) => {
      const t = (now - start) / 1000;
      context.clearRect(0, 0, width, height);
      context.setTransform(1, 0, 0, 1, 0, 0);
      context.drawImage(grid, 0, 0);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);

      for (let i = 0; i < BUCKETS; i += 1) whitePaths[i] = new Path2D();
      signalPath.current = new Path2D();

      const mid = height / 2;
      const maxAmp = height * 0.46;
      for (let x = GAP / 2; x < width; x += GAP) {
        const amp = envelope(x / Math.max(width, 1), t) * maxAmp;
        if (amp < GAP / 2) continue;
        const firstRow = Math.ceil((mid - amp - GAP / 2) / GAP) * GAP + GAP / 2;
        for (let row = firstRow; row <= mid + amp; row += GAP) {
          const depth = 1 - Math.abs(row - mid) / amp;
          const path = depth > 0.72 ? signalPath.current : whitePaths[Math.min(BUCKETS - 1, Math.floor(depth * BUCKETS))];
          path.rect(x - DOT / 2, row - DOT / 2, DOT, DOT);
        }
      }

      for (let i = 0; i < BUCKETS; i += 1) {
        context.fillStyle = `rgba(255,255,255,${(0.18 + ((i + 0.5) / BUCKETS) * 0.6).toFixed(2)})`;
        context.fill(whitePaths[i]);
      }
      context.fillStyle = "rgba(94,234,212,0.9)";
      context.fill(signalPath.current);
    };

    const loop = (now: number) => {
      if (!visible) return;
      if (now - last >= FRAME_MS) {
        last = now;
        draw(now);
      }
      frame = requestAnimationFrame(loop);
    };

    resize();
    draw(start + 2400);

    const resizeObserver = new ResizeObserver(() => {
      resize();
      draw(reduceMotion ? start + 2400 : performance.now());
    });
    resizeObserver.observe(canvas);

    const intersection = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      cancelAnimationFrame(frame);
      if (visible) frame = requestAnimationFrame(loop);
    });
    if (!reduceMotion) intersection.observe(canvas);

    return () => {
      visible = false;
      cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      intersection.disconnect();
    };
  }, []);

  return <canvas ref={canvasRef} aria-hidden="true" className={className} />;
}
