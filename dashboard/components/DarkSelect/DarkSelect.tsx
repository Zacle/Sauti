"use client";

import type { ReactNode } from "react";
import * as Select from "@radix-ui/react-select";
import { Check, ChevronDown, ChevronUp } from "lucide-react";
import styles from "./DarkSelect.module.css";

export type DarkSelectOption = { value: string; label: string };

export function DarkSelect({ ariaLabel, icon, name, options, placeholder, required, triggerClassName, value, onValueChange }: {
  ariaLabel: string;
  icon?: ReactNode;
  name?: string;
  options: DarkSelectOption[];
  placeholder?: string;
  required?: boolean;
  triggerClassName?: string;
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <Select.Root name={name} required={required} value={value} onValueChange={onValueChange}>
      <Select.Trigger
        className={`${styles.trigger} ${triggerClassName ?? ""}`.trim()}
        aria-label={ariaLabel}
        data-has-icon={icon ? "true" : "false"}
      >
        {icon && <span className={styles.icon}>{icon}</span>}
        <Select.Value className={styles.value} placeholder={placeholder} />
        <Select.Icon className={styles.chevron}><ChevronDown size={16} /></Select.Icon>
      </Select.Trigger>
      <Select.Portal>
        <Select.Content className={styles.content} position="popper" sideOffset={7} align="start">
          <Select.ScrollUpButton className={styles.scrollButton}><ChevronUp size={15} /></Select.ScrollUpButton>
          <Select.Viewport className={styles.viewport}>
            {options.map((option) => (
              <Select.Item className={styles.item} key={option.value} value={option.value}>
                <Select.ItemIndicator className={styles.indicator}><Check size={14} /></Select.ItemIndicator>
                <Select.ItemText>{option.label}</Select.ItemText>
              </Select.Item>
            ))}
          </Select.Viewport>
          <Select.ScrollDownButton className={styles.scrollButton}><ChevronDown size={15} /></Select.ScrollDownButton>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  );
}
