"use client";

import { useState, type ReactNode } from "react";
import NumberFlow from "@number-flow/react";
import { ArrowRight, Bot, CalendarDays, Command, Inbox, Languages, PhoneCall, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label, Textarea } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import {
  Badge,
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
  Checkbox,
  EmptyState,
  Kbd,
  RadioCard,
  RadioGroup,
  Skeleton,
  Switch,
  Table,
  TableBody,
  TableHead,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Tooltip,
  TooltipProvider,
} from "@/components/ui/primitives";
import { Dialog, DialogClose, DialogContent, DialogTrigger, Drawer } from "@/components/ui/overlays";
import { CommandMenu } from "@/components/ui/command-menu";
import { StatCard, PageHeader } from "@/components/ui/console";
import { toast } from "@/components/ui/sonner";
import { COUNTRIES } from "@/lib/countries";
import { DateRangePicker, type DateRange } from "@/components/ui/date-range-picker";

const colors = [
  ["canvas", "#000000"],
  ["surface", "#0a0a0a"],
  ["surface-2", "#111111"],
  ["surface-3", "#181818"],
  ["fg", "#ededed"],
  ["fg-soft", "#a1a1a1"],
  ["fg-muted", "#6b6b6b"],
  ["signal", "#5eead4"],
  ["success", "#4ade80"],
  ["warning", "#fbbf24"],
  ["danger", "#ff7a7a"],
  ["info", "#60a5fa"],
  ["violet", "#a78bfa"],
];

function Block({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <section className="border-t border-line py-12">
      <div className="mb-6">
        <h2 className="text-xl font-medium tracking-tight text-white">{title}</h2>
        {note ? <p className="mt-1 text-sm text-fg-soft">{note}</p> : null}
      </div>
      {children}
    </section>
  );
}

