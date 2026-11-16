import type { Metadata } from "next";
import { GeistSans } from "geist/font/sans";
import { GeistMono } from "geist/font/mono";
import { Instrument_Serif } from "next/font/google";
import "flag-icons/css/flag-icons.min.css";
import { Toaster } from "@/components/ui/sonner";
import { ConnectionStatus } from "@/components/ui/connection-status";
import { TooltipProvider } from "@/components/ui/primitives";
import "../styles/tailwind.css";
import "./globals.css";

const instrumentSerif = Instrument_Serif({
  subsets: ["latin"],
  weight: "400",
  style: ["normal", "italic"],
  variable: "--font-instrument-serif",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Sauti | AI voice agents for bookings",
  description: "Multilingual AI phone agents that answer calls, qualify callers, and book appointments into your calendar.",
  icons: {
    icon: [{ url: "/sauti-logo-circular.png", type: "image/png" }],
    shortcut: "/sauti-logo-circular.png",
    apple: "/sauti-logo-circular.png",
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${GeistSans.variable} ${GeistMono.variable} ${instrumentSerif.variable}`}
    >
      <head>
        <link rel="preconnect" href="https://api.fontshare.com" />
        <link rel="preconnect" href="https://cdn.fontshare.com" crossOrigin="anonymous" />
        <link
          href="https://api.fontshare.com/v2/css?f[]=satoshi@300,400,500,600,700&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <TooltipProvider delayDuration={250}>
          {children}
          <Toaster />
          <ConnectionStatus />
        </TooltipProvider>
      </body>
    </html>
  );
}
