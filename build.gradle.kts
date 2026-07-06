import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.infipush"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        jvmToolchain(17)
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.infinity-verse"
                artifactId = "infipush-sdk"
                version = "1.0.2"
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/infinity-verse/infipush-sdk")
                credentials {
                    val localProps = Properties()
                    val localPropsFile = rootProject.file("local.properties")
                    if (localPropsFile.exists()) {
                        localProps.load(FileInputStream(localPropsFile))
                    }

                    username = localProps.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                    password = localProps.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
    // Firebase Cloud Messaging — for receiving push token
//    implementation(libs.firebase.messaging)

    implementation("com.google.firebase:firebase-messaging:25.1.0")
}
