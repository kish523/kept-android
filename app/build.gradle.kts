import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "app.kept"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.kept"
        minSdk = 26 // Android 8.0
        targetSdk = 36
        // CI passes its run number (-PbuildNumber=N), which only grows, so every published build
        // is newer than the last; that's also how the in-app updater compares builds.
        versionCode = (project.findProperty("buildNumber") as String?)?.toInt() ?: 3
        versionName = "0.2.1"
        // Public repo that holds only release builds and notes (the source repo is private).
        buildConfigField("String", "UPDATE_REPO", "\"kish523/kept-releases\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A fixed debug key, so every test build installs over the previous one and keeps your receipts.
        // (CI runners would otherwise make a new random key per run, and Android refuses that update.)
        // This is only for test builds; a Play Store release gets its own private key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric renders Compose screens on the JVM for screenshot checks.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Screenshots render only when asked for: gradle :app:testDebugUnitTest -Pscreenshots
            if (project.hasProperty("screenshots")) {
                test.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.path)
            }
            test.maxHeapSize = "2g"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// The changelog ships inside the app, so "What's new" works offline right after an update.
val bundleChangelog = tasks.register<Copy>("bundleChangelog") {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/changelog"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/changelog"))
tasks.named("preBuild") { dependsOn(bundleChangelog) }

// Exported schemas double as debug-only assets, so migration tests run against the real old schema.
// (Release builds don't include them.)
android.sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

room {
    // Schema JSON files are committed so every migration can be tested against the real old schema.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:parser"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.haze)
    implementation(libs.androidx.work.runtime)

    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
