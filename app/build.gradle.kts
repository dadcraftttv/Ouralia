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
        versionCode = 12
        versionName = "0.9.0-rc4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Release candidates are signed with the debug key, as rc3 and rc4 were (those were app.pdscenes, the working
            // name: Ouralia is a new app beside them). A real release key comes with Play release prep.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        jniLibs {
            // Pd loads its optional externals (fiddle~, bonk~, sigmund~, ...) from real files, so the
            // native libraries must be unpacked at install time instead of staying inside the apk.
            useLegacyPackaging = true
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
    implementation("io.github.libpd.android:pd-core:1.4.2-pdscenes.1")
}
