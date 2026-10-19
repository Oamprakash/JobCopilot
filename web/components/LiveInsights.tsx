import { publicInsights } from "@/lib/publicJobs";

const MIN_JOBS = 15;

/** Live skill and city counts from open jobs, for the blog's data posts. Server component; renders a note when data is thin. */
export async function LiveInsights({ role }: { role?: string }) {
  const data = await publicInsights(role);
  if (!data || data.total < MIN_JOBS) {
    return (
      <p className="notice small">
        There aren&apos;t enough open {data?.role ? `${data.role} ` : ""}jobs on the boards we read right now to show this reliably. It fills in as more
        companies post.
      </p>
    );
  }
  const top = data.skills.slice(0, 15);
  const max = top[0]?.count || 1;
  const pct = (n: number) => Math.round((n / data.total) * 100);
  return (
    <figure className="insights card">
      <figcaption className="small muted">
        {data.role ? `${data.role} jobs` : "Tech jobs"}: share of {data.total.toLocaleString("en-IN")} open roles that mention each skill · updated{" "}
        {new Date(data.updatedAt).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit", timeZone: "Asia/Kolkata" })} IST
      </figcaption>
      <ol className="insights-bars">
        {top.map((s) => (
          <li key={s.name}>
            <span className="insights-name">{s.name}</span>
            <span className="insights-track" aria-hidden="true">
              <span style={{ width: `${Math.max(4, (s.count / max) * 100)}%` }} />
            </span>
            <span className="insights-value">{pct(s.count)}%</span>
          </li>
        ))}
      </ol>
      {data.places.length > 0 && (
        <p className="small" style={{ margin: 0 }}>
          <strong>Where they are:</strong> {data.places.slice(0, 6).map((p) => `${p.name} ${pct(p.count)}%`).join(" · ")}
        </p>
      )}
    </figure>
  );
}
