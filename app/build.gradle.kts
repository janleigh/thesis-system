plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.chate.thesis"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.chate.thesis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val gitCommitHash: String =
            providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
            }.standardOutput.asText.map { it.trim() }.getOrElse("7d8135a")
        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

ktlint {
    android.set(true)
}
