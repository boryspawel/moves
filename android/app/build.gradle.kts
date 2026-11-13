plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.motionecosystem.moves.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.motionecosystem.moves.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { buildConfig = true; compose = true }
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"${providers.gradleProperty("moves.devBaseUrl").getOrElse("http://10.0.2.2:8080/")}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            manifestPlaceholders["appAuthRedirectScheme"] = "com.motionecosystem.moves.mobile.dev"
            buildConfigField("String", "OIDC_ISSUER", "\"${providers.gradleProperty("moves.devOidcIssuer").getOrElse("http://10.0.2.2:8180/realms/motion-local")}\"")
            buildConfigField("String", "OIDC_CLIENT_ID", "\"motion-android-dev\"")
            buildConfigField("String", "OIDC_REDIRECT_URI", "\"com.motionecosystem.moves.mobile.dev:/oauth2redirect\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"${providers.gradleProperty("moves.prodBaseUrl").getOrElse("https://moves-api.example.invalid/")}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            manifestPlaceholders["appAuthRedirectScheme"] = "com.motionecosystem.moves.mobile"
            buildConfigField("String", "OIDC_ISSUER", "\"${providers.gradleProperty("moves.prodOidcIssuer").getOrElse("https://auth.example.invalid/realms/moves")}\"")
            buildConfigField("String", "OIDC_CLIENT_ID", "\"motion-android\"")
            buildConfigField("String", "OIDC_REDIRECT_URI", "\"com.motionecosystem.moves.mobile:/oauth2redirect\"")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("net.openid:appauth:0.11.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.1")
}
