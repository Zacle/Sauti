export type CompletedAgentTranscript = {
  text: string;
  interrupted: boolean;
};

export class TelnyxAgentTranscriptAccumulator {
  private itemId = "";
  private text = "";

  append(id: string, content: string): {
    caption: string;
    completed?: CompletedAgentTranscript;
  } {
    const itemId = responseItemId(id);
    const completed = this.itemId && this.itemId !== itemId
      ? this.flush(false)
      : undefined;
    if (!this.itemId) this.itemId = itemId;

    // Current Telnyx SDK items are deltas. Accept a future cumulative item too,
    // so an SDK upgrade cannot duplicate the already accumulated prefix.
    this.text = content.startsWith(this.text) ? content : this.text + content;
    return {
      caption: this.text.trim(),
      completed,
    };
  }

  flush(interrupted: boolean): CompletedAgentTranscript | undefined {
    const text = this.text.trim();
    this.itemId = "";
    this.text = "";
    return text ? { text, interrupted } : undefined;
  }
}

export function isTelnyxControlTranscript(content: string) {
  return /^\(\s*conversation ended\s*\)$/i.test(content.trim());
}

function responseItemId(id: string) {
  // ai-agent-lib appends Date.now() to every assistant delta even though the
  // provider item id is stable for the full response.
  return id.replace(/-\d{13,}$/, "");
}
