plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

/**
 * 🔐 Force Java 17 everywhere
 */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

/**
 * IntelliJ Platform configuration
 */
intellij {
    version.set("2024.1") // Android Studio Koala / IntelliJ 2024.1 base
    type.set("IC")

    plugins.set(
        listOf(
            "com.intellij.java",
            "org.jetbrains.kotlin",
            "com.intellij.properties"
        )
    )
}

tasks {

    withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("243.*")
    }

    signPlugin {
        certificateChain.set("")
        privateKey.set("")
        password.set("")
    }

    publishPlugin {
        token.set("")
    }
}
