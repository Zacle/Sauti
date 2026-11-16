"use client";

import * as React from "react";
import * as AccordionPrimitive from "@radix-ui/react-accordion";
import { Plus } from "lucide-react";
import { cn } from "@/lib/utils";

export const Accordion = AccordionPrimitive.Root;

export function AccordionItem({
  className,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Item>) {
  return <AccordionPrimitive.Item className={cn("border-b border-line", className)} {...props} />;
}

export function AccordionTrigger({
  className,
  children,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Trigger>) {
  return (
    <AccordionPrimitive.Header className="m-0 flex text-base font-normal">
      <AccordionPrimitive.Trigger
        className={cn(
          "group flex w-full flex-1 cursor-pointer items-center justify-between gap-6 border-0 bg-transparent px-0 py-5 text-left font-sans text-[1.02rem] text-fg transition-colors hover:text-ink focus-visible:outline-2 focus-visible:outline-signal",
          className,
        )}
        {...props}
      >
        {children}
        <Plus
          aria-hidden="true"
          className="size-4 shrink-0 text-fg-muted transition-transform duration-300 ease-out-expo group-data-[state=open]:rotate-45 group-data-[state=open]:text-fg"
        />
      </AccordionPrimitive.Trigger>
    </AccordionPrimitive.Header>
  );
}

export function AccordionContent({
  className,
  children,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Content>) {
  return (
    <AccordionPrimitive.Content
      className="overflow-hidden data-[state=closed]:animate-[accordion-up_220ms_ease-out] data-[state=open]:animate-[accordion-down_260ms_cubic-bezier(0.16,1,0.3,1)]"
      {...props}
    >
      <div className={cn("pb-5 pr-10 text-[0.95rem] leading-relaxed text-fg-soft", className)}>{children}</div>
    </AccordionPrimitive.Content>
  );
}
