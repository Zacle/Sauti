"use client";

import { useRef } from "react";
import NumberFlow from "@number-flow/react";
import { useInView, useReducedMotion } from "motion/react";
import { ArrowDownRight, ArrowUpRight, AudioLines, PhoneIncoming, Star, Zap } from "lucide-react";
import styles from "./Stats.module.css";

const stats = [
  { value: 42, prefix: "+", suffix: "%", label: "More calls answered", decimals: 0, icon: PhoneIncoming, tone: "mint" },
  { value: 31, prefix: "−", suffix: "%", label: "Lower operating cost", decimals: 0, icon: ArrowDownRight, tone: "blue" },
  { value: 1.2, prefix: "<", suffix: "s", label: "Response time", decimals: 1, icon: Zap, tone: "violet" },
  { value: 4.8, prefix: "", suffix: "/5", label: "Caller rating", decimals: 1, icon: Star, tone: "amber" },
];

export function Stats() {
  const ref = useRef<HTMLDListElement>(null);
  const inView = useInView(ref, { once: true, margin: "0px 0px -80px 0px" });
  const reduced = useReducedMotion();

  return (
    <dl ref={ref} className={styles.grid}>
      {stats.map((stat, index) => {
        const Icon = stat.icon;
        return (
        <div
          key={stat.label}
          className={styles.card}
          data-tone={stat.tone}
        >
          <dt className={styles.label}><span className={styles.icon}><Icon size={17} aria-hidden="true" /></span>{stat.label}</dt>
          <dd className={styles.value}>
            <NumberFlow
              locales="en-US"
              value={inView || reduced ? stat.value : 0}
              animated={!reduced}
              prefix={stat.prefix}
              suffix={stat.suffix}
              format={{ minimumFractionDigits: stat.decimals, maximumFractionDigits: stat.decimals }}
              transformTiming={{ duration: 1100, easing: "cubic-bezier(0.16, 1, 0.3, 1)" }}
              spinTiming={{ duration: 1100, easing: "cubic-bezier(0.16, 1, 0.3, 1)" }}
            />
          </dd>
          <div className={styles.visual} aria-hidden="true">
            {index === 0 ? <div className={styles.callVisual}><span className={styles.callLine} /><span className={styles.callNode}><PhoneIncoming size={18} /></span><span className={styles.callLine} /><span className={styles.increase}><ArrowUpRight size={15} />42%</span></div>
              : index === 1 ? <div className={styles.costVisual}><span /><span /><ArrowDownRight size={18} /></div>
              : index === 2 ? <div className={styles.waveVisual}><AudioLines size={18} />{[8,14,24,12,32,20,38,18,28,14,24,10,18,8,12,6].map((height,i) => <i key={i} style={{ height }} />)}<span className={styles.latencyDot} /></div>
              : <div className={styles.ratingVisual}><span className={styles.stars}>{[0,1,2,3,4].map(i => <Star key={i} size={22} />)}<span className={styles.starFill}>{[0,1,2,3,4].map(i => <Star key={i} size={22} fill="currentColor" />)}</span></span><span className={styles.ratingMax}>/ 5</span></div>}
          </div>
        </div>
      );})}
    </dl>
  );
}
