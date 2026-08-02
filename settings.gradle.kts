// Lets Gradle fetch the JDK 21 the IntelliJ Platform requires, no matter which JDK is used
// to start the build (JetBrains IDEs ship newer JBRs these days).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bracket-colorizer"
