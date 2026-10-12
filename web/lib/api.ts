// Types mirror the Kotlin DTOs in backend/src/main/kotlin/com/applycopilot/{core,api}.

export type Experience = {
  title: string;
  company: string;
  location?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  current: boolean;
  bullets: string[];
};

export type Education = {
  institution: string;
  degree?: string | null;
  field?: string | null;
  endYear?: number | null;
};

export type Profile = {
  fullName: string;
  headline: string;
  email?: string | null;
  phone?: string | null;
  location?: string | null;
  totalExperienceYears?: number | null;
  summary?: string | null;
  experience: Experience[];
  education: Education[];
  skills: string[];
  links: string[];
};

export type RemoteMode = "onsite" | "hybrid" | "remote" | "any";

export type Preferences = {
  roles: string[];
  locations: string[];
  remoteMode: RemoteMode;
  minCtcLpa?: number | null;
  experienceYears?: number | null;
  noticePeriodDays?: number | null;
  skipCompanies: string[];
};

export type Me = { id: string; email: string; plan: "free" | "pro"; hasProfile: boolean };
export type ResumeFile = { id: string; fileName: string };
export type ResumeUpload = { resumeFile: ResumeFile; parser: "claude" | "heuristic"; profile: Profile; removed: string[] };
export type StoredProfile = { version: number; profile: Profile; resumeFile: ResumeFile | null; updatedAt: string };

export type MatchStatus = "new" | "saved" | "skipped";

export type FeedJob = {
  jobId: string;
  title: string;
  company: string;
  location: string;
  remote: boolean;
  applyUrl: string;
  postedAt: string | null;
  score: number;
  reasons: string[];
  missingSkills: string[];
  scorer: "claude" | "heuristic";
  status: MatchStatus;
  closed: boolean;
};

export type MatchRun = {
  startedAt: string;
  finishedAt: string | null;
  considered: number;
  passedFilter: number;
  scored: number;
  llmScored: number;
  error: string | null;
  /** Jobs the hard filter dropped, by reason. */
  filtered: Partial<Record<"role" | "location" | "experience" | "seniority" | "skippedCompany" | "other", number>>;
};

export type Feed = { jobs: FeedJob[]; lastRun: MatchRun | null; needsRefresh: boolean };

export type JobDetail = {
  jobId: string;
  title: string;
  company: string;
  location: string;
  remote: boolean;
  applyUrl: string;
  postedAt: string | null;
  description: string;
  closed: boolean;
};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public problems: string[] = [],
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isForm = init.body instanceof FormData;
  const res = await fetch(`/api${path}`, {
    ...init,
    credentials: "same-origin",
    headers: isForm ? init.headers : { "Content-Type": "application/json", ...init.headers },
  });
  if (!res.ok) {
    let message = "Something went wrong. Please try again.";
    let problems: string[] = [];
    try {
      const body = await res.json();
      message = body.error ?? message;
      problems = body.problems ?? [];
    } catch {
      // Non-JSON error (proxy down, HTML error page): keep the generic message.
    }
    throw new ApiError(res.status, message, problems);
  }
  return (await res.json()) as T;
}

const json = (body: unknown) => JSON.stringify(body);

export const api = {
  requestLink: (email: string) =>
    request<{ ok: boolean; devLink?: string | null }>("/auth/magic-link", { method: "POST", body: json({ email }) }),
  verify: (token: string) => request<Me>("/auth/verify", { method: "POST", body: json({ token }) }),
  logout: () => request<{ ok: boolean }>("/auth/logout", { method: "POST" }),
  me: () => request<Me>("/me"),
  deleteAccount: () => request<{ ok: boolean }>("/me", { method: "DELETE" }),

  uploadResume: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<ResumeUpload>("/resume", { method: "POST", body: form });
  },

  getProfile: () => request<StoredProfile>("/profile"),
  saveProfile: (profile: Profile, resumeFileId?: string) =>
    request<StoredProfile>("/profile", { method: "PUT", body: json({ profile, resumeFileId }) }),

  feed: (status: MatchStatus) => request<Feed>(`/feed?status=${status}`),
  refreshFeed: () => request<Feed>("/feed/refresh", { method: "POST" }),
  setMatchStatus: (jobId: string, status: MatchStatus) =>
    request<{ ok: boolean }>(`/feed/${jobId}/status`, { method: "PUT", body: json({ status }) }),
  job: (jobId: string) => request<JobDetail>(`/jobs/${jobId}`),

  getPreferences: () => request<Preferences>("/preferences"),
  savePreferences: (preferences: Preferences) =>
    request<Preferences>("/preferences", { method: "PUT", body: json(preferences) }),
};

export const emptyPreferences: Preferences = {
  roles: [],
  locations: [],
  remoteMode: "any",
  minCtcLpa: null,
  experienceYears: null,
  noticePeriodDays: null,
  skipCompanies: [],
};