export function DesignSystemShowcase() {
  const [industry, setIndustry] = useState("healthcare");
  const [country, setCountry] = useState("KE");
  const [enabled, setEnabled] = useState(true);
  const [checked, setChecked] = useState(true);
  const [plan, setPlan] = useState("growth");
  const [drawer, setDrawer] = useState(false);
  const [menu, setMenu] = useState(false);
  const [calls, setCalls] = useState(1284);
  const [range, setRange] = useState<DateRange | undefined>({ from: new Date(), to: new Date(Date.now() + 6 * 86400000) });

  return (
    <TooltipProvider>
      <main className="mk-v2 min-h-dvh bg-canvas">
        <div className="mx-auto max-w-[1100px] px-4 py-16 sm:px-8">
          <p className="font-mono text-[0.68rem] uppercase tracking-[0.18em] text-signal">Sauti UI</p>
          <h1 className="mt-4 text-5xl font-medium tracking-[-0.035em] text-white">
            Design system <span className="font-serif font-normal italic text-fg-soft">reference.</span>
          </h1>
          <p className="mt-4 max-w-2xl text-fg-soft">
            Every token and component used across marketing, auth and the console. Import from{" "}
            <code className="rounded bg-white/[0.06] px-1.5 py-0.5 font-mono text-[0.85em]">@/components/ui/*</code>. Built on
            Radix, shadcn patterns, Sonner, Vaul, cmdk, NumberFlow and Motion.
          </p>

          <Block title="Colour tokens" note="Tailwind names: bg-canvas, text-fg-soft, border-line, text-signal…">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
              {colors.map(([name, hex]) => (
                <div key={name} className="overflow-hidden rounded-xl border border-line">
                  <div className="h-16" style={{ background: hex }} />
                  <div className="px-3 py-2">
                    <p className="text-xs text-white">{name}</p>
                    <p className="font-mono text-[0.65rem] text-fg-muted">{hex}</p>
                  </div>
                </div>
              ))}
            </div>
          </Block>

          <Block title="Typography" note="Geist Sans for UI, Instrument Serif italic for one accent phrase per headline, Geist Mono for labels.">
            <div className="grid gap-4">
              <p className="text-5xl font-medium tracking-[-0.035em] text-white">
                Every call answered, <span className="font-serif font-normal italic text-fg-soft">in your language.</span>
              </p>
              <p className="text-2xl font-medium tracking-tight text-white">Section title</p>
              <p className="max-w-xl leading-relaxed text-fg-soft">Body copy uses fg-soft at 15–17px with relaxed leading.</p>
              <p className="font-mono text-[0.68rem] uppercase tracking-[0.18em] text-fg-muted">Eyebrow / label</p>
            </div>
          </Block>

          <Block title="Buttons">
            <div className="flex flex-wrap items-center gap-3">
              <Button>Primary <ArrowRight /></Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="ghost">Ghost</Button>
              <Button size="sm">Small</Button>
              <Button size="lg">Large</Button>
              <Button disabled>Disabled</Button>
              <Button variant="secondary" className="text-danger"><Trash2 /> Delete</Button>
            </div>
          </Block>

          <Block title="Form controls" note="Select switches to a searchable list automatically when there are more than 12 options.">
            <div className="grid gap-5 sm:grid-cols-2">
              <Label>Business name<Input placeholder="Acme Health" /></Label>
              <Label>Industry
                <Select value={industry} onValueChange={setIndustry} options={[
                  { value: "healthcare", label: "Healthcare" },
                  { value: "services", label: "Professional services", description: "Law, accounting, consulting" },
                  { value: "home", label: "Home services" },
                ]} />
              </Label>
              <Label>Country (searchable)
                <Select icon={<Languages />} value={country} onValueChange={setCountry} options={COUNTRIES.map((c) => ({ value: c.code, label: c.name, keywords: [c.code] }))} />
              </Label>
              <Label>Compact filter
                <Select size="sm" icon={<Bot />} value="all" onValueChange={() => undefined} options={[{ value: "all", label: "All agents" }, { value: "a", label: "Front desk" }]} />
              </Label>
              <div className="grid gap-2 text-sm font-medium text-fg">Date range<DateRangePicker value={range} onChange={setRange} presets={[{ label: "Next 7 days", range: () => ({ from: new Date(), to: new Date(Date.now() + 6 * 86400000) }) }]} /></div>
              <Label className="sm:col-span-2">Notes<Textarea placeholder="Anything else we should know?" /></Label>
              <div className="flex items-center gap-3 text-sm text-fg">
                <Switch checked={enabled} onCheckedChange={setEnabled} aria-label="Enable agent" /> Agent enabled
              </div>
              <label className="flex items-center gap-3 text-sm text-fg">
                <Checkbox checked={checked} onCheckedChange={(v) => setChecked(v === true)} /> Record calls
              </label>
              <RadioGroup value={plan} onValueChange={setPlan} className="sm:col-span-2 sm:grid-cols-3">
                <RadioCard value="launch" title="Launch" description="100 minutes" />
                <RadioCard value="growth" title="Growth" description="750 minutes" />
                <RadioCard value="scale" title="Scale" description="2,500 minutes" />
              </RadioGroup>
              <Alert tone="error" className="sm:col-span-2">Something went wrong. Please try again.</Alert>
              <Alert tone="success" className="sm:col-span-2">Saved. Your agent uses the new settings on its next call.</Alert>
            </div>
          </Block>

          <Block title="Badges">
            <div className="flex flex-wrap gap-2">
              <Badge>Neutral</Badge>
              <Badge tone="signal" dot>Live</Badge>
              <Badge tone="success">Answered</Badge>
              <Badge tone="warning">Pending</Badge>
              <Badge tone="danger">Missed</Badge>
              <Badge tone="info">Booked</Badge>
              <Badge tone="violet">Transferred</Badge>
            </div>
          </Block>

          <Block title="Console building blocks">
            <PageHeader
              eyebrow="Workspace"
              title="Calls"
              description="Every conversation your agents handled."
              actions={<><Button variant="secondary" size="sm">Export</Button><Button size="sm"><Plus /> New agent</Button></>}
            />
            <div className="mt-6 grid gap-4 sm:grid-cols-3">
              <StatCard label="Calls this week" value={calls} change={12.4} icon={<PhoneCall />} />
              <StatCard label="Booked" value={0.87} format={{ style: "percent" }} change={3.1} icon={<CalendarDays />} />
              <StatCard label="Missed" value={14} change={-22} invertChange icon={<Inbox />} />
            </div>
            <Button variant="secondary" size="sm" className="mt-4" onClick={() => setCalls((v) => v + Math.round(Math.random() * 90))}>
              Animate numbers
            </Button>
          </Block>

          <Block title="Cards, tabs and tables">
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Recent calls</CardTitle>
                  <CardDescription>Last 24 hours across all agents.</CardDescription>
                </div>
                <Tabs defaultValue="all">
                  <TabsList>
                    <TabsTrigger value="all">All</TabsTrigger>
                    <TabsTrigger value="missed">Missed</TabsTrigger>
                  </TabsList>
                  <TabsContent value="all" />
                  <TabsContent value="missed" />
                </Tabs>
              </CardHeader>
              <Table>
                <TableHead>
                  <tr><th>Caller</th><th>Agent</th><th>Outcome</th><th>Duration</th></tr>
                </TableHead>
                <TableBody>
                  <tr><td className="text-white">+254 7•• ••• 214</td><td>Front desk</td><td><Badge tone="info">Booked</Badge></td><td className="tabular-nums">2:14</td></tr>
                  <tr><td className="text-white">+33 6 •• •• 81</td><td>Front desk</td><td><Badge tone="success">Answered</Badge></td><td className="tabular-nums">1:02</td></tr>
                  <tr><td className="text-white">+255 7•• ••• 900</td><td>After hours</td><td><Badge tone="danger">Missed</Badge></td><td className="tabular-nums">0:00</td></tr>
                </TableBody>
              </Table>
              <CardFooter><Button variant="ghost" size="sm">View all calls <ArrowRight /></Button></CardFooter>
            </Card>
          </Block>

          <Block title="Loading and empty states">
            <div className="grid gap-4 sm:grid-cols-2">
              <Card className="p-6">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="mt-4 h-8 w-2/3" />
                <Skeleton className="mt-6 h-24 w-full" />
              </Card>
              <EmptyState icon={<PhoneCall />} title="No calls yet" description="Calls appear here as soon as your agent answers its first one." action={<Button size="sm">Test your agent</Button>} />
            </div>
          </Block>

          <Block title="Disclosure">
            <Accordion type="single" collapsible className="border-t border-line">
              <AccordionItem value="a"><AccordionTrigger>What languages are supported?</AccordionTrigger><AccordionContent>English, Kiswahili, French and more.</AccordionContent></AccordionItem>
              <AccordionItem value="b"><AccordionTrigger>Can I hand calls to my team?</AccordionTrigger><AccordionContent>Yes, with transfer rules.</AccordionContent></AccordionItem>
            </Accordion>
          </Block>

          <Block title="Overlays and feedback" note="Dialog for confirmations, Drawer (Vaul) for details, ⌘K command menu, Sonner toasts, tooltips.">
            <div className="flex flex-wrap gap-3">
              <Dialog>
                <DialogTrigger asChild><Button variant="secondary">Open dialog</Button></DialogTrigger>
                <DialogContent title="Delete this agent?" description="Calls and bookings are kept, but the agent stops answering immediately.">
                  <div className="flex justify-end gap-2">
                    <DialogClose asChild><Button variant="secondary" size="sm">Cancel</Button></DialogClose>
                    <DialogClose asChild><Button size="sm" className="bg-danger text-black hover:bg-danger/85" onClick={() => toast.success("Agent deleted")}>Delete</Button></DialogClose>
                  </div>
                </DialogContent>
              </Dialog>
              <Button variant="secondary" onClick={() => setDrawer(true)}>Open drawer</Button>
              <Button variant="secondary" onClick={() => setMenu(true)}><Command /> Command menu <Kbd>⌘K</Kbd></Button>
              <Button variant="secondary" onClick={() => toast.success("Settings saved", { description: "Applies from the next call." })}>Success toast</Button>
              <Button variant="secondary" onClick={() => toast.error("Couldn't reach the calendar", { description: "Check the Google Calendar connection." })}>Error toast</Button>
              <Tooltip content="Agents answer in the caller's language"><Button variant="ghost">Hover for tooltip</Button></Tooltip>
            </div>
            <Drawer open={drawer} onOpenChange={setDrawer} title="Call with +254 7•• ••• 214" description="Front desk · 2:14 · Booked">
              <div className="grid gap-3">
                <p className="text-sm text-fg-soft">Drawers keep the list behind them in view. On phones they become a bottom sheet you can swipe down.</p>
                <p className="flex items-center gap-2 text-sm text-fg">Outcome <Badge tone="info">Booked</Badge> <NumberFlow value={2.14} format={{ minimumFractionDigits: 2 }} /></p>
              </div>
            </Drawer>
            <CommandMenu open={menu} onOpenChange={setMenu} />
          </Block>
        </div>
      </main>
    </TooltipProvider>
  );
}
