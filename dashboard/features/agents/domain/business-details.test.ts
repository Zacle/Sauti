import assert from "node:assert/strict";
import test from "node:test";
import {
  detailError,
  detailSpec,
  parseFaq,
  parseList,
  parseSchedule,
  serializeFaq,
  serializeList,
  serializeSchedule,
} from "./business-details.ts";

test("lists keep thousands separators inside one item", () => {
  assert.deepEqual(parseList("Cleaning – KES 3,500, Whitening – KES 12,000"), ["Cleaning – KES 3,500", "Whitening – KES 12,000"]);
  assert.equal(serializeList(["Cleaning", " Whitening "]), "Cleaning, Whitening");
});

test("schedules round-trip and accept day ranges like the server", () => {
  const schedule = parseSchedule("Mon-Fri 9-17; Sat 09:00-13:00");
  assert.equal(schedule.Wed.enabled, true);
  assert.equal(schedule.Wed.open, "09:00");
  assert.equal(schedule.Sun.enabled, false);
  assert.equal(serializeSchedule(schedule), "Mon 09:00-17:00; Tue 09:00-17:00; Wed 09:00-17:00; Thu 09:00-17:00; Fri 09:00-17:00; Sat 09:00-13:00");
});

test("overnight hours are allowed, equal times are not", () => {
  assert.equal(detailError("business_hours", "Fri 20:00-02:00"), "");
  assert.notEqual(detailError("business_hours", "Fri 09:00-09:00"), "");
});

test("faq round-trips and reads older free text", () => {
  const pairs = [{ question: "Is there parking?", answer: "Yes, behind the building." }];
  assert.deepEqual(parseFaq(serializeFaq(pairs)), pairs);
  assert.deepEqual(parseFaq("Parking: available behind the building"), [{ question: "Parking", answer: "available behind the building" }]);
});

test("keys get sensible controls", () => {
  assert.equal(detailSpec("appointment_buffer").kind, "choice");
  assert.equal(detailSpec("services_and_prices").kind, "list");
  assert.equal(detailSpec("dental_urgency_policy").kind, "longtext");
  assert.equal(detailSpec("transfer_number").kind, "phone");
  assert.equal(detailSpec("booking_link").kind, "url");
  assert.notEqual(detailError("booking_link", "example.com"), "");
});
