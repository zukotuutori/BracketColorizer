import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.zukotuutori"
version = "1.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Built against IntelliJ IDEA (the unified distribution since 2025.3). The resulting jar works in
        // every JetBrains IDE that provides the platform and language modules declared in plugin.xml.
        intellijIdea("2026.3")
        testFramework(TestFrameworkType.Platform)
        // Only used by the tests, to have real languages (lexer, comments, string literals)
        // available. The plugin itself depends on neither Java nor Markdown support.
        testBundledPlugin("com.intellij.java")
        testBundledPlugin("org.intellij.plugins.markdown")
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "263" // 2026.3 and newer, every JetBrains IDE
            untilBuild = provider { null } // no upper bound - keeps working after IDE updates
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
