plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.njarrar.backthen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.njarrar.backthen"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "1.29.0"
        resourceConfigurations += listOf("en", "ar", "es", "fr", "pt", "zh")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The language files in lang/ at the top of the repo ship as app assets (en.xml, ar.xml, ...).
    sourceSets["main"].assets.srcDir("../../lang")
    // The city list for places (cities.tsv). Exclude places/README.md from the packaged APK.
    sourceSets["main"].assets.srcDir("../../places")

    androidResources {
        ignoreAssetsPattern = "!README.md:!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    // Builds sign with the Play upload key when these are set (the Play Store and Build
    // apps workflows set them from GitHub secrets). Without them the release bundle is
    // unsigned and debug builds use the usual debug key. Signing debug builds with the
    // upload key too gives the APK in build/ the same key each run, so a new one installs over the last.
    val uploadKey = System.getenv("UPLOAD_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = uploadKey
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (uploadKey != null) signingConfig = signingConfigs.getByName("upload")
            buildConfigField("boolean", "TEST_HOOKS", "true")
        }
        release {
            if (uploadKey != null) {
                signingConfig = signingConfigs.getByName("upload")
            } else if (project.hasProperty("signReleaseWithDebugKey")) {
                // CI only: lets the shrunk release build install on an emulator for a smoke test.
                signingConfig = signingConfigs.getByName("debug")
            }
            // R8 shrinks and renames the code. Google Play flags apps without it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "TEST_HOOKS", "false")
        }
        // Release QA only (docs/QA.md, gate 3): the release build, shrunk by R8 the same way,
        // plus the launch options that open each screen with a set language. Never uploaded.
        create("qa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "TEST_HOOKS", "true")
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
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.exifinterface:exifinterface:1.4.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("me.saket.telephoto:zoomable-image-coil3:0.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation(kotlin("test"))

    // On-phone checks (src/androidTest), run on an emulator by CI before every store upload.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
}
