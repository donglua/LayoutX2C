plugins {
    id("org.jetbrains.kotlin.jvm")
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

dependencies {
    implementation(project(":compiler-core"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${rootProject.extra["kspVersion"]}")
    implementation("com.squareup:kotlinpoet:${rootProject.extra["kotlinPoetVersion"]}")
    implementation("com.squareup:kotlinpoet-ksp:${rootProject.extra["kotlinPoetVersion"]}")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}
