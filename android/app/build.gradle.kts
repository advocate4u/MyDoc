plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.advocate4u.mydoc"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.advocate4u.mydoc"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    val keystoreFile = System.getenv("MYADV_KEYSTORE_FILE")
    val storePassword = System.getenv("MYADV_STORE_PASSWORD")
    val keyAlias = System.getenv("MYADV_KEY_ALIAS")
    val keyPassword = System.getenv("MYADV_KEY_PASSWORD")
    val releaseSigningConfigured = !keystoreFile.isNullOrBlank() &&
        !storePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyPassword.isNullOrBlank() &&
        file(keystoreFile!!).isFile

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile!!)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/NOTICE*", "META-INF/LICENSE*") }
}

// The canonical icon is kept at the repository root as MyDoc.png. Copy it into
// Android resources during every build so the APK always uses that exact asset.
val copyMyDocIcon = tasks.register<Copy>("copyMyDocIcon") {
    val source = rootProject.projectDir.parentFile.resolve("MyDoc.png")
    from(source)
    into(layout.projectDirectory.dir("src/main/res/drawable-nodpi"))
    rename { "mydoc_icon.png" }
}
tasks.named("preBuild") { dependsOn(copyMyDocIcon) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
