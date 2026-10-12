package com.applycopilot.core

/** Common tech skills, matched as whole words in resumes and job descriptions; the display form is what gets stored. */
object SkillDictionary {
    val skills = listOf(
        "Kotlin", "Java", "Swift", "Objective-C", "Dart", "Flutter", "React Native", "JavaScript", "TypeScript",
        "Python", "Golang", "Rust", "C++", "C#", "Scala", "Ruby", "PHP", "SQL", "Bash",
        "Android", "Android SDK", "Jetpack Compose", "Coroutines", "Kotlin Flow", "RxJava", "Dagger",
        "Hilt", "Koin", "Retrofit", "OkHttp", "Room", "SQLite", "Realm", "WorkManager", "Navigation Component",
        "MVVM", "MVI", "MVP", "Clean Architecture", "Kotlin Multiplatform", "KMP", "Gradle", "ProGuard", "R8",
        "Firebase", "Crashlytics", "AdMob", "Google Play", "Play Console", "Espresso", "JUnit", "Mockito",
        "MockK", "Robolectric", "iOS", "SwiftUI", "Xcode", "Android Studio",
        "React", "Next.js", "Angular", "Vue", "Node.js", "Spring", "Spring Boot", "Ktor", "Django",
        "Flask", "FastAPI", ".NET", "GraphQL", "REST", "gRPC", "Microservices",
        "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "Kafka", "RabbitMQ",
        "AWS", "GCP", "Azure", "Docker", "Kubernetes", "Terraform", "Jenkins", "GitHub Actions", "CI/CD",
        "Git", "Linux", "Agile", "Scrum", "JIRA", "Figma", "Machine Learning", "TensorFlow", "PyTorch",
        "Team leadership", "Mentoring", "Code review", "System design",
    )

    private val patterns: List<Pair<String, Regex>> = skills.map { skill ->
        // Single words ("Room", "Spring", "REST") must match case to avoid hits in ordinary prose.
        val options = if (skill.contains(' ')) setOf(RegexOption.IGNORE_CASE) else emptySet()
        skill to Regex("(?<![A-Za-z0-9+#.])${Regex.escape(skill)}(?![A-Za-z0-9+#])", options)
    }

    fun findIn(text: String): List<String> = patterns.filter { (_, regex) -> regex.containsMatchIn(text) }.map { it.first }
}
