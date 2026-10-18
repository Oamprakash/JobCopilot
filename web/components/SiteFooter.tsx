import Link from "next/link";
import { brand } from "@/lib/brand";

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <span>© {new Date().getFullYear()} {brand.name}</span>
      <Link href="/jobs">Jobs</Link>
      <Link href="/privacy">Privacy</Link>
      <Link href="/terms">Terms</Link>
      <a href={`mailto:${brand.contactEmail}`}>Contact</a>
      <a href={brand.instagram} target="_blank" rel="noopener noreferrer">Instagram</a>
    </footer>
  );
}
