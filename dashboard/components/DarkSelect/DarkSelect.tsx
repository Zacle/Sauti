"use client";

import type { ReactNode } from "react";
import { Select } from "@/components/ui/select";

export type DarkSelectOption = { value: string; label: string };

/**
 * Legacy console API kept so existing call sites keep working; it renders the
 * shared `Select` from components/ui/select so every dropdown looks the same.
 * Prefer importing `Select` directly in new code.
 */
export function DarkSelect({ ariaLabel, icon, name, options, placeholder, required, value, onValueChange }: {
  ariaLabel: string;
  icon?: ReactNode;
  name?: string;
  options: DarkSelectOption[];
  placeholder?: string;
  required?: boolean;
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <Select
      ariaLabel={ariaLabel}
      icon={icon}
      name={name}
      options={options}
      placeholder={placeholder}
      required={required}
      value={value}
      onValueChange={onValueChange}
    />
  );
}
