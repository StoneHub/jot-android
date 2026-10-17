plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "space.jot.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.monroestone.jotmodellab"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      jniLibs { useLegacyPackaging = true }
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
  // GenieX AAR prepared by scripts/prepare-runtime.py; its QAIRT libraries come from qnn-runtime.
  implementation(files("../build/runtime/geniex-android-0.8.0.aar"))
  implementation(libs.onnxruntime.android)
  implementation(libs.onnxruntime.android.qnn)
  implementation(libs.qnn.runtime)
  implementation(libs.jtransforms)
  implementation(libs.kotlinx.coroutines.android)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)

  testImplementation(libs.junit)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
}
