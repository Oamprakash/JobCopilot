/**
 * Countries with an employer-sponsored work route that skilled tech workers from India commonly use.
 * Route names and official links only: salary thresholds and rules change every year, so the official page
 * is the source of truth. `location` matches a whole-country place the API knows.
 */
export type VisaCountry = { location: string; route: string; note: string; officialUrl: string; adzuna: boolean };

export const visaCountries: VisaCountry[] = [
  {
    location: "Germany",
    route: "EU Blue Card",
    note: "IT specialists can qualify on 3+ years' experience without a degree. Opportunity Card for job hunting.",
    officialUrl: "https://www.make-it-in-germany.com/en/visa-residence/types/eu-blue-card",
    adzuna: true,
  },
  {
    location: "Netherlands",
    route: "Highly Skilled Migrant",
    note: "Employer must be an IND recognised sponsor; the IND publishes the list.",
    officialUrl: "https://ind.nl/en/residence-permits/work/highly-skilled-migrant",
    adzuna: true,
  },
  {
    location: "United Kingdom",
    route: "Skilled Worker visa",
    note: "Employer needs a sponsor licence; the government publishes the register.",
    officialUrl: "https://www.gov.uk/skilled-worker-visa",
    adzuna: true,
  },
  {
    location: "Ireland",
    route: "Critical Skills Employment Permit",
    note: "Salary minimums were raised in March 2026.",
    officialUrl: "https://enterprise.gov.ie/en/what-we-do/workplace-and-skills/employment-permits/permit-types/critical-skills-employment-permit/",
    adzuna: false,
  },
  {
    location: "Canada",
    route: "Global Talent Stream / Express Entry",
    note: "Employer-led fast track for tech roles, or points-based permanent residence.",
    officialUrl: "https://www.canada.ca/en/employment-social-development/services/foreign-workers/global-talent.html",
    adzuna: true,
  },
  {
    location: "Australia",
    route: "Skills in Demand visa (subclass 482)",
    note: "Income thresholds rose on 1 July 2026.",
    officialUrl: "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-listing/skills-in-demand-visa-subclass-482",
    adzuna: true,
  },
  {
    location: "New Zealand",
    route: "Accredited Employer Work Visa",
    note: "Employer must be accredited by Immigration New Zealand.",
    officialUrl: "https://www.immigration.govt.nz/new-zealand-visas/visas/visa/accredited-employer-work-visa",
    adzuna: true,
  },
  {
    location: "Singapore",
    route: "Employment Pass",
    note: "Points-based COMPASS assessment alongside a salary minimum.",
    officialUrl: "https://www.mom.gov.sg/passes-and-permits/employment-pass",
    adzuna: true,
  },
  {
    location: "UAE",
    route: "Employer-sponsored work visa",
    note: "The employer applies; no income tax. Golden Visa for higher earners.",
    officialUrl: "https://u.ae/en/information-and-services/visa-and-emirates-id",
    adzuna: false,
  },
];
