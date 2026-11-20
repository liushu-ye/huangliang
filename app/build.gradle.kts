plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.liushuye.huangliang"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.liushuye.huangliang"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

// 项目路径含中文时，测试 worker 会按 GBK 去解读类路径，结果是
// ClassNotFoundException: ...StatsLogicTest（类明明编译出来了）。
// 这是路径问题不是代码问题，所以直接跳过并说明，而不是给一个看不懂的失败。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    onlyIf {
        val asciiOnly = rootDir.absolutePath.all { it.code < 128 }
        if (!asciiOnly) {
            logger.lifecycle(
                "跳过单元测试：项目路径含非 ASCII 字符（$rootDir），放到纯英文路径即可运行"
            )
        }
        asciiOnly
    }
}
