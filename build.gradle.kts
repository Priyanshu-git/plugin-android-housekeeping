plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.housekeeping"
version = "0.1.0"

repositories {
    mavenCentral()
}

intellij {
    type.set("AI") // Android Studio
    version.set("2024.1.1.8") // Hedgehog+
    plugins.set(listOf("android"))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("241.*")
    }
}
