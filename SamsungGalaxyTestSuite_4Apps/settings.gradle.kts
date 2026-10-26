pluginManagement {
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

rootProject.name = "SamsungGalaxyTestSuite_4Apps"
include(":app1_shortnews")
include(":app2_longnews")
include(":app3_photosaver")
include(":app4_cachefiller")
