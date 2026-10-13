plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ruich97.nijikaahoge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ruich97.nijikaahoge"
        minSdk = 21
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jbox2d:jbox2d-library:2.2.1.1")
    androidTestCompileOnly(files(
        "${android.sdkDirectory}/platforms/android-35/optional/android.test.runner.jar",
        "${android.sdkDirectory}/platforms/android-35/optional/android.test.base.jar"
    ))
}
