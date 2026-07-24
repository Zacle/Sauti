export type RetellTranscriptRole = "caller" | "agent";

export type RetellTranscriptTurn = {
  index: number;
  role: RetellTranscriptRole;
  text: string;
};

export class RetellTranscriptReconciler {
  private snapshot: RetellTranscriptTurn[] = [];
  private readonly delivered = new Set<number>();

  update(snapshot: RetellTranscriptTurn[]): RetellTranscriptTurn[] {
    this.snapshot = snapshot;
    return this.completeThrough(Math.max(-1, snapshot.length - 2));
  }

  completeLatest(role: RetellTranscriptRole): RetellTranscriptTurn[] {
    for (let index = this.snapshot.length - 1; index >= 0; index -= 1) {
      if (this.snapshot[index]?.role === role) return this.completeIndex(index);
    }
    return [];
  }

  completeAll(): RetellTranscriptTurn[] {
    return this.completeThrough(this.snapshot.length - 1);
  }

  activeAgentTurn(): RetellTranscriptTurn | null {
    const latest = this.snapshot.at(-1);
    return latest?.role === "agent" ? latest : null;
  }

  private completeThrough(lastIndex: number): RetellTranscriptTurn[] {
    const completed: RetellTranscriptTurn[] = [];
    for (let index = 0; index <= lastIndex; index += 1) {
      completed.push(...this.completeIndex(index));
    }
    return completed;
  }

  private completeIndex(index: number): RetellTranscriptTurn[] {
    const turn = this.snapshot[index];
    if (!turn || !turn.text || this.delivered.has(turn.index)) return [];
    this.delivered.add(turn.index);
    return [turn];
  }
}
