import { confirmedEndCallResult } from "./realtimeProtocol.ts";

type ExecuteTool = (
  toolCallId: string,
  name: string,
  argumentsJson: string,
) => Promise<Record<string, unknown>>;

type Schedule = (callback: () => void) => void;

export async function executeTelnyxClientTool(
  toolCallId: string,
  name: string,
  parameters: unknown,
  executeTool: ExecuteTool,
  onConfirmedEnd: () => void,
  schedule: Schedule = (callback) => {
    globalThis.setTimeout(callback, 0);
  },
) {
  const result = await executeTool(
    toolCallId,
    name,
    JSON.stringify(parameters ?? {}),
  );
  if (confirmedEndCallResult(name, result)) {
    // Return the tool result to Telnyx before its provider session is stopped.
    schedule(onConfirmedEnd);
  }
  return result;
}
