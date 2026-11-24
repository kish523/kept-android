import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin, no Android dependencies: tests run on any computer in seconds,
// and the iOS app can later reuse the same rules (Kotlin Multiplatform) or port them.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // Show the golden-set accuracy report in the build output.
    testLogging {
        showStandardStreams = true
        events("failed")
    }
}
