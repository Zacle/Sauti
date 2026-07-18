export type StructuredAgentSettingKey = "calendar_provider" | "routing_policy" | "after_hours_behavior";

export type StructuredAgentSetting = {
  title: string;
  description: string;
  options: ReadonlyArray<{
    value: string;
    label: string;
    description: string;
  }>;
};

export const STRUCTURED_AGENT_SETTINGS: Record<StructuredAgentSettingKey, StructuredAgentSetting> = {
  calendar_provider: {
    title: "Calendar destination",
    description: "Choose where confirmed bookings are created. Account access is managed in Integrations.",
    options: [
      {
        value: "Google Calendar",
        label: "Google Calendar",
        description: "Use the Google calendar connected to this agent.",
      },
      {
        value: "Calendly",
        label: "Calendly",
        description: "Route bookings through connected Calendly event types.",
      },
      {
        value: "Custom webhook",
        label: "Custom webhook",
        description: "Send booking actions to your scheduling API.",
      },
      {
        value: "Set up later",
        label: "Not connected yet",
        description: "Keep the agent in setup mode without a live destination.",
      },
    ],
  },
  routing_policy: {
    title: "Meeting routing",
    description: "Choose how confirmed bookings are assigned to a destination.",
    options: [
      {
        value: "Fixed calendar",
        label: "Single calendar",
        description: "Send every confirmed booking to the selected calendar.",
      },
      {
        value: "Set up later",
        label: "Decide later",
        description: "Leave routing inactive until a destination is connected.",
      },
    ],
  },
  after_hours_behavior: {
    title: "After-hours behavior",
    description: "Choose what the agent should do when the business is closed.",
    options: [
      {
        value: "answer",
        label: "Answer normally",
        description: "Keep the agent and its enabled tools available outside business hours.",
      },
      {
        value: "take_message",
        label: "Collect a message",
        description: "Capture the caller's details for business follow-up.",
      },
      {
        value: "closed",
        label: "Announce closure",
        description: "Explain that the business is closed and end the call politely.",
      },
    ],
  },
};

export function structuredAgentSetting(key: string): StructuredAgentSetting | null {
  return key in STRUCTURED_AGENT_SETTINGS
    ? STRUCTURED_AGENT_SETTINGS[key as StructuredAgentSettingKey]
    : null;
}
