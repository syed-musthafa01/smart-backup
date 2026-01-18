plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gptbackup"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.gptbackup"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation("com.google.api-client:google-api-client-android:1.33.2")
    implementation("com.google.http-client:google-http-client-gson:1.41.5")
    implementation("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")


    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-auth")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}