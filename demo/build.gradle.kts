plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
}

val compileSdk: Int by rootProject.extra
val minSdk: Int by rootProject.extra
val targetSdk: Int by rootProject.extra

android {
    namespace = "com.github.donglua.layoutx2c.demo"
    compileSdk = rootProject.extra["compileSdk"] as Int

    defaultConfig {
        applicationId = "com.github.donglua.layoutx2c.demo"
        minSdk = rootProject.extra["minSdk"] as Int
        targetSdk = rootProject.extra["targetSdk"] as Int
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(project(":runtime"))
    ksp(project(":ksp-processor"))

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.14.0")
}

ksp {
    arg("layoutx2c.resDir", "${project.projectDir}/src/main/res")
    arg("layoutx2c.packageName", "com.github.donglua.layoutx2c.demo.generated")
    arg("layoutx2c.rPackageName", "com.github.donglua.layoutx2c.demo")
}
