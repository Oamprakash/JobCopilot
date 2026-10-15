/** Countries people can choose to work remotely from. Codes match the server's list (Locations.remoteCountries). */
export const remoteCountries: { code: string; name: string }[] = [
  { code: "in", name: "India" },
  { code: "us", name: "United States" },
  { code: "ca", name: "Canada" },
  { code: "gb", name: "United Kingdom" },
  { code: "ie", name: "Ireland" },
  { code: "de", name: "Germany" },
  { code: "nl", name: "Netherlands" },
  { code: "fr", name: "France" },
  { code: "es", name: "Spain" },
  { code: "pt", name: "Portugal" },
  { code: "pl", name: "Poland" },
  { code: "ae", name: "UAE" },
  { code: "sg", name: "Singapore" },
  { code: "my", name: "Malaysia" },
  { code: "ph", name: "Philippines" },
  { code: "jp", name: "Japan" },
  { code: "au", name: "Australia" },
  { code: "nz", name: "New Zealand" },
  { code: "br", name: "Brazil" },
  { code: "mx", name: "Mexico" },
];

/** The countries in effect: the chosen ones, or India when none are chosen. */
export function remoteFrom(codes: string[] | undefined): string[] {
  return codes && codes.length > 0 ? codes : ["in"];
}

export function remoteCountryName(code: string): string {
  return remoteCountries.find((c) => c.code === code)?.name ?? code.toUpperCase();
}
