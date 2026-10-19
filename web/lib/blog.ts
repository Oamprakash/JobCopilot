import { promises as fs } from "fs";
import path from "path";

/** A post in web/content/blog. `draft: true` posts open by their address but aren't listed, indexed or in the sitemap. */
export type Post = { slug: string; title: string; description: string; date: string; author: string; draft: boolean; body: string };

const DIR = path.join(process.cwd(), "content", "blog");

function parse(slug: string, raw: string): Post {
  const m = raw.match(/^---\n([\s\S]*?)\n---\n?([\s\S]*)$/);
  const meta: Record<string, string> = {};
  for (const line of (m?.[1] ?? "").split("\n")) {
    const kv = line.match(/^([a-zA-Z]+):\s*(.*)$/);
    if (kv) meta[kv[1]] = kv[2].trim().replace(/^"(.*)"$/, "$1").replace(/^'(.*)'$/, "$1");
  }
  return {
    slug,
    title: meta.title ?? slug,
    description: meta.description ?? "",
    date: meta.date ?? "2026-01-01",
    author: meta.author ?? "",
    draft: meta.draft === "true",
    body: m?.[2] ?? raw,
  };
}

export async function allPosts(): Promise<Post[]> {
  let files: string[] = [];
  try {
    files = (await fs.readdir(DIR)).filter((f) => f.endsWith(".md"));
  } catch {
    return [];
  }
  const posts = await Promise.all(files.map(async (f) => parse(f.replace(/\.md$/, ""), await fs.readFile(path.join(DIR, f), "utf8"))));
  return posts.sort((a, b) => b.date.localeCompare(a.date));
}

export async function publishedPosts(): Promise<Post[]> {
  return (await allPosts()).filter((p) => !p.draft);
}

export async function getPost(slug: string): Promise<Post | null> {
  if (!/^[a-z0-9-]{1,100}$/.test(slug)) return null;
  try {
    return parse(slug, await fs.readFile(path.join(DIR, `${slug}.md`), "utf8"));
  } catch {
    return null;
  }
}

export function formatDate(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" });
}
