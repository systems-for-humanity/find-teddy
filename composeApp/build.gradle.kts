import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Pre-renders the spoken prompts whenever the English strings change.
// Requires network + `pipx install edge-tts`; the rendered mp3s are
// committed, so the task is UP-TO-DATE on normal builds. Skip explicitly
// with -x generateVoicePrompts if strings changed while offline.
val generateVoicePrompts by tasks.registering(Exec::class) {
    inputs.files(
        "src/commonMain/composeResources/values/strings.xml",
        rootProject.file("tools/generate_voice_prompts.py"),
    )
    // The clip files are managed by the script itself (and by
    // record_voice_prompts.py), so gradle only tracks a stamp: declaring the
    // voice dir as output would mark the task stale after every out-of-band
    // render or recording session and re-run a long TTS job.
    val stamp = layout.buildDirectory.file("generateVoicePrompts.stamp")
    outputs.file(stamp)
    commandLine("python3", rootProject.file("tools/generate_voice_prompts.py").absolutePath)
    doLast { stamp.get().asFile.writeText("ok\n") }
}

tasks.matching {
    it.name == "preBuild" ||
        it.name.startsWith("compileKotlinIos") ||
        it.name.startsWith("copyNonXmlValueResources") ||
        it.name.startsWith("prepareComposeResourcesTask")
}.configureEach {
    dependsOn(generateVoicePrompts)
}

compose.resources {
    // Pin the Res class package: it must not drift with the gradle
    // project/module names (imports reference it directly).
    packageOfResClass = "app.s4h.findteddy.resources"
}

android {
    namespace = "app.s4h.findteddy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.s4h.findteddy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Upload key lives outside the repo; FINDTEDDY_* properties come from
    // ~/.gradle/gradle.properties. Clones without them still build (unsigned
    // release / debug only).
    val keystorePath = findProperty("FINDTEDDY_KEYSTORE_PATH") as String?
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = findProperty("FINDTEDDY_KEYSTORE_PASSWORD") as String?
                keyAlias = findProperty("FINDTEDDY_KEY_ALIAS") as String?
                keyPassword = findProperty("FINDTEDDY_KEY_PASSWORD") as String?
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
