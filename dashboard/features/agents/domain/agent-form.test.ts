import assert from "node:assert/strict";
import test from "node:test";
import {
  agentFormReducer,
  agentFormSetters,
  formFromSettings,
  initialAgentForm,
  settingsFromForm,
  type AgentFormAction,
} from "./agent-form.ts";

test("loading saved settings and saving them again sends back the same settings", () => {
  const saved = settingsFromForm({
    ...initialAgentForm,
    name: "Reception",
    transferEnabled: true,
    transferNumber: " +254700000000 ",
    webVoiceEnabled: true,
    webVoiceAllowedOrigins: "https://a.example\nhttps://b.example",
    sttVocabularyDomain: "medical",
  });
  assert.deepEqual(settingsFromForm(formFromSettings(saved)), saved);
  assert.equal(saved.humanTransferNumber, "+254700000000");
  assert.deepEqual(saved.webVoiceAllowedOrigins, ["https://a.example", "https://b.example"]);
});

test("the form keeps what the controls show and the API gets what it stores", () => {
  const settings = settingsFromForm({
    ...initialAgentForm,
    maxDuration: "8",
    transferEnabled: false,
    transferNumber: "+254700000000",
    sttVocabularyDomain: "general",
    afterHoursMessage: "   ",
    language: "sw",
    supportedLanguages: ["en"],
  });
  assert.equal(settings.maxCallDurationSeconds, 480);
  assert.equal(settings.humanTransferNumber, null);
  assert.equal(settings.sttVocabularyDomain, null);
  assert.equal(settings.afterHoursMessage, null);
  assert.deepEqual(settings.supportedLanguages, ["sw", "en"]);
});

test("a set action changes one field, and setting the same value keeps the same form", () => {
  const renamed = agentFormReducer(initialAgentForm, { type: "set", field: "name", value: "Zawadi" });
  assert.equal(renamed.name, "Zawadi");
  assert.equal(renamed.prompt, initialAgentForm.prompt);
  assert.equal(agentFormReducer(renamed, { type: "set", field: "name", value: "Zawadi" }), renamed);
  const more = agentFormReducer(renamed, { type: "set", field: "maxReminders", value: (count) => count + 1 });
  assert.equal(more.maxReminders, initialAgentForm.maxReminders + 1);
});

test("setters look like React state setters, one per field", () => {
  const actions: AgentFormAction[] = [];
  const setters = agentFormSetters((action) => actions.push(action));
  setters.setPrompt("Answer for the clinic.");
  setters.setBookingEnabled(true);
  assert.deepEqual(actions, [
    { type: "set", field: "prompt", value: "Answer for the clinic." },
    { type: "set", field: "bookingEnabled", value: true },
  ]);
  assert.equal(Object.keys(setters).length, Object.keys(initialAgentForm).length);
});
