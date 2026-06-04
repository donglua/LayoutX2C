plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
}

val kspVersion: String by rootProject.extra
val agpVersion: String by rootProject.extra

dependencies {
    implementation("com.android.tools.build:gradle:$agpVersion")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$kspVersion")

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}

gradlePlugin {
    website.set("https://github.com/donglua/LayoutX2C")
    vcsUrl.set("https://github.com/donglua/LayoutX2C.git")
    plugins {
        create("layoutx2c") {
            id = "io.github.donglua.layoutx2c"
            implementationClass = "com.github.donglua.layoutx2c.plugin.LayoutX2CPlugin"
            displayName = "LayoutX2C Gradle Plugin"
            description = "Compile-time XML layout to code generation"
            tags.set(listOf("android", "layout", "ksp", "codegen"))
        }
    }
}

if (providers.gradleProperty("layoutx2c.enablePublishing").isPresent) {
    pluginManager.apply("com.gradle.plugin-publish")
}
