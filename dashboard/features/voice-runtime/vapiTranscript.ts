export function mergeVapiTranscript(previous: string, fragment: string): string {
  const left = previous.trim();
  const right = fragment.trim();
  if (!left) return right;
  if (!right) return left;

  const normalizedLeft = normalize(left);
  const normalizedRight = normalize(right);
  if (normalizedLeft === normalizedRight || normalizedLeft.endsWith(normalizedRight)) return left;
  if (normalizedRight.startsWith(normalizedLeft)) return right;

  const leftWords = left.split(/\s+/);
  const rightWords = right.split(/\s+/);
  const maximumOverlap = Math.min(8, leftWords.length, rightWords.length);
  let overlap = 0;
  for (let size = maximumOverlap; size > 0; size -= 1) {
    const leftTail = leftWords.slice(-size).map(normalizeWord);
    const rightHead = rightWords.slice(0, size).map(normalizeWord);
    if (leftTail.every((word, index) => word === rightHead[index])) {
      overlap = size;
      break;
    }
  }

  const remainder = rightWords.slice(overlap).join(" ");
  if (!remainder) return left;
  const separator = /^[,.;:!?،؛؟]/u.test(remainder) ? "" : " ";
  return `${left}${separator}${remainder}`;
}

export function isVapiOpeningTranscript(expected: string, observed: string): boolean {
  const expectedWords = normalizedWords(expected);
  const observedWords = normalizedWords(observed);
  if (!expectedWords.length || !observedWords.length) return false;
  if (normalize(expected) === normalize(observed)) return true;
  if (Math.min(expectedWords.length, observedWords.length) < 4) return false;

  const observedSet = new Set(observedWords);
  const shared = expectedWords.filter((word) => observedSet.has(word)).length;
  return shared >= 3 && shared / Math.min(expectedWords.length, observedWords.length) >= 0.6;
}

function normalize(value: string) {
  return value.trim().replace(/\s+/g, " ").toLocaleLowerCase();
}

function normalizeWord(value: string) {
  return value.toLocaleLowerCase().replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, "");
}

function normalizedWords(value: string) {
  return value.split(/\s+/).map(normalizeWord).filter(Boolean);
}
