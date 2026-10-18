import Link from "next/link";
import { Logo } from "@/components/AppHeader";
import { brand } from "@/lib/brand";

/** The header on public pages (job pages): brand, browse, sign in. Works in server components. */
export function PublicHeader() {
  return (
    <header className="landing-bar">
      <Link href="/" className="brand"><Logo /> {brand.name}</Link>
      <nav className="row" style={{ gap: 4 }}>
        <Link href="/jobs" className="btn btn-ghost">Jobs</Link>
        <Link href="/login" className="btn btn-ghost">Sign in</Link>
        <Link href="/#try" className="btn btn-primary landing-bar-cta">Check my fit</Link>
      </nav>
    </header>
  );
}
