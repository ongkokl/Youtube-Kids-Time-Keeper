plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.parentguard.youtubetimerguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.parentguard.youtubetimerguard"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.2.3"
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


val prepareLauncherIcon by tasks.registering {
    val parts = listOf(
        rootProject.file("icon_parts/kid_safe_icon.part1.b64"),
        rootProject.file("icon_parts/kid_safe_icon.part2.b64"),
        rootProject.file("icon_parts/kid_safe_icon.part3.b64")
    )
    val output = file("src/main/res/drawable/kid_safe_icon_v022.webp")
    inputs.files(parts)
    outputs.file(output)

    doLast {
        val encoded = parts.joinToString("") { it.readText().trim() }
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        check(bytes.size == 8126) { "Unexpected launcher icon size: ${bytes.size}" }
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(sha256 == "62a061ce57ff933aa4f8f651f9e90851811f3810c0c329ed7da00898903912f9") {
            "Launcher icon checksum mismatch"
        }
        output.parentFile.mkdirs()
        output.writeBytes(bytes)
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareLauncherIcon)
}
