plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
}

val kspVersion: String by rootProject.extra
val agpVersion: String by rootProject.extra

dependencies {
    implementation("com.android.tools.build:gradle:$agpVersion")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$kspVersion")
}

gradlePlugin {
    plugins {
        create("layoutx2c") {
            id = "com.github.donglua.layoutx2c"
            implementationClass = "com.github.donglua.layoutx2c.plugin.LayoutX2CPlugin"
            displayName = "LayoutX2C Gradle Plugin"
            description = "Compile-time XML layout to code generation"
        }
    }
}
