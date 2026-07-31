plugins {
    id("org.jetbrains.kotlin.jvm")
}

val jdkMajor = JavaVersion.current().majorVersion.toInt()
val kotlinJvmTarget =
    org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jdkMajor.toString())

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jdkMajor))
    }
}

kotlin {
    jvmToolchain(jdkMajor)
    compilerOptions {
        jvmTarget.set(kotlinJvmTarget)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(jdkMajor)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
