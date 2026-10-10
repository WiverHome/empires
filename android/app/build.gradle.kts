import java.util.Properties

// Нативная версия Empires: libGDX без WebView. Логика игры - модуль :logic (порт js/*.js).
// Картинки и звуки берутся из общей папки assets/ в корне репозитория.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gdxVersion: String by rootProject.extra
val natives: Configuration by configurations.creating

// Ключ релиза и пароли - в android/local.properties (release.storeFile, release.storePassword,
// release.keyAlias, release.keyPassword); сам файл ключа и local.properties в git не попадают
val localProps = Properties().also { p ->
    rootProject.file("local.properties").takeIf { f -> f.exists() }?.inputStream()?.use { stream -> p.load(stream) }
}
val releaseStore: File? = localProps.getProperty("release.storeFile")?.let { path -> rootProject.file(path) }?.takeIf { f -> f.exists() }

android {
    namespace = "empires.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.wiverhome.empires"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets["main"].apply {
        assets.srcDirs("../../assets")
        jniLibs.srcDirs("libs")
    }

    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = releaseStore
            storePassword = localProps.getProperty("release.storePassword")
            keyAlias = localProps.getProperty("release.keyAlias")
            keyPassword = localProps.getProperty("release.keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // без ключа релиз подписывается отладочным ключом (для проверки, не для магазина)
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "META-INF/*" }
}

dependencies {
    implementation(project(":logic"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// Нативные .so libGDX раскладываются по ABI в app/libs перед сборкой
val copyAndroidNatives by tasks.registering {
    doFirst {
        natives.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfterLast("natives-")
            val outputDir = file("libs/$abi")
            outputDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
