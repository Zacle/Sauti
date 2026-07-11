"use client";

import type { ReactNode } from "react";
import * as Select from "@radix-ui/react-select";
import { Check, ChevronDown } from "lucide-react";
import styles from "./DarkSelect.module.css";

export type DarkSelectOption = { value: string; label: string };

export function DarkSelect({ ariaLabel, icon, options, value, onValueChange }: {
  ariaLabel: string;
  icon?: ReactNode;
  options: DarkSelectOption[];
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <Select.Root value={value} onValueChange={onValueChange}>
      <Select.Trigger className={styles.trigger} aria-label={ariaLabel}>
        {icon && <span className={styles.icon}>{icon}</span>}
        <Select.Value className={styles.value} />
        <Select.Icon className={styles.chevron}><ChevronDown size={16} /></Select.Icon>
      </Select.Trigger>
      <Select.Portal>
        <Select.Content className={styles.content} position="popper" sideOffset={7} align="start">
          <Select.Viewport className={styles.viewport}>
            {options.map((option) => (
              <Select.Item className={styles.item} key={option.value} value={option.value}>
                <Select.ItemIndicator className={styles.indicator}><Check size={14} /></Select.ItemIndicator>
                <Select.ItemText>{option.label}</Select.ItemText>
              </Select.Item>
            ))}
          </Select.Viewport>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  );
}
