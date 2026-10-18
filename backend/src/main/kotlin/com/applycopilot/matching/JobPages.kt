package com.applycopilot.matching

/**
 * The public "Android developer jobs in Chennai" pages: a fixed set of roles and places, each page listing the open
 * jobs whose title fits the role and whose location fits the place. Pure, so the grouping is easy to test.
 */
object JobPages {
    data class Role(val slug: String, val name: String, val pattern: Regex)
    data class Place(val slug: String, val name: String, val matches: (location: String, remote: Boolean) -> Boolean)
    data class Page(val slug: String, val role: Role, val place: Place) {
        val title: String get() = if (place.slug == REMOTE) "Remote ${role.name} jobs" else "${role.name} jobs in ${place.name}"
    }

    /** A job as the pages see it. */
    data class Listing(val id: String, val title: String, val location: String, val remote: Boolean)

    const val REMOTE = "remote"
    const val INDIA = "india"
    const val MIN_JOBS = 3

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val roles: List<Role> = listOf(
        Role("android-developer", "Android Developer", r("\\bandroid\\b")),
        Role("ios-developer", "iOS Developer", r("\\bios\\b|\\bswift(ui)?\\b")),
        Role("flutter-developer", "Flutter Developer", r("\\bflutter\\b")),
        Role("react-native-developer", "React Native Developer", r("react[\\s-]?native")),
        Role("react-developer", "React Developer", r("\\breact(\\.?js)?\\b(?![\\s-]?native)")),
        Role("frontend-developer", "Frontend Developer", r("front[\\s-]?end|\\bui (engineer|developer)\\b|\\bweb developer\\b")),
        Role("backend-developer", "Backend Developer", r("back[\\s-]?end")),
        Role("full-stack-developer", "Full Stack Developer", r("full[\\s-]?stack")),
        Role("java-developer", "Java Developer", r("\\bjava\\b(?!\\s*script)")),
        Role("python-developer", "Python Developer", r("\\bpython\\b|\\bdjango\\b")),
        Role("golang-developer", "Go Developer", r("\\bgolang\\b|\\bgo (developer|engineer)\\b")),
        Role("node-js-developer", "Node.js Developer", r("\\bnode(\\.js|js)?\\b")),
        Role("devops-engineer", "DevOps Engineer", r("devops|\\bsre\\b|site reliability|platform engineer|cloud engineer")),
        Role("data-engineer", "Data Engineer", r("data engineer")),
        Role("data-scientist", "Data Scientist", r("data scien")),
        Role("machine-learning-engineer", "Machine Learning Engineer", r("machine learning|\\bml engineer|\\bai engineer|\\bmlops\\b")),
        Role("qa-engineer", "QA Engineer", r("\\bqa\\b|quality assurance|\\bsdet\\b|test engineer|automation test")),
        Role("security-engineer", "Security Engineer", r("security engineer|application security|\\bappsec\\b")),
        Role("engineering-manager", "Engineering Manager", r("engineering manager|manager,? engineering")),
        Role("product-manager", "Product Manager", r("product manager")),
        Role("product-designer", "Product Designer", r("(product|ux|ui|visual|interaction)\\s*designer")),
        Role("software-engineer", "Software Engineer", r("software (engineer|developer)|\\bsde\\b")),
    )

    private fun city(slug: String, name: String, vararg also: String) =
        Place(slug, name) { loc, _ -> (listOf(name) + also).any { Locations.cityMatches(it, loc) } }

    val places: List<Place> = listOf(
        city("bengaluru", "Bengaluru"),
        city("chennai", "Chennai"),
        city("hyderabad", "Hyderabad"),
        city("pune", "Pune"),
        city("mumbai", "Mumbai"),
        city("delhi-ncr", "Delhi NCR", "Delhi", "Gurugram", "Noida"),
        city("kolkata", "Kolkata"),
        city("kochi", "Kochi"),
        city("ahmedabad", "Ahmedabad"),
        city("coimbatore", "Coimbatore"),
        Place(REMOTE, "India (remote)") { loc, remote ->
            Locations.remoteForIndia(JobForMatching(java.util.UUID(0, 0), "", "", loc, remote, ""))
        },
        Place(INDIA, "India") { loc, remote ->
            Locations.mentionsIndia(loc) || Locations.remoteForIndia(JobForMatching(java.util.UUID(0, 0), "", "", loc, remote, ""))
        },
    )

    fun slugFor(role: Role, place: Place) = if (place.slug == REMOTE) "remote-${role.slug}-jobs" else "${role.slug}-jobs-in-${place.slug}"

    val allPages: List<Page> = roles.flatMap { role -> places.map { Page(slugFor(role, it), role, it) } }
    private val bySlug = allPages.associateBy { it.slug }

    fun page(slug: String): Page? = bySlug[slug]

    /** Which pages each job belongs on. A title can fit more than one role ("Senior Android / iOS Engineer"). */
    fun group(jobs: List<Listing>): Map<String, List<Listing>> {
        val out = LinkedHashMap<String, MutableList<Listing>>()
        for (job in jobs) {
            val jobRoles = roles.filter { it.pattern.containsMatchIn(job.title) }
            if (jobRoles.isEmpty()) continue
            val jobPlaces = places.filter { it.matches(job.location, job.remote) }
            for (role in jobRoles) for (place in jobPlaces) out.getOrPut(slugFor(role, place)) { mutableListOf() }.add(job)
        }
        return out
    }
}
