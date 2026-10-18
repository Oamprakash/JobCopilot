// Google's mirror of Maven Central comes first: Render's build machines share addresses that Maven Central
// sometimes rate-limits (HTTP 429), which fails the deploy. Maven Central and the Gradle Plugin Portal stay as
// fallbacks for anything the mirror doesn't have.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "apply-copilot-backend"
