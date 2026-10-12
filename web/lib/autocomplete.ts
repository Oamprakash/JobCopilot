import type { AutocompleteOption } from "./api";

// Used until /autocomplete answers, or if it fails. Mirrors Locations and Autocomplete.roles in the API.
const cities: [string, ...string[]][] = [
  ["Bengaluru", "Bangalore", "Blr"], ["Mumbai", "Bombay", "Navi Mumbai", "Thane"], ["Chennai", "Madras"],
  ["Hyderabad", "Secunderabad"], ["Pune"], ["Delhi", "New Delhi", "Ncr", "Delhi Ncr"], ["Gurugram", "Gurgaon"],
  ["Noida", "Greater Noida"], ["Kolkata", "Calcutta"], ["Ahmedabad"], ["Coimbatore"], ["Kochi", "Cochin"],
  ["Thiruvananthapuram", "Trivandrum"], ["Mysuru", "Mysore"], ["Visakhapatnam", "Vizag"], ["Jaipur"], ["Indore"],
  ["Chandigarh"], ["Lucknow"], ["Nagpur"], ["Bhubaneswar"], ["Madurai"],
  ["Singapore"], ["Dubai"], ["Abu Dhabi"], ["Sharjah"], ["Kuala Lumpur"], ["Penang", "George Town", "Bayan Lepas"],
  ["Cyberjaya"], ["Petaling Jaya"], ["Johor Bahru"],
  // Whole countries: match a job anywhere in them.
  ["India"], ["Malaysia"], ["UAE", "United Arab Emirates"],
];

const roles = [
  "Android Engineer", "Senior Android Engineer", "Lead Android Engineer", "Android Developer",
  "iOS Engineer", "Senior iOS Engineer", "Mobile Engineer", "Flutter Developer", "React Native Developer",
  "Software Engineer", "Senior Software Engineer", "Staff Software Engineer", "Principal Engineer",
  "Backend Engineer", "Senior Backend Engineer", "Frontend Engineer", "Senior Frontend Engineer",
  "Full Stack Engineer", "Java Developer", "Python Developer", "Kotlin Developer", "Golang Developer",
  "Engineering Manager", "Technical Lead", "Solutions Architect",
  "Platform Engineer", "DevOps Engineer", "Site Reliability Engineer", "Cloud Engineer", "Security Engineer",
  "Data Engineer", "Data Scientist", "Data Analyst", "Machine Learning Engineer", "AI Engineer",
  "QA Engineer", "SDET", "Embedded Engineer",
  "Product Manager", "Technical Program Manager", "Product Designer", "UX Designer",
];

const withoutCounts = (values: string[]): AutocompleteOption[] => values.map((value) => ({ value, count: null }));

export const fallbackOptions = {
  locations: cities.map(([value, ...aliases]): AutocompleteOption => ({ value, count: null, aliases })),
  roles: withoutCounts(roles),
};

/**
 * The options to show for what's typed: names (or aliases) starting with it first, then names with a word
 * starting with it, then any other match; ties go to the value with more open jobs. Already chosen values are left out.
 */
export function matchOptions(options: AutocompleteOption[], query: string, chosen: string[], limit = 8): AutocompleteOption[] {
  const q = query.trim().toLowerCase();
  const taken = new Set(chosen.map((c) => c.toLowerCase()));
  const rankName = (name: string) => {
    const v = name.toLowerCase();
    if (!q || v.startsWith(q)) return 0;
    if (v.split(/[\s/-]+/).some((w) => w.startsWith(q))) return 1;
    return v.includes(q) ? 2 : -1;
  };
  // An option ranks by its best-matching name, so "Bangalore" finds Bengaluru as readily as "Beng" does.
  const rank = (o: AutocompleteOption) => {
    const ranks = [o.value, ...(o.aliases ?? [])].map(rankName).filter((r) => r >= 0);
    return ranks.length ? Math.min(...ranks) : -1;
  };
  return options
    .filter((o) => ![o.value, ...(o.aliases ?? [])].some((name) => taken.has(name.toLowerCase())))
    .map((o) => ({ o, r: rank(o) }))
    .filter(({ r }) => r >= 0)
    .sort((a, b) => a.r - b.r || (b.o.count ?? 0) - (a.o.count ?? 0))
    .slice(0, limit)
    .map(({ o }) => o);
}
