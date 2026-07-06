import type { LucideIcon } from "lucide-react";
import styles from "./ConsolePlaceholder.module.css";

export function ConsolePlaceholder({
  icon: Icon,
  eyebrow,
  title,
  description,
}: {
  icon: LucideIcon;
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <section className={styles.root}>
      <span className={styles.icon}><Icon size={24} /></span>
      <small className={styles.eyebrow}>{eyebrow}</small>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.description}>{description}</p>
    </section>
  );
}
