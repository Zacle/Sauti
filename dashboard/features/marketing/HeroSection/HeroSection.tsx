"use client";

import { useEffect, useState } from "react";
import {
  ArrowRight,
  CalendarCheck,
  Languages,
  Mic2,
  Pause,
  PhoneCall,
  Play,
  Radio,
  RotateCcw,
  Sparkles,
  Volume2,
  VolumeX,
} from "lucide-react";
export function HeroSection() {
  return (
    <section className="hero-section" id="product">
      <div className="hero-noise" aria-hidden="true" />
      <div className="hero-grid">
        <div className="hero-copy" data-reveal>
          <div className="eyebrow">
            <Sparkles size={14} />
            AI voice agents that work
          </div>
          <h1>Every missed call becomes a <span>booked appointment</span></h1>
          <p>
            Sauti answers phone calls, speaks the caller&apos;s language, handles interruptions, checks calendars, and books
            the right slot while you monitor the operation live.
          </p>
          <div className="hero-actions">
            <a className="solid-button large" href="#dashboard">
              <Play size={16} /> Explore the platform
            </a>
            <a className="demo-button large" href="#dashboard">
              <Play size={16} /> Hear a live call
            </a>
          </div>
        </div>
        <div data-reveal>
          <LiveCallCard />
        </div>
      </div>
    </section>
  );
}

const CONVERSATION_SCRIPT = [
  {
    state: "thinking" as const,
    speaker: "system" as const,
    text: "Connecting to carrier network...",
    duration: 1800,
  },
  {
    state: "ai-speaking" as const,
    speaker: "ai" as const,
    text: "Hello, thank you for calling Sauti. How can I help you book your appointment today?",
    duration: 4000,
  },
  {
    state: "thinking" as const,
    speaker: "system" as const,
    text: "Agent listening...",
    duration: 1200,
  },
  {
    state: "user-speaking" as const,
    speaker: "user" as const,
    text: "Hi, I'd like to reschedule my dentist cleaning to next Tuesday morning, please.",
    duration: 3800,
  },
  {
    state: "thinking" as const,
    speaker: "system" as const,
    text: "Checking calendar availability for Tuesday, June 24th...",
    duration: 2000,
  },
  {
    state: "ai-speaking" as const,
    speaker: "ai" as const,
    text: "I see an opening for next Tuesday, June 24th at 10:30 AM or 1:00 PM. Do either of those work?",
    duration: 5000,
  },
  {
    state: "thinking" as const,
    speaker: "system" as const,
    text: "Agent listening...",
    duration: 1200,
  },
  {
    state: "user-speaking" as const,
    speaker: "user" as const,
    text: "Yes, 10:30 AM works great.",
    duration: 2500,
  },
  {
    state: "thinking" as const,
    speaker: "system" as const,
    text: "Booking appointment in calendar...",
    duration: 2000,
    bookingDetail: {
      title: "Booking Found",
      subtitle: "Dental Cleaning — Jun 24, 10:30 AM",
    },
  },
  {
    state: "ai-speaking" as const,
    speaker: "ai" as const,
    text: "Perfect! You're booked for next Tuesday, June 24th at 10:30 AM. Sending your confirmation text now.",
    duration: 5000,
    bookingDetail: {
      title: "Booking Confirmed",
      subtitle: "Dental Cleaning — Jun 24, 10:30 AM",
    },
  },
  {
    state: "idle" as const,
    speaker: "system" as const,
    text: "Call ended. Booking confirmed.",
    duration: 4000,
    bookingDetail: {
      title: "Booking Confirmed",
      subtitle: "Dental Cleaning — Jun 24, 10:30 AM",
    },
  },
];

