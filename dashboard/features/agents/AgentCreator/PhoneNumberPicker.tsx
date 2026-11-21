"use client";


import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { Checkbox, RadioDot } from "@/components/ui/primitives";
import { Select } from "@/components/ui/select";
import {
listAvailableAgentNumbers,
provisionAgentNumber
} from "@/lib/api/agents";
import { COUNTRIES } from "@/lib/countries";
import { cn } from "@/lib/utils";
import type { Agent, AvailablePhoneNumber } from "@/types/api";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import {
CircleAlert,
LoaderCircle,
Phone,
RefreshCw,
ShieldCheck,
X
} from "lucide-react";
import { useEffect, useState } from "react";
import {
Note
} from "./studio-kit";


export function CountryFlag({ code }: { code: string }) {
  return (
    <span
      aria-hidden="true"
      className={`fi fi-${code.toLowerCase()} block h-[14px] w-[19px] shrink-0 overflow-hidden rounded-[3px] bg-cover bg-center shadow-[0_0_0_1px_rgb(0_0_0/0.12)]`}
    />
  );
}

export const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({
  value: country.code,
  label: country.name,
  keywords: [country.code],
  leading: <CountryFlag code={country.code} />,
}));

export function PhoneNumberPicker({
  agentId,
  defaultCountryCode,
  existingNumber,
  existingProvider,
  onClose,
  onAssigned,
}: {
  agentId: string;
  defaultCountryCode: string;
  existingNumber?: string | null;
  existingProvider?: string | null;
  onClose: () => void;
  onAssigned: (agent: Agent) => void;
}) {
  const [countryCode, setCountryCode] = useState(defaultCountryCode);
  const [numbers, setNumbers] = useState<AvailablePhoneNumber[]>([]);
  const [selectedNumber, setSelectedNumber] = useState("");
  const [loading, setLoading] = useState(false);
  const [ordering, setOrdering] = useState(false);
  const [error, setError] = useState("");
  const [providerChargesConfirmed, setProviderChargesConfirmed] = useState(false);
  const [warningDismissed, setWarningDismissed] = useState(false);
  const selectedCountry = COUNTRIES.find((country) => country.code === countryCode);
  const selected = numbers.find((number) => number.phoneNumber === selectedNumber);

  async function searchNumbers() {
    setLoading(true);
    setError("");
    setSelectedNumber("");
    setProviderChargesConfirmed(false);
    try {
      const availableNumbers = await listAvailableAgentNumbers(agentId, countryCode, 12);
      setNumbers(availableNumbers);
      setSelectedNumber(availableNumbers[0]?.phoneNumber ?? "");
    } catch (caught) {
      setNumbers([]);
      setError(caught instanceof Error ? caught.message : "Unable to load available numbers.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void searchNumbers();
    setWarningDismissed(false);
    // Search whenever the user changes market. The function only depends on these values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentId, countryCode]);

  async function assignNumber(phoneNumber?: string) {
    setOrdering(true);
    setError("");
    try {
      onAssigned(await provisionAgentNumber(
        agentId,
        phoneNumber ?? "",
        Boolean(existingNumber),
        providerChargesConfirmed,
      ));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to assign this number.");
      setOrdering(false);
    }
  }

  return (
    <DialogPrimitive.Root open onOpenChange={(open) => { if (!open && !ordering) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[150] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          onInteractOutside={(event) => { if (ordering) event.preventDefault(); }}
          className="fixed left-1/2 top-1/2 z-[151] flex max-h-[min(90dvh,760px)] w-[min(94vw,680px)] -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[var(--radius-card)] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)] max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:max-h-[92dvh] max-sm:w-full max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-b-none"
        >
          <header className="flex items-start justify-between gap-4 px-6 pb-4 pt-5">
            <div className="min-w-0">
              <DialogPrimitive.Title className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">
                {existingNumber ? "Change phone number" : "Choose a phone number"}
              </DialogPrimitive.Title>
              <p className="m-0 mt-1 text-[0.85rem] leading-relaxed text-fg-soft">Pick the country your customers call from. Availability and paperwork vary by country.</p>
            </div>
            <DialogPrimitive.Close asChild>
              <Button variant="ghost" size="icon-xs" aria-label="Close" disabled={ordering} className="-mr-1.5"><X /></Button>
            </DialogPrimitive.Close>
          </header>

          <div className="flex items-end gap-2 px-6 pb-4">
            <div className="grid min-w-0 flex-1 gap-1.5 sm:max-w-[18rem]">
              <span className="text-[0.8rem] font-medium text-ink">Country</span>
              <Select
                size="md"
                className="h-10 text-[0.88rem]"
                ariaLabel="Country"
                searchable
                searchPlaceholder="Search countries"
                value={countryCode}
                onValueChange={setCountryCode}
                options={COUNTRY_OPTIONS}
                disabled={ordering}
              />
            </div>
            <Button variant="secondary" size="sm" className="h-10" disabled={loading || ordering} onClick={() => void searchNumbers()}>
              <RefreshCw className={loading ? "spin" : undefined} /> Refresh
            </Button>
          </div>

          {existingNumber && !warningDismissed ? (
            <div className="px-6 pb-4">
              <Note
                tone="warning"
                icon={<CircleAlert />}
                title={`This replaces ${existingNumber}`}
                action={<Button variant="ghost" size="icon-xs" aria-label="Dismiss" onClick={() => setWarningDismissed(true)}><X /></Button>}
              >
                Your current {readableProvider(existingProvider)} number stays in that provider account and may keep billing until you release it there.
              </Note>
            </div>
          ) : null}

          <div className="flex items-center justify-between gap-3 border-y border-line bg-tint/[0.02] px-6 py-2 text-[0.75rem] text-fg-muted">
            <span className="font-medium text-fg-soft">Available in {selectedCountry?.name ?? countryCode}</span>
            <span className="tabular-nums">{loading ? "Checking…" : `${numbers.length} ${numbers.length === 1 ? "number" : "numbers"}`}</span>
          </div>

          <div className="min-h-[200px] flex-1 overflow-y-auto px-3 py-2">
            {loading ? (
              <div className="grid gap-2 p-3">{[0, 1, 2, 3].map((item) => <div key={item} className="h-14 animate-pulse rounded-lg bg-tint/[0.05]" />)}</div>
            ) : numbers.length ? (
              <div role="radiogroup" aria-label="Available numbers" className="grid gap-0.5">
                {numbers.map((number, index) => {
                  const checked = selectedNumber === number.phoneNumber;
                  const place = [number.locality, number.region].filter(Boolean).join(", ");
                  return (
                    <div
                      key={number.phoneNumber}
                      role="radio"
                      aria-checked={checked}
                      tabIndex={0}
                      onClick={() => { setSelectedNumber(number.phoneNumber); setProviderChargesConfirmed(false); }}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          setSelectedNumber(number.phoneNumber);
                          setProviderChargesConfirmed(false);
                        }
                      }}
                      className={cn(
                        "group grid cursor-pointer grid-cols-[18px_minmax(0,1fr)_auto] items-center gap-3 rounded-lg border px-3 py-2.5 outline-none transition-colors focus-visible:ring-2 focus-visible:ring-signal/40",
                        checked ? "border-signal/35 bg-signal/[0.07]" : "border-transparent hover:bg-tint/[0.04]",
                      )}
                    >
                      <RadioDot checked={checked} />
                      <span className="min-w-0">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-[0.92rem] font-medium tabular-nums text-ink">{number.phoneNumber}</span>
                          {index === 0 ? <span className="rounded-[4px] bg-signal/10 px-1.5 py-[3px] text-[0.68rem] font-medium leading-none text-signal">Recommended</span> : null}
                        </span>
                        <span className="mt-0.5 block truncate text-[0.76rem] text-fg-muted">
                          {[place || selectedCountry?.name, readableNumberType(number.type)].filter(Boolean).join(" · ")}
                        </span>
                      </span>
                      <span className="text-right">
                        <span className="block text-[0.88rem] font-medium tabular-nums text-ink">{formatPhoneNumberCost(number.monthlyCost, number.currency)}</span>
                        <span className="block text-[0.72rem] text-fg-muted">per month</span>
                      </span>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="grid place-items-center gap-1.5 px-6 py-12 text-center">
                <span className="mb-1 grid size-10 place-items-center rounded-full bg-tint/[0.06] text-fg-muted"><Phone className="size-[18px]" /></span>
                <p className="m-0 text-[0.9rem] font-medium text-ink">No numbers to choose from in {selectedCountry?.name ?? countryCode}</p>
                <p className="m-0 max-w-sm text-[0.82rem] leading-relaxed text-fg-soft">{error || "Try another country, or contact support and we’ll source one for you."}</p>
              </div>
            )}
          </div>

          {error && numbers.length > 0 ? <div className="px-6 pt-3"><Alert>{error}</Alert></div> : null}

          {selected ? (
            <div className="px-6 pt-4">
              <label className="flex cursor-pointer items-start gap-3 rounded-[var(--radius-control)] border border-line p-3.5 transition-colors hover:bg-tint/[0.02]">
                <Checkbox
                  checked={providerChargesConfirmed}
                  disabled={ordering}
                  onCheckedChange={(next) => setProviderChargesConfirmed(next === true)}
                  className="mt-px"
                />
                <span className="min-w-0">
                  <span className="block text-[0.86rem] font-medium text-ink">
                    I agree to pay {formatPhoneNumberCost(selected.monthlyCost, selected.currency)} a month for {selected.phoneNumber}
                  </span>
                  <span className="mt-0.5 block text-[0.78rem] leading-relaxed text-fg-muted">
                    {Number(selected.upfrontCost) > 0 ? `Plus a one-off ${formatPhoneNumberCost(selected.upfrontCost, selected.currency)} setup charge. ` : ""}Calls, texts, taxes and carrier fees are billed separately. Numbers that need identity documents stay pending until approved.
                  </span>
                </span>
              </label>
            </div>
          ) : null}

          <footer className="mt-4 flex items-center justify-between gap-3 border-t border-line px-6 py-3.5 max-sm:flex-col max-sm:items-stretch">
            <p className="m-0 flex items-center gap-1.5 text-[0.76rem] text-fg-muted">
              <ShieldCheck className="size-3.5 shrink-0" /> Nothing is charged until you assign a number.
            </p>
            <div className="flex gap-2 max-sm:justify-end">
              <DialogPrimitive.Close asChild>
                <Button variant="secondary" size="sm" disabled={ordering}>Cancel</Button>
              </DialogPrimitive.Close>
              <Button size="sm" disabled={!selectedNumber || !providerChargesConfirmed || ordering} onClick={() => void assignNumber(selectedNumber)}>
                {ordering ? <LoaderCircle className="spin" /> : null}
                {ordering ? "Assigning…" : "Assign number"}
              </Button>
            </div>
          </footer>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

export function readableNumberType(value: string) {
  if (!value) return "";
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function readableProvider(value?: string | null) {
  if (!value || value === "legacy") return "previous provider";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function readablePhoneStatus(value?: string | null) {
  if (!value || value === "legacy" || value === "active" || value === "success") return "Connected";
  if (value === "pending") return "Pending activation";
  if (value === "failed" || value === "failure") return "Activation failed";
  return readableNumberType(value);
}

export function formatPhoneNumberCost(value: string, currency: string) {
  const amount = Number(value);
  if (!value || Number.isNaN(amount)) return "Price on order";
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: currency || "USD",
      minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency || "USD"}`;
  }
}
