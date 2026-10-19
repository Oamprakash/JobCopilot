/**
 * A small Markdown renderer for the blog's own posts: headings, paragraphs, lists, block quotes, bold, italics,
 * inline code and links. Everything is HTML-escaped first, so a post can't inject markup. Lines like
 * `{{insights role=android-developer}}` or `{{cta}}` become separate blocks the page renders as components.
 */
export type Block = { kind: "html"; html: string } | { kind: "insights"; role?: string } | { kind: "cta" };

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

function inline(text: string): string {
  let s = esc(text);
  s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
  s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  s = s.replace(/(^|[^*])\*([^*\s][^*]*)\*/g, "$1<em>$2</em>");
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label: string, href: string) => {
    const safe = /^(https?:\/\/|\/|#)/.test(href) ? href : "#";
    const external = /^https?:\/\//.test(safe);
    return `<a href="${safe}"${external ? ' target="_blank" rel="noopener noreferrer"' : ""}>${label}</a>`;
  });
  return s;
}

export function slugify(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60);
}

export function render(markdown: string): { blocks: Block[]; headings: { id: string; text: string }[] } {
  const blocks: Block[] = [];
  const headings: { id: string; text: string }[] = [];
  let html: string[] = [];
  const flush = () => {
    if (html.length) blocks.push({ kind: "html", html: html.join("\n") });
    html = [];
  };
  const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const directive = line.trim().match(/^\{\{\s*(insights|cta)((?:\s+[a-z]+=[a-z0-9-]+)*)\s*\}\}$/);
    if (directive) {
      flush();
      if (directive[1] === "cta") blocks.push({ kind: "cta" });
      else blocks.push({ kind: "insights", role: directive[2].match(/role=([a-z0-9-]+)/)?.[1] });
      i++;
      continue;
    }
    const heading = line.match(/^(#{2,4})\s+(.+)$/);
    if (heading) {
      const level = heading[1].length;
      const id = slugify(heading[2]);
      if (level === 2) headings.push({ id, text: heading[2] });
      html.push(`<h${level} id="${id}">${inline(heading[2])}</h${level}>`);
      i++;
      continue;
    }
    if (/^\s*[-*]\s+/.test(line) || /^\s*\d+\.\s+/.test(line)) {
      const ordered = /^\s*\d+\./.test(line);
      const items: string[] = [];
      while (i < lines.length && (ordered ? /^\s*\d+\.\s+/ : /^\s*[-*]\s+/).test(lines[i])) {
        items.push(lines[i].replace(ordered ? /^\s*\d+\.\s+/ : /^\s*[-*]\s+/, ""));
        i++;
      }
      html.push(`<${ordered ? "ol" : "ul"}>${items.map((t) => `<li>${inline(t)}</li>`).join("")}</${ordered ? "ol" : "ul"}>`);
      continue;
    }
    if (/^>\s?/.test(line)) {
      const quote: string[] = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) quote.push(lines[i++].replace(/^>\s?/, ""));
      html.push(`<blockquote><p>${inline(quote.join(" "))}</p></blockquote>`);
      continue;
    }
    if (line.trim() === "") {
      i++;
      continue;
    }
    const para: string[] = [];
    while (i < lines.length && lines[i].trim() !== "" && !/^(#{2,4}\s|>|\s*[-*]\s|\s*\d+\.\s|\{\{)/.test(lines[i])) para.push(lines[i++]);
    html.push(`<p>${inline(para.join(" "))}</p>`);
  }
  flush();
  return { blocks, headings };
}
