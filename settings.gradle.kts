pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Gullak"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:ai")
include(":core:speech")
include(":core:designsystem")
include(":core:testing")
include(":feature:paytmhome")
include(":feature:home")
include(":feature:creategoal")
include(":feature:goaldetail")
include(":feature:settings")
