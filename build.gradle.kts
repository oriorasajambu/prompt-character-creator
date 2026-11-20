plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    application
}

group = "com.promptgenerator"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.promptgenerator.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

val jdk17Home =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }.map { it.metadata.installationPath.asFile.absolutePath }

tasks.register<Exec>("packageWindows") {
    group = "distribution"
    description = "Build a portable Windows app-image and ZIP with a bundled Java runtime."
    dependsOn(tasks.named("installDist"))

    doFirst {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            throw GradleException("The Windows app-image can only be packaged on Windows.")
        }
        commandLine(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            rootProject.file("scripts/package-windows.ps1").absolutePath,
            "-JdkHome",
            jdk17Home.get(),
            "-ProjectDir",
            rootProject.projectDir.absolutePath,
            "-Version",
            project.version.toString(),
        )
    }
}
