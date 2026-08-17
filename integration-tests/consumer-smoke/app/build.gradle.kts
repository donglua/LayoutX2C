plugins {
    id("com.android.application") version "9.3.1"
    id("io.github.donglua.layoutx2c")
}

android {
    namespace = "com.example.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

layoutX2C {
    maxFallbackLayouts.set(0)
}

tasks.register("verifyLayoutX2CSmoke") {
    dependsOn("layoutX2CReport")
    doLast {
        check(file("build/generated/ksp/debug/kotlin/com/example/consumer/generated/LayoutX2CGenerated.kt").isFile) {
            "LayoutX2C registry was not generated"
        }
        check(file("build/reports/layoutx2c/index.json").isFile) {
            "LayoutX2C JSON report was not generated"
        }
        check(file("build/reports/layoutx2c/index.html").isFile) {
            "LayoutX2C HTML report was not generated"
        }
    }
}
