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
  /** For jobs abroad: hide the ones whose description rules out visa sponsorship. */
  needsVisaSponsorship?: boolean;
};

export type Me = { id: string; email: string; plan: "free" | "pro"; hasProfile: boolean; isAdmin?: boolean };

export type Counted = { value: string; count: number };
export type AdminOverview = {
  windowDays: number;
  totalUsers: number;
  newUsers: number;
  activeUsers7: number;
  activeUsers30: number;
  days: { day: string; activeUsers: number; newUsers: number; events: number }[];
  eventCounts: Record<string, number>;
  funnel: Record<string, number>;
  topRoles: Counted[];
  topLocations: Counted[];
  topAppliedCompanies: Counted[];
};
export type AdminUser = {
  id: string;
  email: string;
  plan: string;
  createdAt: string;
  lastSeenAt: string | null;
  name: string | null;
  headline: string | null;
  roles: string[];
  locations: string[];
  refreshes: number;
  tailored: number;
  approved: number;
  applyClicks: number;
};
export type AdminEvent = { type: string; at: string; jobId: string | null; jobTitle: string | null; company: string | null; data: Record<string, unknown> };
export type AdminUserDetail = {
  user: AdminUser;
  preferences: Preferences | null;
  applications: { jobId: string; title: string; company: string; status: string; updatedAt: string; applyUrl: string }[];
  timeline: AdminEvent[];
};
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
  /** A board ("greenhouse", ...) or "adzuna". */
  source?: string;
  /** Only a summary of the description is available. */
  descriptionPartial?: boolean;
  /** What the description says about visa sponsorship (absent when it doesn't say). */
  visa?: "offered" | "not_offered" | null;
  relocation?: boolean;
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
  filtered?: Partial<Record<"role" | "location" | "experience" | "seniority" | "skippedCompany" | "other", number>>;
  /** Single preference changes that would bring in more relevant jobs, best first. */
  suggestions?: Suggestion[];
};

export type Suggestion = { kind: "location" | "role" | "remote"; value: string; count: number };

/** hiddenWeak: new matches scoring below minScore that were left out because weak matches weren't asked for. */
export type Feed = {
  jobs: FeedJob[];
  lastRun: MatchRun | null;
  needsRefresh: boolean;
  minScore?: number;
  hiddenWeak?: number;
  /** Jobs on each tab; New follows the weak-match setting. */
  counts?: Partial<Record<MatchStatus, number>>;
};

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
  source?: string;
  descriptionPartial?: boolean;
};

export type BulletChange = "unchanged" | "moved_up" | "reworded";
export type TailoredBullet = { text: string; sourceIndex: number; change: BulletChange; original?: string | null };
export type TailoredExperience = {
  sourceIndex: number;
  title: string;
  company: string;
  startDate?: string | null;
  endDate?: string | null;
  current: boolean;
  bullets: TailoredBullet[];
};
export type TailoredResume = {
  fullName: string;
  headline: string;
  email?: string | null;
  phone?: string | null;
  location?: string | null;
  summary?: string | null;
  skills: string[];
  experience: TailoredExperience[];
  education: Education[];
  links: string[];
};
export type ScreeningAnswer = { question: string; answer: string; needsUser: boolean; note?: string | null };
export type TailoredApplication = { resume: TailoredResume; coverLetter: string; answers: ScreeningAnswer[] };
export type Usage = { plan: "free" | "pro"; tailoredThisMonth: number; limit: number | null };
export type ApplicationStatus = "draft" | "approved" | "applied" | "interview" | "offer" | "rejected";
export type ApplicationView = {
  job: {
    jobId: string;
    title: string;
    company: string;
    location: string;
    applyUrl: string;
    closed: boolean;
    source?: string;
    descriptionPartial?: boolean;
  };
  status: ApplicationStatus;
  generator: "claude" | "heuristic";
  application: TailoredApplication;
  removed: string[];
  warnings: string[];
  keywordsCovered: string[];
  keywordsMissing: string[];
  score: number | null;
  usage: Usage;
  /** The full job description the user pasted, when the source gave only a summary. */
  pastedDescription?: string | null;
  updatedAt: string;
  approvedAt: string | null;
};

/** A value to suggest while typing; count is how many open jobs it matches (null when unknown). Aliases also find it. */
export type AutocompleteOption = { value: string; count: number | null; aliases?: string[] };
export type AutocompleteOptions = { locations: AutocompleteOption[]; roles: AutocompleteOption[] };

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

  feed: (status: MatchStatus, weak = false) => request<Feed>(`/feed?status=${status}${weak ? "&weak=true" : ""}`),
  refreshFeed: (weak = false) => request<Feed>(`/feed/refresh${weak ? "?weak=true" : ""}`, { method: "POST" }),
  setMatchStatus: (jobId: string, status: MatchStatus) =>
    request<{ ok: boolean }>(`/feed/${jobId}/status`, { method: "PUT", body: json({ status }) }),
  job: (jobId: string) => request<JobDetail>(`/jobs/${jobId}`),

  getApplication: (jobId: string) => request<ApplicationView>(`/applications/${jobId}`),
  /** jobDescription: undefined keeps the current one, "" goes back to the job's own, text replaces it. */
  tailor: (jobId: string, questions?: string[], jobDescription?: string) =>
    request<ApplicationView>(`/applications/${jobId}/tailor`, {
      method: "POST",
      body: json({ questions: questions ?? null, jobDescription: jobDescription ?? null }),
    }),
  saveApplication: (jobId: string, application: TailoredApplication) =>
    request<ApplicationView>(`/applications/${jobId}`, { method: "PUT", body: json({ application }) }),
  approveApplication: (jobId: string) => request<ApplicationView>(`/applications/${jobId}/approve`, { method: "POST" }),
  resumePdfUrl: (jobId: string) => `/api/applications/${jobId}/resume.pdf`,
  usage: () => request<Usage>("/usage"),

  autocomplete: () => request<AutocompleteOptions>("/autocomplete"),

  /** Reports a click the server can't see (a link to another site). Fire and forget; survives the page unloading. */
  track: (type: "job_opened" | "apply_clicked", jobId: string) => {
    fetch("/api/events", {
      method: "POST",
      credentials: "same-origin",
      keepalive: true,
      headers: { "Content-Type": "application/json" },
      body: json({ type, jobId }),
    }).catch(() => undefined);
  },

  adminOverview: (days: number) => request<AdminOverview>(`/admin/overview?days=${days}`),
  adminUsers: (q: string, offset = 0) =>
    request<{ users: AdminUser[]; total: number }>(`/admin/users?q=${encodeURIComponent(q)}&offset=${offset}&limit=50`),
  adminUser: (id: string) => request<AdminUserDetail>(`/admin/users/${id}`),

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
  needsVisaSponsorship: false,
};
