plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("com.squareup:kotlinpoet:${rootProject.extra["kotlinPoetVersion"]}")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}
