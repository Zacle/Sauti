const TERMINAL_PHRASES = [
  /\b(?:goodbye|good bye|bye|bye bye)\b/,
  /\b(?:that(?:'s| is) all|nothing else|no thank you|no thanks|i(?:'m| am) done|we(?:'re| are) done)\b/,
  /\b(?:have a (?:good|nice|great) day|talk to you later)\b/,
  /\b(?:au revoir|non merci|c(?:'|’)est tout|rien d(?:'|’)autre|bonne journee|a bientot)\b/,
  /\b(?:kwaheri|hapana asante|hiyo tu|sina kingine|asante,? basi)\b/,
  /(?:مع السلامة|لا شكرا|هذا كل شيء|ليس لدي شيء آخر|شكرا وداعا)/,
];

const CONTINUATION_PHRASES = [
  /\b(?:but|however|except|although|before (?:i|we) go|one more thing)\b/,
  /\b(?:mais|cependant|sauf|encore une chose)\b/,
  /\b(?:lakini|isipokuwa|kitu kimoja)\b/,
  /(?:لكن|ولكن|شيء آخر قبل)/,
];

export function callerClearlyRequestedBrowserEnd(transcript: string) {
  const normalized = normalize(transcript);
  if (!normalized || normalized.length > 180) return false;
  if (CONTINUATION_PHRASES.some((pattern) => pattern.test(normalized))) return false;
  return TERMINAL_PHRASES.some((pattern) => pattern.test(normalized));
}

function normalize(value: string) {
  return value
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[!?؛،。]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}
