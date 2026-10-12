#!/usr/bin/env python3
"""Probe candidate company slugs on the Greenhouse, Lever and Ashby public board APIs.

For each slug that answers, count open jobs, jobs in India, and India jobs with a mobile
title (Android, iOS, Flutter, React Native, mobile). Writes probe-results.json and a
readable probe-results.md. Stdlib only, so it runs on a bare CI runner.

Usage: python3 tools/probe_boards.py tools/board_candidates.txt
"""
import json
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

INDIA = re.compile(
    r"\b(india|bengaluru|bangalore|mumbai|chennai|hyderabad|pune|delhi|gurugram|gurgaon|noida|kolkata|"
    r"ahmedabad|coimbatore|kochi|trivandrum|thiruvananthapuram|jaipur|indore|chandigarh|ncr)\b",
    re.I,
)
MOBILE = re.compile(r"\b(android|ios|flutter|react native|mobile|kotlin|swift)\b", re.I)
ENGINEERING = re.compile(r"\b(engineer|developer|sde|architect|programmer)\b", re.I)


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "JobCopilot board probe (public APIs)"})
    with urllib.request.urlopen(req, timeout=25) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def jobs_for(ats, slug):
    """Returns [(title, location)] or None when the board doesn't exist."""
    try:
        if ats == "greenhouse":
            data = get(f"https://boards-api.greenhouse.io/v1/boards/{slug}/jobs")
            return [(j.get("title", ""), (j.get("location") or {}).get("name", "")) for j in data.get("jobs", [])]
        if ats == "lever":
            data = get(f"https://api.lever.co/v0/postings/{slug}?mode=json")
            if not isinstance(data, list):
                return None
            out = []
            for p in data:
                cats = p.get("categories") or {}
                locs = cats.get("allLocations") or [cats.get("location") or ""]
                country = "India" if (p.get("country") or "").upper() == "IN" else ""
                out.append((p.get("text", ""), ", ".join([l for l in locs if l] + ([country] if country else []))))
            return out
        if ats == "ashby":
            data = get(f"https://api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=false")
            out = []
            for j in data.get("jobs", []):
                if j.get("isListed") is False:
                    continue
                locs = [j.get("location") or ""] + [s.get("location", "") for s in j.get("secondaryLocations") or []]
                country = ((j.get("address") or {}).get("postalAddress") or {}).get("addressCountry") or ""
                out.append((j.get("title", ""), ", ".join([l for l in locs + [country] if l])))
            return out
    except Exception:
        return None
    return None


def probe(ats_slug):
    ats, slug = ats_slug
    jobs = jobs_for(ats, slug)
    if jobs is None:
        return None
    india = [(t, l) for t, l in jobs if INDIA.search(l) or ("remote" in l.lower() and INDIA.search(l))]
    india_eng = [(t, l) for t, l in india if ENGINEERING.search(t)]
    india_mobile = [(t, l) for t, l in india if MOBILE.search(t)]
    return {
        "ats": ats,
        "slug": slug,
        "total": len(jobs),
        "india": len(india),
        "indiaEngineering": len(india_eng),
        "indiaMobile": len(india_mobile),
        "mobileTitles": [f"{t} | {l}" for t, l in india_mobile][:15],
    }


def main():
    slugs = []
    for line in open(sys.argv[1]):
        s = line.split("#", 1)[0].strip().lower()
        if s and re.fullmatch(r"[a-z0-9_-]{1,100}", s) and s not in slugs:
            slugs.append(s)
    work = [(ats, s) for s in slugs for ats in ("greenhouse", "lever", "ashby")]
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = [r for r in pool.map(probe, work) if r]
    results.sort(key=lambda r: (-r["indiaMobile"], -r["indiaEngineering"], r["slug"]))

    with open("probe-results.json", "w") as f:
        json.dump({"probed": len(slugs), "found": len(results), "boards": results}, f, indent=1)
    with open("probe-results.md", "w") as f:
        f.write(f"Probed {len(slugs)} slugs x 3 ATSs; {len(results)} boards answered.\n\n")
        f.write("| ATS | slug | total | India | India eng | India mobile |\n|---|---|---|---|---|---|\n")
        for r in results:
            f.write(f"| {r['ats']} | {r['slug']} | {r['total']} | {r['india']} | {r['indiaEngineering']} | {r['indiaMobile']} |\n")
        f.write("\n## Mobile titles in India\n")
        for r in results:
            for t in r["mobileTitles"]:
                f.write(f"- {r['ats']}/{r['slug']}: {t}\n")
    print(f"{len(results)} boards answered; {sum(1 for r in results if r['indiaMobile'])} with India mobile roles")


if __name__ == "__main__":
    main()
