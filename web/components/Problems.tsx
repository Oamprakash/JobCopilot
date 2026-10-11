import { ApiError } from "@/lib/api";

/** Shows an API error, with the field-level problems a 400 response carries. */
export function Problems({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
  const problems = error instanceof ApiError ? error.problems : [];
  return (
    <div className="notice notice-bad" role="alert">
      {message}
      {problems.length > 0 && (
        <ul>
          {problems.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
