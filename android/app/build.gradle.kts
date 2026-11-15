plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.hamkketayo.car"
    compileSdk = 34
    useLibrary("android.car")          // AAOS Car API (CarPropertyManager, CarUxRestrictionsManager)
    defaultConfig {
        applicationId = "com.hamkketayo.car"
        minSdk = 28; targetSdk = 34
        versionCode = 1; versionName = "0.1.0"
        // 에뮬레이터에서 PC의 서버(10.0.2.2)에 접속
        buildConfigField("String", "SERVER_URL", "\"http://10.0.2.2:8000\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // TODO: Pleos Connect SDK(Vehicle, NaviHelper) 의존성은 Pleos Playground 문서의 안내대로 추가
}
