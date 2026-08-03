import type { Metadata } from "next";
import { redirect } from "next/navigation";

export const metadata: Metadata = {
  title: "Request a demo | Sauti",
};

export default function RegisterPage() {
  redirect("/request-demo?registration=closed");
}
