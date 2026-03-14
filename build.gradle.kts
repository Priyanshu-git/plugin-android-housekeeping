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

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    // IntelliJ 2024.2+ modular platform: settings module + Java/Kotlin plugins for UAST
    testImplementation(
        fileTree("${gradle.gradleUserHomeDir}/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2024.2.1") {
            include("**/intellij.platform.settings.local.jar")
            include("**/plugins/java/lib/*.jar")
            include("**/plugins/java/lib/modules/*.jar")
            include("**/plugins/Kotlin/lib/*.jar")
        }
    )
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
    version.set("2024.2.1") // Android Studio Koala / IntelliJ 2024.1 base
    type.set("IC")

    plugins.set(
        listOf(
            "com.intellij.java",
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
        untilBuild.set("300")
    }

    signPlugin {
        certificateChain.set("")
        privateKey.set("")
        password.set("")
    }

    publishPlugin {
        token.set("")
    }

    test {
        // IntelliJ 2024.2+ needs idea.home.path for service container initialization
        // Also require Java + Kotlin plugins so UAST extension points get registered
        doFirst {
            val cacheBase = file("${gradle.gradleUserHomeDir}/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2024.2.1")
            val ideDir = cacheBase.walkTopDown().maxDepth(2).find { it.name == "ideaIC-2024.2.1" && it.isDirectory }
            if (ideDir != null) {
                systemProperty("idea.home.path", ideDir.absolutePath)
            }
            systemProperty("idea.required.plugins.id", "com.nexxlabs.housekeeping,com.intellij.java,org.jetbrains.kotlin")
            // Disable modular loading to force classic plugin resolution from plugin.path
            systemProperty("intellij.platform.runtime.repository.path", "")
        }
    }

    prepareTestingSandbox {
        // Copy bundled plugins into test sandbox so their extension points get registered
        val cacheBase = file("${gradle.gradleUserHomeDir}/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2024.2.1")
        val ideDir = cacheBase.walkTopDown().maxDepth(2).find { it.name == "ideaIC-2024.2.1" && it.isDirectory }
        if (ideDir != null) {
            val pluginsDir = file("${ideDir.absolutePath}/plugins")
            listOf("Kotlin", "java").forEach { pluginName ->
                val pluginDir = file("${pluginsDir.absolutePath}/$pluginName")
                if (pluginDir.exists()) {
                    from(pluginDir) { into(pluginName) }
                }
            }
        }
    }
}
