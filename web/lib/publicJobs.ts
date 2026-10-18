// Server-side reads of the public job pages, straight from the API inside the same container.
export type PublicPage = { slug: string; title: string; role: string; roleSlug: string; place: string; placeSlug: string; count: number };
export type PublicJob = {
  id: string;
  title: string;
  company: string;
  location: string;
  remote: boolean;
  postedAt: string | null;
  applyUrl: string;
  snippet: string;
};
export type PublicIndex = { updatedAt: string; totalJobs: number; pages: PublicPage[] };
export type PublicPageDetail = { page: PublicPage; updatedAt: string; jobs: PublicJob[]; related: PublicPage[] };

const apiBase = () => process.env.API_URL || `http://127.0.0.1:${process.env.API_PORT || "8080"}`;

async function get<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(`${apiBase()}${path}`, { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

export const publicIndex = () => get<PublicIndex>("/public/jobs");
export const publicPage = (slug: string) => get<PublicPageDetail>(`/public/jobs/${encodeURIComponent(slug)}`);

export function postedLabel(iso: string | null): string | null {
  if (!iso) return null;
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days <= 0) return "Posted today";
  if (days === 1) return "Posted yesterday";
  if (days < 30) return `Posted ${days} days ago`;
  return null;
}
