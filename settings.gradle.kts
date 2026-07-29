rootProject.name = "sykmelding-bucket-upload"

val ktorVersion = "3.5.1"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:${ktorVersion}")
    }
}
