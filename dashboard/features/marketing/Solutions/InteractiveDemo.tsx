"use client";

import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useInView, useReducedMotion } from "motion/react";
import { ArrowRight, BookOpen, CalendarDays, Check, CheckCheck, ChevronRight, FileText, Globe2, Headphones, MessageSquare, Moon, MousePointer2, Phone, PhoneForwarded, Pause, Play, RotateCcw, Sparkles, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";
import { walkthroughs, type SolutionSlug } from "./walkthrough-content";
import styles from "./InteractiveDemo.module.css";

const journeys = {
  "appointment-booking": { icon: CalendarDays, actions: ["Connect sample calendar", "Check availability", "Confirm sample booking"], titles: ["Give your agent a calendar", "A caller wants Friday morning", "Choose an available time"], done: "A place in the diary.", hint: "Click a time, then confirm the booking." },
  "customer-service": { icon: BookOpen, actions: ["Use this knowledge", "Find the approved answer", "Save the call summary"], titles: ["Start with information you trust", "A simple question comes in", "The answer has a source"], done: "Answered. And reviewable.", hint: "Choose a question to see its source and answer." },
  "lead-qualification": { icon: UserRound, actions: ["Use these questions", "Capture the caller’s needs", "Save the enquiry summary"], titles: ["Decide what a good enquiry includes", "Let the caller tell their story", "The details, ready for follow-up"], done: "Your next conversation starts here.", hint: "The caller’s words become a useful summary." },
  "human-transfer": { icon: PhoneForwarded, actions: ["Enable sample transfer", "Offer a human transfer", "Try the sample destination"], titles: ["Put a person within reach", "The caller asks for your team", "Choose how this example ends"], done: "A clear next step for the caller.", hint: "Try both destination states to explore the fallback." },
  "multilingual-call-handling": { icon: Globe2, actions: ["Use these languages", "Start the example conversation", "Keep the conversation"], titles: ["One agent. Three languages", "The caller chooses the language", "Same intent. Different words"], done: "The context stays with the call.", hint: "Switch languages to change the conversation." },
  "after-hours-answering": { icon: Moon, actions: ["Use this weekly schedule", "Try an after-hours call", "Apply the sample policy"], titles: ["Set the hours your team works", "It’s 21:42. The phone rings", "You decide what happens next"], done: "Closed doors. A considered response.", hint: "Choose a policy and see what the caller hears." },
} as const;

const languages = {
  English: ["Can I book an appointment for Friday?", "Yes, 10am is available. Shall I book that for you?"],
  Kiswahili: ["Habari, naweza kuweka miadi ya Ijumaa?", "Ndiyo, saa nne asubuhi inapatikana. Nikuwekee?"],
  Français: ["Puis-je prendre rendez-vous vendredi ?", "Oui, 10 heures est disponible. Voulez-vous réserver ce créneau ?"],
} as const;

function Choices({ label, values, value, onChange }: { label: string; values: readonly string[]; value: string; onChange: (value: string) => void }) {
  return <div className={styles.choices} role="group" aria-label={label}>{values.map(option => <button key={option} type="button" aria-pressed={value === option} onClick={() => onChange(option)} className={cn(styles.choice, value === option && styles.selected)}>{option}{value === option && <Check size={14} aria-hidden="true" />}</button>)}</div>;
}

/** All actions stay in this local simulation. No calls, credentials or account writes. */
export function InteractiveDemo({ slug }: { slug: SolutionSlug }) {
  const item = walkthroughs[slug];
  const journey = journeys[slug];
  const Icon = journey.icon;
  const reduced = useReducedMotion();
  const demoRef = useRef<HTMLDivElement>(null);
  const visible = useInView(demoRef, { amount: 0.15 });
  const [playing, setPlaying] = useState(true);
  const [pageVisible, setPageVisible] = useState(true);
  const autoplay = playing && !reduced;
  const running = autoplay && visible && pageVisible;
  const [stage, setStage] = useState(0);
  const [slot, setSlot] = useState("10:00");
  const [language, setLanguage] = useState("Kiswahili");
  const [policy, setPolicy] = useState("Collect a message");
  const [available, setAvailable] = useState("Team available");
  const [question, setQuestion] = useState("Saturday hours");
  const done = stage === 3;
  const lang = language as keyof typeof languages;
  const caller = slug === "multilingual-call-handling" ? languages[lang][0]
    : slug === "lead-qualification" && stage >= 2 ? "A three-bedroom rental in Kilimani, up to KES 120,000 a month. I’d like to move next month."
    : slug === "customer-service" && question === "Policy exception" ? "Can you waive the cancellation fee for me?" : item.caller;
  const answer = slug === "appointment-booking" ? `Friday at ${slot} is available. Shall I book that for you?`
    : slug === "multilingual-call-handling" ? languages[lang][1]
    : slug === "customer-service" ? question === "Saturday hours" ? item.reply : "I can’t approve an exception to that policy. Would you like me to refer this to the team?"
    : slug === "after-hours-answering" ? policy === "Answer normally" ? "I can help with that. Which day would you like to visit?" : policy === "Announce closure" ? "Our office is closed. Please call again from 9am tomorrow." : item.reply
    : slug === "human-transfer" ? available === "Team available" ? "I’ll try to connect you to our team now." : "The team is unavailable. May I take a message and your preferred callback time?"
    : slug === "lead-qualification" ? "Thanks. I’ve noted your requirements so the team can follow up with suitable options." : item.reply;
  const outcome = slug === "appointment-booking" ? `Cleaning · Friday, ${slot}`
    : slug === "human-transfer" ? available === "Team available" ? "Sample transfer connected" : "Message requested for the team"
    : slug === "after-hours-answering" ? policy === "Announce closure" ? "Closure announced · Call ended" : policy === "Answer normally" ? "Conversation continues with the agent" : "Callback requested · Tomorrow morning"
    : slug === "multilingual-call-handling" ? `Booking request · ${language}`
    : slug === "customer-service" && question === "Policy exception" ? "Policy exception · Human help offered" : item.result;

  function restart() { setStage(0); setSlot("10:00"); setLanguage("Kiswahili"); setPolicy("Collect a message"); setAvailable("Team available"); setQuestion("Saturday hours"); }

  useEffect(() => {
    const updateVisibility = () => setPageVisible(!document.hidden);
    updateVisibility();
    document.addEventListener("visibilitychange", updateVisibility);
    return () => document.removeEventListener("visibilitychange", updateVisibility);
  }, []);

  useEffect(() => {
    if (!running) return;
    const timer = window.setTimeout(() => {
      if (stage === 3) {
        setStage(0);
        setSlot("10:00"); setLanguage("Kiswahili"); setPolicy("Collect a message");
        setAvailable("Team available"); setQuestion("Saturday hours");
      } else setStage(current => current + 1);
    }, stage === 3 ? 4500 : 4000);
    return () => window.clearTimeout(timer);
  }, [running, stage]);

  return <div ref={demoRef} className={styles.demo} onClickCapture={event => {
    if ((event.target as HTMLElement).closest("button:not([data-playback-control])")) setPlaying(false);
  }}>
    <div className={styles.topline}><span className={styles.demoLabel}><MousePointer2 size={14} />Watch the workflow unfold</span><span>Interactive illustration · Sample data</span></div>
    <div className={styles.workspace}>
      <div className={styles.windowBar}><div className={styles.windowDots} aria-hidden="true"><i /><i /><i /></div><span>sauti / {item.agent.toLowerCase()}</span><button data-playback-control type="button" className={styles.restart} onClick={() => setPlaying(value => !value)} disabled={Boolean(reduced)} aria-label={autoplay ? "Pause demo" : "Play demo"}>{autoplay ? <Pause size={13} /> : <Play size={13} />}{autoplay ? "Pause" : "Play"}</button><button data-playback-control type="button" className={styles.restart} onClick={() => { restart(); setPlaying(true); }}><RotateCcw size={13} />Restart demo</button></div>
      <div className={styles.layout}>
        <aside className={styles.rail}>
          <div className={styles.agent}><span><Icon size={20} /></span><div><strong>{item.agent}</strong><small>Example workspace</small></div></div>
          <ol className={styles.steps}>{item.steps.map((label,index) => <li key={label} className={cn(stage === index && styles.activeStep, stage > index && styles.completedStep)}><span>{stage > index ? <Check size={14} /> : `0${index+1}`}</span><div><strong>{label}</strong><small>{index === 0 ? item.section : index === 1 ? "Test call" : "Review the outcome"}</small></div>{stage === index && <ChevronRight size={14} />}</li>)}</ol>
          <div className={styles.railNote}><ShieldNote /><p>A guided example.<br />Your real workspace stays untouched.</p></div>
        </aside>
        <div className={styles.main}>
          <div className={styles.sceneHeader}><span className={styles.eyebrow}>{done ? "Outcome / ready to review" : `Step 0${stage+1} / ${stage === 0 ? "Configure" : stage === 1 ? "Try a conversation" : "Take action"}`}</span><Badge tone={done ? "signal" : "neutral"}>{done ? "Complete" : "Explore"}</Badge></div>
          <h3 className={styles.title}>{done ? journey.done : journey.titles[stage]}</h3>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div key={stage} initial={{opacity:reduced ? 1 : 0,y:reduced ? 0 : 10}} animate={{opacity:1,y:0}} exit={{opacity:reduced ? 1 : 0,y:reduced ? 0 : -6}} transition={{duration:reduced ? 0 : 0.2}} className={styles.scene}>
              {stage === 0 ? <>
                <div className={styles.configCard}><div className={styles.cardHeading}><Icon size={18} /><strong>{item.section}</strong><span className={styles.draft}>Sample configuration</span></div>
                  {slug === "appointment-booking" ? <div className={styles.provider}><span className={styles.calendarLogo}><CalendarDays size={26} /></span><div><strong>Google Calendar</strong><p>Bright Smile Dental · Appointment calendar</p></div><Badge>Not connected</Badge></div>
                  : slug === "after-hours-answering" ? <div className={styles.week}>{["Mon","Tue","Wed","Thu","Fri","Sat","Sun"].map((day,index)=><div key={day} className={index > 4 ? styles.closedDay : ""}><span>{day}</span><i /><strong>{index > 4 ? "Closed" : "9–17"}</strong></div>)}</div>
                  : slug === "multilingual-call-handling" ? <div className={styles.languageGrid}>{Object.keys(languages).map((name,index)=><div key={name}><span>{["EN","SW","FR"][index]}</span><strong>{name}</strong><Check size={15} /></div>)}</div>
                  : slug === "lead-qualification" ? <div className={styles.questionList}>{["What are you looking for?","Which area do you prefer?","What is your monthly budget?","When would you like to move?"].map((text,index)=><div key={text}><span>0{index+1}</span>{text}<Check size={14} /></div>)}</div>
                  : slug === "human-transfer" ? <div className={styles.destination}><span><Headphones size={24} /></span><div><strong>Customer care</strong><p>Transfer number · +254 ••• ••• 214</p></div><Badge>Ready to enable</Badge></div>
                  : <div className={styles.document}><FileText size={28} /><div><strong>Front-desk guide.pdf</strong><p>Opening hours · Business policies · Escalation rules</p></div><Badge>Sample source</Badge></div>}
                </div><div className={styles.prompt}><span className={styles.eyebrow}>Agent instructions</span><p>{item.prompt}</p></div>
              </> : <div className={styles.twoColumns}>
                <div className={styles.callCard}><div className={styles.cardHeading}><Phone size={16} /><strong>Example call</strong><span className={styles.callTime}>00:42</span></div><div className={styles.wave} aria-hidden="true">{Array.from({length:32},(_,i)=><i key={i} style={{height:`${8+((i*17+9)%29)}px`}} />)}</div><div className={styles.bubble}><span>Caller</span><p>{caller}</p></div>{stage >= 2 && <div className={styles.agentBubble}><span>Sauti</span><p>{answer}</p></div>}<div className={styles.callFoot}><span className={styles.statusDot} />{done ? "Example complete" : stage === 1 ? "Caller intent received" : "Response based on your configuration"}</div></div>
                <div className={styles.actionCard}>
                  <div className={styles.cardHeading}><Icon size={16} /><strong>{done ? "Recorded outcome" : stage === 1 ? "Your agent is ready" : item.name}</strong></div>
                  {done ? <div className={styles.receipt}><span className={styles.successIcon}><CheckCheck size={28} /></span><h4>{outcome}</h4><p>{slug === "appointment-booking" ? "Amani · 30-minute cleaning · Google Calendar" : "Example call summary · Ready for your team to review"}</p><div className={styles.receiptLine}><FileText size={15} /><span>{slug === "appointment-booking" ? "Bookings / Appointment detail" : "Calls / Call detail"}</span><Check size={14} /></div></div>
                  : stage === 1 ? <div className={styles.ready}><span className={styles.readyIcon}><Sparkles size={28} /></span><h4>{slug === "appointment-booking" ? "Check before confirming." : "Your instructions guide the next step."}</h4><p>{slug === "appointment-booking" ? "A request is not a reservation. Check the connected calendar before offering a time." : item.notes[1]}</p><div className={styles.connection}><span>{item.section}</span><ArrowRight size={16} /><span>Conversation</span></div></div>
                  : <>
                    {slug === "appointment-booking" && <><p className={styles.controlLabel}>Friday · Select a 30-minute slot</p><div className={styles.slots}><button disabled type="button">09:30<span>Unavailable</span></button>{["10:00","10:30","11:00"].map(time=><button key={time} type="button" aria-pressed={slot === time} className={slot === time ? styles.selected : ""} onClick={()=>setSlot(time)}>{time}<span>{slot === time ? "Selected" : "Available"}</span>{slot === time && <Check size={14} />}</button>)}</div><p className={styles.smallNote}>Sample caller confirms: “Yes, {slot} works.”</p></>}
                    {slug === "customer-service" && <><Choices label="Example support question" values={["Saturday hours","Policy exception"]} value={question} onChange={setQuestion} /><div className={styles.source}><FileText size={16} /><span>Front-desk guide / {question === "Saturday hours" ? "Opening hours" : "Escalation rules"}</span><blockquote>{question === "Saturday hours" ? "Saturday: open 09:00–13:00" : "Policy exceptions must be referred to the team."}</blockquote></div></>}
                    {slug === "lead-qualification" && <dl className={styles.leadFields}>{[["Property","3-bedroom rental"],["Location","Kilimani"],["Budget","KES 120,000 / month"],["Moving","Next month"]].map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}<Check size={13} /></dd></div>)}</dl>}
                    {slug === "human-transfer" && <><Choices label="Sample destination availability" values={["Team available","Team unavailable"]} value={available} onChange={setAvailable} /><div className={styles.transferPath}><span><Phone size={20} />Caller</span><i /><span className={styles.routeNode}><PhoneForwarded size={22} />Sauti</span><i /><span>{available === "Team available" ? <Headphones size={20} /> : <MessageSquare size={20} />}{available === "Team available" ? "Team" : "Message"}</span></div><p className={styles.smallNote}>{available === "Team available" ? "Try the transfer to your configured number." : "Follow the fallback instructions in Prompt."}</p></>}
                    {slug === "multilingual-call-handling" && <><Choices label="Example conversation language" values={Object.keys(languages)} value={language} onChange={setLanguage} /><div className={styles.languageFocus}><Globe2 size={36} /><span>Conversation language</span><strong>{language}</strong><p>Shared instructions. Shared booking rules.</p></div></>}
                    {slug === "after-hours-answering" && <><div className={styles.clock}><Moon size={23} /><strong>21:42</strong><span>Africa/Nairobi<br />Outside office hours</span></div><Choices label="Example after-hours policy" values={["Answer normally","Collect a message","Announce closure"]} value={policy} onChange={setPolicy} /></>}
                  </>}
                </div>
              </div>}
            </motion.div>
          </AnimatePresence>
          <div className={styles.actionBar}><p aria-live={autoplay ? "off" : "polite"}>{done ? autoplay ? "Workflow complete. Replaying in a moment." : "Workflow complete. Play to watch again." : autoplay ? "Playing automatically · Pause to explore at your own pace." : stage === 2 ? journey.hint : "Use the action to continue, or press Play."}</p><div className={styles.clickTarget}>{!done && running && <span key={stage} className={styles.autoCursor} aria-hidden="true"><MousePointer2 size={25} fill="#071c17" /><i /></span>}<Button onClick={()=>done ? restart() : setStage(stage+1)} size="lg" variant={done ? "secondary" : "primary"}>{done ? "Try again" : journey.actions[stage]}{done ? <RotateCcw /> : <ArrowRight />}</Button></div></div>
        </div>
      </div>
      <div className={styles.progress} aria-label={`Demo progress: ${stage} of 3 actions completed`}>{[0,1,2].map(index=><span key={index} className={index < stage ? styles.progressDone : ""} />)}</div>
    </div>
  </div>;
}

function ShieldNote() { return <CheckCheck size={16} aria-hidden="true" />; }
