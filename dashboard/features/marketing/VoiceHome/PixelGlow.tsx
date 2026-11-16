"use client";

import { useEffect, useRef } from "react";

type RGB = readonly [number, number, number];

export const glowPalettes = {
  /** Sauti default: teal into blue with a violet edge. */
  signal: [
    [94, 234, 212],
    [59, 130, 246],
    [139, 92, 246],
  ],
  /** Cooler variant for quieter sections. */
  ocean: [
    [56, 189, 248],
    [37, 99, 235],
    [94, 234, 212],
  ],
  /** Warm accent for the closing call to action. */
  dusk: [
    [94, 234, 212],
    [99, 102, 241],
    [251, 191, 36],
  ],
} satisfies Record<string, readonly RGB[]>;

type Props = {
  className?: string;
  palette?: readonly RGB[];
  /** Pixel block size in CSS px at rest. The field steps through coarser sizes and back. */
  block?: number;
  /** Overall brightness, 0–1. */
  intensity?: number;
  /** Changes blob paths so neighbouring sections don't look identical. */
  seed?: number;
};

const FRAME_MS = 1000 / 24; // pixel-art cadence; also keeps it cheap
const LEVELS = 7; // brightness steps, gives the posterised/dithered look

/**
 * Animated pixel-mosaic glow: a few soft colour blobs drift on a coarse grid,
 * posterised into brightness steps with a little per-cell flicker, and the
 * grid periodically "resolves" from big blocks to small ones.
 *
 * The canvas is only (width / block) × (height / block) pixels and is
 * upscaled by CSS with `image-rendering: pixelated`, so each frame touches a
 * couple of thousand pixels at most. Pauses off-screen; static for
 * reduced-motion users.
 */
export function PixelGlow({ className, palette = glowPalettes.signal, block = 18, intensity = 0.85, seed = 1 }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const resolveSteps = [block * 2.6, block * 1.8, block * 1.3, block, block, block, block];
    let cssWidth = 0;
    let cssHeight = 0;
    let cols = 0;
    let rows = 0;
    let image: ImageData | null = null;
    let frame = 0;
    let last = 0;
    let visible = false;
    let stepIndex = 0;
    let stepChangedAt = 0;
    const start = performance.now();

    const rand = (n: number) => {
      const x = Math.sin(n * 12.9898 + seed * 78.233) * 43758.5453;
      return x - Math.floor(x);
    };

    const blobs = palette.map((color, index) => ({
      color,
      ax: 0.18 + rand(index + 1) * 0.22,
      ay: 0.16 + rand(index + 11) * 0.2,
      fx: 0.07 + rand(index + 21) * 0.08,
      fy: 0.06 + rand(index + 31) * 0.07,
      px: rand(index + 41) * Math.PI * 2,
      py: rand(index + 51) * Math.PI * 2,
      cx: 0.3 + rand(index + 61) * 0.4,
      cy: 0.35 + rand(index + 71) * 0.3,
      r: 0.22 + rand(index + 81) * 0.14,
    }));

    const setGrid = (size: number) => {
      cols = Math.max(4, Math.ceil(cssWidth / size));
      rows = Math.max(3, Math.ceil(cssHeight / size));
      canvas.width = cols;
      canvas.height = rows;
      image = context.createImageData(cols, rows);
    };

    const measure = () => {
      const rect = canvas.getBoundingClientRect();
      cssWidth = rect.width;
      cssHeight = rect.height;
      setGrid(resolveSteps[stepIndex]);
    };

    const draw = (now: number) => {
      if (!image) return;
      const t = (now - start) / 1000;
      const tick = Math.floor(t * 6);
      const aspect = cssWidth / Math.max(cssHeight, 1);
      const data = image.data;

      const centers = blobs.map((blob) => ({
        x: (blob.cx + Math.sin(t * blob.fx * Math.PI * 2 + blob.px) * blob.ax) * aspect,
        y: blob.cy + Math.cos(t * blob.fy * Math.PI * 2 + blob.py) * blob.ay,
        r2: blob.r * blob.r * (1 + 0.15 * Math.sin(t * 0.7 + blob.px)),
        color: blob.color,
      }));

      let offset = 0;
      for (let y = 0; y < rows; y += 1) {
        const v = (y + 0.5) / rows;
        for (let x = 0; x < cols; x += 1) {
          const u = ((x + 0.5) / cols) * aspect;
          let r = 0;
          let g = 0;
          let b = 0;
          let weight = 0;
          for (const c of centers) {
            const dx = u - c.x;
            const dy = v - c.y;
            const w = Math.exp(-(dx * dx + dy * dy) / c.r2);
            r += c.color[0] * w;
            g += c.color[1] * w;
            b += c.color[2] * w;
            weight += w;
          }
          // Per-cell flicker, re-rolled a few times a second.
          const cell = x * 73856093 + y * 19349663;
          const flicker = (rand(cell + tick * 83492791) - 0.5) * 0.22;
          const level = Math.round(Math.min(1, Math.max(0, weight + flicker)) * LEVELS) / LEVELS;
          const norm = weight > 0 ? 1 / weight : 0;
          data[offset] = r * norm;
          data[offset + 1] = g * norm;
          data[offset + 2] = b * norm;
          data[offset + 3] = level * level * 255 * intensity;
          offset += 4;
        }
      }
      context.putImageData(image, 0, 0);
    };

    const loop = (now: number) => {
      if (!visible) return;
      if (now - last >= FRAME_MS) {
        last = now;
        // Step through the "resolve" sequence: coarse → fine, then hold.
        const hold = stepIndex >= resolveSteps.length - 1 ? 5200 : 260;
        if (now - stepChangedAt > hold) {
          stepIndex = (stepIndex + 1) % resolveSteps.length;
          stepChangedAt = now;
          setGrid(resolveSteps[stepIndex]);
        }
        draw(now);
      }
      frame = requestAnimationFrame(loop);
    };

    if (reduceMotion) stepIndex = resolveSteps.length - 1;
    measure();
    draw(start + 4000);

    const resizeObserver = new ResizeObserver(() => {
      measure();
      draw(performance.now());
    });
    resizeObserver.observe(canvas);

    const intersection = new IntersectionObserver(([entry]) => {
      const wasVisible = visible;
      visible = entry.isIntersecting;
      cancelAnimationFrame(frame);
      if (visible) {
        if (!wasVisible) {
          // Re-run the resolve whenever the section scrolls into view.
          stepIndex = 0;
          stepChangedAt = performance.now();
          setGrid(resolveSteps[0]);
        }
        frame = requestAnimationFrame(loop);
      }
    });
    if (!reduceMotion) intersection.observe(canvas);

    return () => {
      visible = false;
      cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      intersection.disconnect();
    };
  }, [block, intensity, palette, seed]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className={className}
      style={{ imageRendering: "pixelated" }}
    />
  );
}
