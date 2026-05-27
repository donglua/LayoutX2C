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
        testInstrumentationRunner = "com.github.donglua.layoutx2c.demo.GeneratedInflateTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // =========================================================================
    // NOTE: The following configuration is ONLY used for the "Code Viewer" 
    // feature in this demo app, which displays the generated code on screen.
    // You DO NOT need this in your own project to use LayoutX2C!
    // =========================================================================
    sourceSets {
        getByName("debug") {
            assets.srcDir("build/generated/custom_assets")
        }
    }
}

val copySourceCodeForDemo by tasks.registering(Copy::class) {
    // Read the original XML files
    from("src/main/res/layout") {
        include("demo_*.xml")
        into("xml")
    }
    // Read the generated Kotlin files (assuming debug build)
    from("build/generated/ksp/debug/kotlin/com/github/donglua/layoutx2c/demo/generated") {
        include("*.kt")
        into("kotlin")
    }
    into(layout.buildDirectory.dir("generated/custom_assets/code"))
    
    // We need to wait for KSP to generate the code first
    dependsOn("kspDebugKotlin")
}

tasks.whenTaskAdded {
    if (name == "generateDebugAssets" || name.startsWith("lint") || name.startsWith("generateDebugLint")) {
        dependsOn(copySourceCodeForDemo)
    }
}
// =========================================================================


androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.kotlin?.addStaticSourceDirectory("build/generated/ksp/debug/kotlin")
    }
}

dependencies {
    implementation(project(":runtime"))
    ksp(project(":ksp-processor"))

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")

    testImplementation("junit:junit:4.13.2")
}
