import assert from "node:assert/strict";
import { test } from "node:test";
import { EMPTY_FAQ, EMPTY_SERVICE, parseAmount, requestFromForm, type BusinessProfileForm } from "./business-profile.ts";

const base: BusinessProfileForm = {
  displayName: " Bright Smile Dental ",
  description: "",
  address: "Ngong Road",
  phone: "",
  email: "",
  website: "",
  cancellationPolicy: "",
  services: [],
  faqs: [],
};

test("reads prices with thousands separators and drops blank rows", () => {
  const result = requestFromForm({
    ...base,
    services: [
      { name: "Cleaning", price: "3,500", currency: "kes", duration: "30", description: "" },
      EMPTY_SERVICE,
      { name: "Consultation", price: "", currency: "", duration: "", description: "" },
    ],
    faqs: [EMPTY_FAQ, { question: "Parking?", answer: "Behind the building." }],
  });
  assert.ok("request" in result);
  assert.equal(result.request.displayName, "Bright Smile Dental");
  assert.equal(result.request.description, null);
  assert.deepEqual(result.request.services, [
    { name: "Cleaning", priceAmount: 3500, currency: "KES", durationMinutes: 30, description: null },
    { name: "Consultation", priceAmount: null, currency: null, durationMinutes: null, description: null },
  ]);
  assert.equal(result.request.faqs.length, 1);
});

test("uses the default currency when a price has none", () => {
  const result = requestFromForm({ ...base, services: [{ ...EMPTY_SERVICE, name: "Cleaning", price: "10" }] }, "EUR");
  assert.ok("request" in result);
  assert.equal(result.request.services[0].currency, "EUR");
});

test("names the row that needs fixing", () => {
  assert.deepEqual(
    requestFromForm({ ...base, services: [{ ...EMPTY_SERVICE, name: "Cleaning", price: "ten" }] }),
    { error: "Enter the price for Cleaning as a number, such as 3500." },
  );
  assert.deepEqual(
    requestFromForm({ ...base, services: [{ ...EMPTY_SERVICE, name: "Cleaning", duration: "2" }] }),
    { error: "The duration for Cleaning must be between 5 and 480 minutes." },
  );
  assert.deepEqual(
    requestFromForm({ ...base, faqs: [{ question: "Parking?", answer: "" }] }),
    { error: "Each FAQ needs both a question and an answer." },
  );
});

test("parses amounts strictly", () => {
  assert.equal(parseAmount(""), null);
  assert.equal(parseAmount("12,000.50"), 12000.5);
  assert.equal(parseAmount("-5"), undefined);
  assert.equal(parseAmount("1.234"), undefined);
});
