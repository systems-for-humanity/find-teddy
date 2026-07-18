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

android {
    namespace = "com.messytable.findteddy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.messytable.findteddy"
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
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
