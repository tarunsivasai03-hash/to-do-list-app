plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.notionapp.mynotes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.notionapp.mynotes"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Export Room schemas so migrations can be verified
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Enable ViewBinding — generates binding classes for each XML layout
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // Material Design 3
    implementation(libs.material)

    // Room Database (Java annotationProcessor instead of KSP)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle (ViewModel + LiveData)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.common)
}
