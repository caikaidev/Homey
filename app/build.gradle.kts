import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

val signingProperties = Properties().apply {
  val propertiesFile = rootProject.file("local.properties")
  if (propertiesFile.exists()) {
    propertiesFile.inputStream().use { load(it) }
  }
}

/** 签名参数：本地从 local.properties 读，CI 从环境变量（GitHub Secrets）读。 */
fun signingValue(propertyKey: String, envKey: String): String? =
  signingProperties.getProperty(propertyKey) ?: providers.environmentVariable(envKey).orNull

val releaseStoreFilePath: String? = signingValue("noticlaw.release.storeFile", "RELEASE_STORE_FILE")

android {
  namespace = "ian.dev.zaizai"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "ian.dev.zaizai"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "0.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (releaseStoreFilePath != null) {
      create("release") {
        storeFile = file(releaseStoreFilePath)
        storePassword = signingValue("noticlaw.release.storePassword", "RELEASE_STORE_PASSWORD")
        keyAlias = signingValue("noticlaw.release.keyAlias", "RELEASE_KEY_ALIAS")
        keyPassword = signingValue("noticlaw.release.keyPassword", "RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // 没有配置签名（CI、新电脑）时打未签名包，不影响编译和测试
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      // 配置了 release 签名就沿用，否则使用系统默认的调试签名
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.okhttp)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
  // 单元测试跑在 JVM 上，android.jar 里的 org.json 只是桩，换成真实实现
  testImplementation(libs.org.json)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