function LiveCallCard() {
  const [stepIndex, setStepIndex] = useState(0);
  const [isAutoPlay, setIsAutoPlay] = useState(true);
  const [isMuted, setIsMuted] = useState(false);
  const [history, setHistory] = useState<{ speaker: "ai" | "user" | "system"; text: string }[]>([]);
  const [manualState, setManualState] = useState<"idle" | "ai-speaking" | "user-speaking" | "thinking" | null>(null);

  // Auto-play simulation loop
  useEffect(() => {
    if (!isAutoPlay || manualState !== null) return;

    const currentStep = CONVERSATION_SCRIPT[stepIndex];
    const timer = setTimeout(() => {
      setStepIndex((prev) => (prev + 1) % CONVERSATION_SCRIPT.length);
    }, currentStep.duration);

    return () => clearTimeout(timer);
  }, [stepIndex, isAutoPlay, manualState]);

  // Update transcript history
  useEffect(() => {
    if (manualState !== null) return;
    const currentStep = CONVERSATION_SCRIPT[stepIndex];

    if (stepIndex === 0) {
      setHistory([{ speaker: currentStep.speaker, text: currentStep.text }]);
    } else {
      setHistory((prev) => {
        // Prevent duplicate append
        if (prev.length > 0 && prev[prev.length - 1].text === currentStep.text) return prev;
        const updated = [...prev, { speaker: currentStep.speaker, text: currentStep.text }];
        // Keep last 3 items to fit card height
        return updated.slice(-3);
      });
    }
  }, [stepIndex, manualState]);

  const activeStep = manualState !== null 
    ? {
        state: manualState,
        speaker: manualState === "ai-speaking" ? ("ai" as const) : manualState === "user-speaking" ? ("user" as const) : ("system" as const),
        text: manualState === "ai-speaking" ? "Simulating AI voice stream..." : manualState === "user-speaking" ? "Simulating Caller voice stream..." : manualState === "thinking" ? "Simulating thinking state..." : "Agent is muted/idle.",
        bookingDetail: manualState === "idle" ? { title: "Booking Confirmed", subtitle: "Dental Cleaning — Jun 24, 10:30 AM" } : undefined
      }
    : CONVERSATION_SCRIPT[stepIndex];

  // Colors based on state
  const glowColors = {
    "ai-speaking": "rgba(55, 230, 218, 0.4)",
    "user-speaking": "rgba(143, 119, 255, 0.4)",
    thinking: "rgba(106, 245, 207, 0.4)",
    idle: "rgba(127, 145, 163, 0.15)",
  };

  const activeGlow = isMuted ? glowColors["idle"] : glowColors[activeStep.state];

  // Helper to generate unique wave variables
  const getBarStyles = (index: number) => {
    const distFromCenter = Math.abs(index - 31.5);
    
    // AI: Bell curve scaling (tapered edges)
    const aiScale = Math.max(0.05, 1 - (distFromCenter / 32) * 0.85);
    
    // User: Multiple peaks (sinusoidal) for human speech
    const userScale = Math.max(0.08, (Math.sin(index * 0.45) * 0.45 + 0.55) * (1 - (distFromCenter / 32) * 0.4));
    
    // Thinking: Gentle rolling wave (smooth sine)
    const thinkScale = 0.25 + Math.sin(index * 0.22) * 0.12;
    
    return {
      "--dist": distFromCenter,
      "--ai-scale": aiScale,
      "--user-scale": userScale,
      "--think-scale": thinkScale,
      "--i": index,
    } as React.CSSProperties;
  };

  const handleManualState = (state: "ai-speaking" | "user-speaking" | "thinking" | "idle") => {
    setIsAutoPlay(false);
    setManualState(state);
  };

  const handleReset = () => {
    setManualState(null);
    setIsAutoPlay(true);
    setStepIndex(0);
    setHistory([]);
  };

  return (
    <div 
      className={`live-call-card state-${isMuted ? "idle" : activeStep.state}`} 
      aria-label="Live call preview"
      style={{ "--card-glow-color": activeGlow } as React.CSSProperties}
    >
      <div className="call-card-header">
        <div className="call-card-title">Live Demonstration</div>
        <div className="live-state">
          {isMuted ? (
            <span className="muted-badge">MUTED</span>
          ) : isAutoPlay ? (
            <>
              <span className="live-dot" />
              <span className="live-text">SIMULATOR</span>
            </>
          ) : (
            <span className="manual-badge">MANUAL</span>
          )}
        </div>
      </div>

      {/* Waveform Visualization Container */}
      <div className="waveform-container">
        <div className="waveform-glow-orb" />
        <div className="hero-waveform primary" aria-hidden="true">
          {Array.from({ length: 64 }).map((_, index) => (
            <i key={`p-${index}`} style={{ ...getBarStyles(index), animationDelay: `${index * 12}ms` }} />
          ))}
        </div>
        <div className="hero-waveform secondary" aria-hidden="true">
          {Array.from({ length: 64 }).map((_, index) => (
            <i key={`s-${index}`} style={{ ...getBarStyles(index), animationDelay: `${(63 - index) * 12 - 300}ms` }} />
          ))}
        </div>
      </div>

      {/* Transcript Log Container */}
      <div className="transcript-box">
        {manualState !== null ? (
          <div className="transcript-row manual-msg">
            <div className={`speaker-badge ${activeStep.speaker}`}>
              {activeStep.speaker === "ai" ? "AI Agent" : activeStep.speaker === "user" ? "Caller" : "System"}
            </div>
            <p>{activeStep.text}</p>
          </div>
        ) : (
          history.map((hist, idx) => (
            <div key={idx} className={`transcript-row ${hist.speaker} ${idx === history.length - 1 ? "active-row" : "past-row"}`}>
              <div className={`speaker-badge ${hist.speaker}`}>
                {hist.speaker === "ai" ? "AI Agent" : hist.speaker === "user" ? "Caller" : "System"}
              </div>
              <p>{hist.text}</p>
            </div>
          ))
        )}
      </div>

      {/* Dynamic Booking Confirmation Overlay */}
      {activeStep.bookingDetail && (
        <div className="booking-confirm slide-up">
          <CalendarCheck size={18} className="booking-icon" />
          <div>
            <strong>{activeStep.bookingDetail.title}</strong>
            <span>{activeStep.bookingDetail.subtitle}</span>
          </div>
          <span className="success-badge">Verified</span>
        </div>
      )}

      {/* Interactive Controls */}
      <div className="call-controls">
        <button 
          className={`control-btn ${isMuted ? "active" : ""}`} 
          onClick={() => {
            setIsMuted(!isMuted);
            if (!isMuted) {
              setIsAutoPlay(false);
              setManualState("idle");
            } else {
              handleReset();
            }
          }}
          title={isMuted ? "Unmute and resume" : "Mute audio"}
        >
          {isMuted ? <VolumeX size={17} /> : <Volume2 size={17} />}
        </button>

        <button 
          className="control-btn end-call" 
          onClick={() => {
            setIsAutoPlay(false);
            setManualState("idle");
            setHistory([]);
          }}
          title="End Call"
        >
          <PhoneCall size={20} />
        </button>

        <button 
          className="control-btn" 
          onClick={handleReset}
          title="Restart Simulation"
        >
          <RotateCcw size={17} />
        </button>
      </div>

      {/* Manual State Selectors */}
      <div className="state-selectors">
        <button 
          className={`selector-pill ai ${activeStep.state === "ai-speaking" && !isMuted ? "active" : ""}`}
          onClick={() => handleManualState("ai-speaking")}
        >
          AI Speaking
        </button>
        <button 
          className={`selector-pill user ${activeStep.state === "user-speaking" && !isMuted ? "active" : ""}`}
          onClick={() => handleManualState("user-speaking")}
        >
          Caller Speaking
        </button>
        <button 
          className={`selector-pill thinking ${activeStep.state === "thinking" && !isMuted ? "active" : ""}`}
          onClick={() => handleManualState("thinking")}
        >
          Thinking
        </button>
        <button 
          className={`selector-pill idle ${activeStep.state === "idle" || isMuted ? "active" : ""}`}
          onClick={() => handleManualState("idle")}
        >
          Idle
        </button>
      </div>
    </div>
  );
}

