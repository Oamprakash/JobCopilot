import Link from "next/link";
import type { ReactNode } from "react";
import { brand } from "@/lib/brand";

/** Shared layout for the privacy policy and terms: readable width, title, date and a way back. */
export function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="legal">
      <Link href="/" className="small" style={{ fontWeight: 600 }}>← {brand.name}</Link>
      <h1>{title}</h1>
      <p className="muted small">Last updated {brand.policyDate}</p>
      {children}
    </main>
  );
}
