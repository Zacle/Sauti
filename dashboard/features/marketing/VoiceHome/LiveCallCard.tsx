"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { CalendarCheck, DatabaseZap, PhoneIncoming } from "lucide-react";

type Line =
  | { kind: "caller" | "agent"; text: string }
  | { kind: "tool"; text: string; icon: "calendar" | "crm" };

const script: Line[] = [
  { kind: "caller", text: "Habari, I'd like to book a cleaning for Friday." },
  { kind: "agent", text: "Of course. Morning or afternoon works better for you?" },
  { kind: "caller", text: "Morning, around ten if possible." },
  { kind: "tool", text: "calendar.check_availability → Fri 10:00 free", icon: "calendar" },
  { kind: "agent", text: "You're booked for Friday at 10:00. I'll text a confirmation." },
  { kind: "tool", text: "crm.update_contact → booking attached", icon: "crm" },
];

/** A looping, self-typing transcript of a live call, used as the hero's product proof. */
export function LiveCallCard() {
  const reduce = useReducedMotion();
  const [count, setCount] = useState(reduce ? script.length : 1);
  // Bumped on each loop so the whole transcript cross-fades instead of
  // individual bubbles exiting while new ones lay out on top of them.
  const [cycle, setCycle] = useState(0);

  useEffect(() => {
    if (reduce) return;
    const finished = count >= script.length;
    const timer = window.setTimeout(() => {
      if (finished) {
        setCycle((value) => value + 1);
        setCount(1);
      } else {
        setCount((value) => value + 1);
      }
    }, finished ? 3600 : 1500);
    return () => window.clearTimeout(timer);
  }, [count, reduce]);

  const seconds = 4 + count * 3;

  return (
    <div className="w-full overflow-hidden rounded-2xl border border-line-strong bg-surface/80 shadow-[0_40px_120px_-40px_rgba(0,0,0,0.9)] backdrop-blur-xl">
      <div className="flex items-center justify-between border-b border-line px-4 py-3">
        <div className="flex items-center gap-2.5">
          <span className="grid size-7 place-items-center rounded-full bg-white/[0.06]">
            <PhoneIncoming className="size-3.5 text-signal" aria-hidden="true" />
          </span>
          <div className="leading-tight">
            <p className="text-[0.8rem] font-medium text-fg">Inbound call · Bright Smile Dental</p>
            <p className="font-mono text-[0.68rem] text-fg-muted">+254 7•• ••• 214 · Kiswahili / English</p>
          </div>
        </div>
        <span className="flex items-center gap-1.5 font-mono text-[0.68rem] text-fg-soft">
          <span className="relative flex size-1.5">
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-signal opacity-60" />
            <span className="relative inline-flex size-1.5 rounded-full bg-signal" />
          </span>
          00:{String(seconds).padStart(2, "0")}
        </span>
      </div>

      <div className="relative min-h-[23rem] sm:min-h-[19.5rem]">
      <AnimatePresence initial={false}>
      <motion.ul
        key={cycle}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0, transition: { duration: 0.35 } }}
        className="absolute inset-0 flex flex-col justify-end gap-2.5 p-4"
        aria-live="off"
      >
          {script.slice(0, count).map((line, index) => (
            <motion.li
              key={`${index}-${line.text}`}
              layout={!reduce}
              initial={reduce || index === 0 ? false : { opacity: 0, y: 10, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
              className={lineClass(line.kind)}
            >
              {line.kind === "tool" ? (
                <>
                  {line.icon === "calendar" ? (
                    <CalendarCheck className="size-3.5 shrink-0 text-signal" aria-hidden="true" />
                  ) : (
                    <DatabaseZap className="size-3.5 shrink-0 text-signal" aria-hidden="true" />
                  )}
                  <span className="truncate">{line.text}</span>
                </>
              ) : (
                line.text
              )}
            </motion.li>
          ))}
      </motion.ul>
      </AnimatePresence>
      </div>
    </div>
  );
}

function lineClass(kind: Line["kind"]) {
  if (kind === "tool") {
    return "flex items-center gap-2 self-start rounded-md border border-dashed border-line-strong px-2.5 py-1.5 font-mono text-[0.7rem] text-fg-soft";
  }
  if (kind === "agent") {
    return "max-w-[85%] self-end rounded-2xl rounded-br-md bg-white px-3.5 py-2 text-[0.84rem] leading-snug text-black";
  }
  return "max-w-[85%] self-start rounded-2xl rounded-bl-md bg-white/[0.07] px-3.5 py-2 text-[0.84rem] leading-snug text-fg";
}
