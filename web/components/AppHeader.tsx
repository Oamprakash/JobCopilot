"use client";

import { brand } from "@/lib/brand";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { api } from "@/lib/api";

export function Logo() {
  return (
    <svg width="28" height="28" viewBox="0 0 28 28" aria-hidden="true">
      <rect width="28" height="28" rx="8" fill="#1F4FE0" />
      <path d="M8 15l4 4 8-10" stroke="#FFFFFF" strokeWidth="2.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function AppHeader({ email, isAdmin }: { email?: string; isAdmin?: boolean }) {
  const pathname = usePathname();
  const router = useRouter();

  async function signOut() {
    await api.logout().catch(() => undefined);
    router.replace("/login");
  }

  return (
    <header className="header">
      <div className="header-inner">
        <Link href="/" className="brand">
          <Logo />
          {brand.name}
        </Link>
        <nav className="header-nav" aria-label="Main">
          <Link href="/feed" className="nav-link" aria-current={pathname === "/feed" ? "page" : undefined}>
            Today&apos;s matches
          </Link>
          <Link href="/tracker" className="nav-link" aria-current={pathname === "/tracker" ? "page" : undefined}>
            Tracker
          </Link>
          <Link href="/profile" className="nav-link" aria-current={pathname === "/profile" ? "page" : undefined}>
            Profile
          </Link>
          {isAdmin && (
            <Link href="/admin" className="nav-link" aria-current={pathname?.startsWith("/admin") ? "page" : undefined}>
              Admin
            </Link>
          )}
        </nav>
        {email && <span className="small muted">{email}</span>}
        <button type="button" className="btn btn-ghost" onClick={signOut}>
          Sign out
        </button>
      </div>
    </header>
  );
}
