import assert from "node:assert/strict";
import { readdirSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { CONSOLE_PREFIXES } from "./console-routes.ts";

const APP = join(import.meta.dirname, "..", "app");

function routeFolders(group: string) {
  return readdirSync(join(APP, group), { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && !entry.name.startsWith("(") && !entry.name.startsWith("["))
    .map((entry) => `/${entry.name}`);
}

test("every signed-in page is protected by the middleware", () => {
  for (const group of ["(console)", "(onboarding)", "(admin)"]) {
    for (const route of routeFolders(group)) {
      assert.ok(CONSOLE_PREFIXES.includes(route), `${route} (app/${group}) is missing from CONSOLE_PREFIXES, so signed-in users would be redirected away from it`);
    }
  }
});
