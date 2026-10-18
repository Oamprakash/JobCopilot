import type { Acquisition } from "./acquisition";
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
  /** A morning email with the day's best new matches. On unless turned off. */
  dailyDigest?: boolean;
  /** Countries the user can work remotely from (ISO codes, lowercase). Empty means India. */
  remoteCountries?: string[];
};

export type InterviewQuestionKind = "role" | "skill" | "gap" | "behavioural" | "company";

export type InterviewPrep = {
  questions: {
    question: string;
    why: string;
    /** The user's own resume bullets to draw on, with the role each came from. */
    points: { role: string; text: string }[];
    tip: string;
    kind: InterviewQuestionKind | string;
  }[];
  /** Skills the job asks for that the profile doesn't show: worth an honest answer ready. */
  gaps: string[];
  removed: string[];
};

export type InterviewPrepView = { jobId: string; generator: "claude" | "heuristic"; prep: InterviewPrep };

export type Me = { id: string; email: string; plan: "free" | "pro"; hasProfile: boolean; isAdmin?: boolean };

export type TrackerStage = "saved" | "applied" | "interview" | "offer" | "rejected" | "withdrawn";
export type TrackerItem = {
  jobId: string;
  title: string;
  company: string;
  location: string;
  applyUrl: string;
  source: string;
  closed: boolean;
  stage: TrackerStage;
  appliedAt: string | null;
  followUpAt: string | null;
  followUpDue: boolean;
  notes: string;
  updatedAt: string;
  createdAt: string;
  applicationStatus: string | null;
};
export type TrackerList = { items: TrackerItem[]; counts: Record<TrackerStage, number>; dueCount: number };
export type TrackerPatch = { stage?: TrackerStage; followUpAt?: string; clearFollowUp?: boolean; notes?: string };

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
  /** New users in the window by where they came from, and how many made a profile and tailored an application. */
  sources?: { source: string; signups: number; profiles: number; tailored: number }[];
  waitlistSources?: Counted[];
};
/** A job aggregator as admins control it. configured: anything it needs is set on the server; enabled: switched on. */
export type JobSource = "adzuna" | "himalayas";
export type SourceStatus = {
  configured: boolean;
  enabled: boolean;
  callsToday: number;
  dailyLimit: number;
  openJobs: number;
  lastSyncedAt: string | null;
  lastError: string | null;
};
/** What a free user said they'd pay for unlimited tailoring. Nothing is charged. */
export type ProChoice = "month_199" | "pass_499" | "year_999" | "no";
export type ProInterestSummary = {
  windowDays: number;
  limitReachedUsers: number;
  choices: Record<ProChoice, number>;
  notes: { email: string; choice: ProChoice; note: string; at: string }[];
};
/** The home page's no-sign-up check of one job against a resume. Nothing is stored on the server. */
export type FitCheckResult = {
  jobTitle: string;
  score: number;
  reasons: string[];
  matchedSkills: string[];
  missingSkills: string[];
  bullets: { original: string; tailored: string; reworded: boolean }[];
  rewordedBy: "claude" | "heuristic";
  profile: Profile;
  removed: string[];
};
export type WaitlistStatus = "waiting" | "invited" | "open";
export type WaitlistEntry = {
  id: string;
  email: string;
  note: string | null;
  status: "waiting" | "invited";
  createdAt: string;
  invitedAt: string | null;
  source?: string | null;
};
export type FeedbackKind = "idea" | "problem" | "praise" | "other";
export type FeedbackItem = {
  id: string;
  userId: string;
  email: string;
  kind: FeedbackKind;
  message: string;
  page: string | null;
  status: "new" | "done";
  createdAt: string;
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
  /** Where they first came from ("reddit", "instagram", "direct"...); null when the browser didn't say. */
  source?: string | null;
  campaign?: string | null;
  referrer?: string | null;
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
  /** A board ("greenhouse", ...) or an aggregator ("adzuna", "himalayas"). */
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

  fitCheck: (file: File, description: string, title?: string, jobId?: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("description", description);
    if (title) form.append("title", title);
    if (jobId) form.append("jobId", jobId);
    return request<FitCheckResult>("/try/fit-check", { method: "POST", body: form });
  },
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

  tracker: () => request<TrackerList>("/tracker"),
  trackJob: (jobId: string, stage: TrackerStage = "saved") =>
    request<TrackerItem>("/tracker", { method: "POST", body: json({ jobId, stage }) }),
  trackManual: (job: { title: string; company: string; url: string; location?: string; stage?: TrackerStage }) =>
    request<TrackerItem>("/tracker/manual", { method: "POST", body: json(job) }),
  updateTracker: (jobId: string, patch: TrackerPatch) =>
    request<TrackerItem>(`/tracker/${jobId}`, { method: "PUT", body: json(patch) }),
  untrack: (jobId: string) => request<{ ok: boolean }>(`/tracker/${jobId}`, { method: "DELETE" }),
  interviewPrep: (jobId: string) => request<InterviewPrepView>(`/tracker/${jobId}/prep`),
  prepareInterview: (jobId: string) => request<InterviewPrepView>(`/tracker/${jobId}/prep`, { method: "POST" }),

  extensionStatus: () => request<{ connected: number }>("/extension"),
  extensionPairCode: () => request<{ code: string; expiresAt: string }>("/extension/pair-code", { method: "POST" }),
  extensionDisconnect: () => request<{ ok: boolean }>("/extension", { method: "DELETE" }),

  adminOverview: (days: number) => request<AdminOverview>(`/admin/overview?days=${days}`),
  adminUsers: (q: string, offset = 0) =>
    request<{ users: AdminUser[]; total: number }>(`/admin/users?q=${encodeURIComponent(q)}&offset=${offset}&limit=50`),
  adminUser: (id: string) => request<AdminUserDetail>(`/admin/users/${id}`),
  site: () =>
    request<{ inviteOnly: boolean; googleClientId?: string | null; stats?: { openJobs: number; companies: number; newToday: number } | null }>("/site"),
  googleSignIn: (credential: string) => request<Me>("/auth/google", { method: "POST", body: json({ credential }) }),
  joinWaitlist: (email: string, note?: string, acquisition?: Acquisition) =>
    request<{ status: WaitlistStatus }>("/waitlist", { method: "POST", body: json({ email, note: note || null, acquisition: acquisition ?? null }) }),
  proInterest: () => request<{ choice: ProChoice | null; note: string | null }>("/pro-interest"),
  saveProInterest: (choice: ProChoice, note?: string) =>
    request<{ choice: ProChoice | null; note: string | null }>("/pro-interest", { method: "PUT", body: json({ choice, note: note || null }) }),
  adminProInterest: () => request<ProInterestSummary>("/admin/pro-interest"),
  reportAcquisition: (acquisition: Acquisition) => request<{ ok: boolean }>("/me/acquisition", { method: "PUT", body: json(acquisition) }),
  adminWaitlist: () => request<{ items: WaitlistEntry[]; waiting: number; invited: number }>("/admin/waitlist"),
  inviteFromWaitlist: (id: string) => request<WaitlistEntry>(`/admin/waitlist/${id}/invite`, { method: "POST" }),
  removeFromWaitlist: (id: string) => request<{ ok: boolean }>(`/admin/waitlist/${id}`, { method: "DELETE" }),
  sendFeedback: (kind: FeedbackKind, message: string, page: string) =>
    request<{ ok: boolean }>("/feedback", { method: "POST", body: json({ kind, message, page }) }),
  adminFeedback: (status?: "new") => request<{ items: FeedbackItem[]; newCount: number }>(`/admin/feedback${status ? `?status=${status}` : ""}`),
  setFeedbackStatus: (id: string, status: "new" | "done") =>
    request<{ ok: boolean }>(`/admin/feedback/${id}`, { method: "PUT", body: json({ status }) }),
  sourceStatus: (source: JobSource) => request<SourceStatus>(`/admin/sources/${source}`),
  setSource: (source: JobSource, enabled: boolean) => request<SourceStatus>(`/admin/sources/${source}`, { method: "PUT", body: json({ enabled }) }),

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
  dailyDigest: true,
};
