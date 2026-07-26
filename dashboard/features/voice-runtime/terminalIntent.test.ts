import assert from "node:assert/strict";
import test from "node:test";

import { callerClearlyRequestedBrowserEnd } from "./terminalIntent.ts";

test("recognizes clear multilingual browser-call endings", () => {
  [
    "No thank you, have a good day.",
    "That's all, thanks.",
    "Au revoir.",
    "Non merci, bonne journée.",
    "C’est tout, merci.",
    "Hapana asante, kwaheri.",
    "لا شكرا، مع السلامة",
  ].forEach((transcript) => assert.equal(callerClearlyRequestedBrowserEnd(transcript), true));
});

test("does not end while the caller continues or qualifies the request", () => {
  [
    "No thanks, but I still need to change my booking.",
    "That's all for the booking; however, I have another question.",
    "Non merci, mais j'ai encore une question.",
    "Hapana asante, lakini nina swali lingine.",
    "لا شكرا، ولكن لدي سؤال آخر.",
    "I want to book tomorrow.",
  ].forEach((transcript) => assert.equal(callerClearlyRequestedBrowserEnd(transcript), false));
});
