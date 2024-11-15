plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.brailleapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.brailleapp"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    //CameraX 라이브러리
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    //ML Kit OCR 라이브러리
    implementation(libs.mlkit.textRecognition)
    implementation(libs.mlkit.textRecognitionKorean)
    implementation(libs.mlkit.visioncommon)

    //BLE 라이브러리
    implementation(libs.androidx.bluetooth)
}
