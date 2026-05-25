plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":compiler-core"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${rootProject.extra["kspVersion"]}")
    implementation("com.squareup:kotlinpoet:1.16.0")
    implementation("com.squareup:kotlinpoet-ksp:1.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.0")
}
