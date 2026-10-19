import Link from "next/link";

/** The box at the end of each post: try the fit check, or get matches daily. */
export function BlogCta() {
  return (
    <aside className="card public-cta blog-cta">
      <div className="stack" style={{ gap: 4 }}>
        <strong>See how your resume fits a real job</strong>
        <span className="small muted">Upload your resume, paste a job, and get your fit score, missing skills and tailored lines in a minute. Free, no sign-up.</span>
      </div>
      <Link href="/#try" className="btn btn-primary">Check my fit</Link>
    </aside>
  );
}
