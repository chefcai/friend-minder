import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.friendminder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.friendminder"
        minSdk = 26
        targetSdk = 34

        // versionCode/versionName default to the last cut release (chefcai/friend-minder#37)
        // and can be overridden from the command line - release.yml does this automatically
        // by deriving both from the pushed `vX.Y.Z` tag, so a tagged release never reports a
        // stale version in Android Settings > App info regardless of what's committed here.
        // Local/CI builds that don't pass these properties (assembleDebug during development,
        // PR checks, `workflow_dispatch` dry runs) fall back to the values below, which should
        // still be bumped to match whenever a release is manually prepared without using the
        // tag-driven path.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 2
        versionName = project.findProperty("appVersionName") as String? ?: "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Fixed debug keystore (FRM-26), committed at the repo root, instead of relying
            // on AGP's implicit default (~/.android/debug.keystore). That default is
            // auto-generated with a *random* keypair the first time a debug build runs on a
            // machine, so every GitHub Actions run - a fresh ephemeral VM each time - was
            // producing a differently-signed "debug" APK. Two releases signed with different
            // certs can't upgrade in place (Android refuses the install as a signature
            // conflict) and Auto Backup restore-on-reinstall requires the cert to match too.
            // Alias/password are Android's standard debug convention, not a secret.
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // FRM-16: real release signing. release.yml sets these four env vars only
        // when the repo secrets RELEASE_KEYSTORE_BASE64/RELEASE_KEYSTORE_PASSWORD/
        // RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD exist (decoding the base64 into
        // RELEASE_KEYSTORE_FILE itself) - previously nothing in this file actually
        // read them, so setting those secrets alone did nothing: assembleRelease
        // had no signingConfig at all and produced an unsigned APK regardless.
        // Absent locally/in a PR build, so this config simply isn't created and
        // release build behavior there is unchanged (still unsigned).
        val releaseKeystoreFile = System.getenv("RELEASE_KEYSTORE_FILE")
        if (!releaseKeystoreFile.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    // GH #121/FRM-97: MIGRATION_1_2 is the project's first real Room schema
    // migration (version was 1 since FRM-81), so this wiring never existed
    // before - MigrationTestHelper needs the exported schemas bundled as
    // androidTest assets to construct a v1 database to migrate from.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }

    lint {
        // Blocking as of FRM-18. The two pre-existing errors that kept this report-only
        // are both fixed: MissingPermission in NotificationHelper.postReminder (added a
        // checkSelfPermission guard before notify(), since that call runs off a background
        // WorkManager job with no UI to prompt from) and PermissionImpliesUnsupportedChromeOsHardware
        // on the declared SEND_SMS permission (added a required="false" telephony uses-feature).
        // The four accessibility checks below were promoted from warning to error for FRM-16
        // and a full lintDebug run found zero violations of them in the current UI - promotion
        // kept as-is so any future regression fails the build instead of merging quietly.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        error += listOf("ContentDescription", "ClickableViewAccessibility", "LabelFor", "KeyboardInaccessibleWidget")
    }
}

kotlin {
    compilerOptions {
        // AGP 9.0+ has built-in Kotlin support and no longer needs the separate
        // org.jetbrains.kotlin.android plugin (applying it alongside AGP 9 is now
        // a hard error - see https://kotl.in/gradle/agp-built-in-kotlin). The old
        // android { kotlinOptions { jvmTarget = "17" } } DSL moves here instead.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Jetpack / AndroidX only — no Play Services, no Firebase (Google Play is our only distribution channel)
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    // Publisher (FRM-5): contact picker list + Material 3 components
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")
    // FRM-65: androidx.core.splashscreen (Android 12+ SplashScreen API + compat back-fill,
    // zero external dependency, matches DESIGN-SYSTEM-PHASE2.md §8.3)
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // FRM-81: partial Room migration (OutreachLog, ContactGroup/membership, SpecialDate)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0") // GrantPermissionRule (FRM-47)
    androidTestImplementation("androidx.work:work-testing:2.8.1") // TestListenableWorkerBuilder (FRM-47), matches work-runtime-ktx above
    androidTestImplementation("androidx.room:room-testing:2.8.5") // Room.inMemoryDatabaseBuilder for DAO/migration tests (FRM-81)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

