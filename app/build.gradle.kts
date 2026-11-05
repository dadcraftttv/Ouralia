plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.dadcraftttv.ouralia"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.dadcraftttv.ouralia"
        // libpd's pd-core (Oboe backend) requires API 28+, i.e. Android 9 and newer.
        minSdk = 28
        targetSdk = 36
        versionCode = 16
        versionName = "0.9.0-rc6"
    }

    // The Play release key, when DadCraft has made one: its path and passwords live in ~/.gradle/gradle.properties
    // (ouraliaKeystore, ouraliaKeystorePassword, ouraliaKeyAlias, ouraliaKeyPassword), never in the repo.
    // Without them, release builds are signed with the debug key, as every rc so far (docs/RELEASING.md).
    val releaseKey = providers.gradleProperty("ouraliaKeystore").orNull
    signingConfigs {
        if (releaseKey != null) {
            create("play") {
                storeFile = file(releaseKey)
                storePassword = providers.gradleProperty("ouraliaKeystorePassword").get()
                keyAlias = providers.gradleProperty("ouraliaKeyAlias").get()
                keyPassword = providers.gradleProperty("ouraliaKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and renames the code (ROADMAP 22); app/proguard-rules.pro keeps libpd and its callbacks.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release candidates are signed with the debug key, as rc3 and rc4 were (those were app.pdscenes, the working
            // name: Ouralia is a new app beside them). The Play key, once it exists, signs instead (see above).
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "play" else "debug")
        }
        // The R.5 dev path (DadCraft, Oct 30): new sounds are tried on the `dev` branch in "Ouralia Dev", a debug
        // build that installs beside the release app (its own id, settings and logs), so he compares the two directly.
        // `./gradlew assembleDev`; its name comes from src/dev/res.
        create("dev") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "debug"
        }
    }

    packaging {
        jniLibs {
            // Pd loads its optional externals (fiddle~, bonk~, sigmund~, ...) from real files, so the
            // native libraries must be unpacked at install time instead of staying inside the apk.
            useLegacyPackaging = true
            // Sound kinds (step 4c, every build since Nov 5): LiteRT on ARM only (DadCraft, Oct 31); x86 goes without.
            excludes += setOf("lib/x86/libtensorflowlite_jni.so", "lib/x86_64/libtensorflowlite_jni.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Pure Data for Android (libpd + Oboe audio backend), built by tools/build_pdcore.sh from our forks: what the
    // 1.4.2 snapshot was (Pd 0.55.2, PdAudio.setDevicesId) plus the Oboe unplug fix (libpd #428, pd-for-android
    // #198). The last numbered release on Maven Central is 1.2.1-rc6 (2021, no device choice).
    implementation("io.github.libpd.android:pd-core:1.4.2-pdscenes.2")
    // Sound kinds, step 4c (Oct 31, docs/design/sound-kinds.md): YAMNet on LiteRT (Apache 2.0). In every build since
    // Nov 5 (DadCraft: "bring it in"; it was Ouralia Dev only).
    implementation("com.google.ai.edge.litert:litert:1.4.2")
}
