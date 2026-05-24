buildscript {
    extra["kotlinVersion"] = "2.2.21"
    extra["agpVersion"] = "9.2.1"
    extra["kspVersion"] = "2.3.8"
    extra["minSdk"] = 21
    extra["targetSdk"] = 34
    extra["compileSdk"] = 34
    extra["groupId"] = "com.github.donglua.layoutx2c"
    extra["versionName"] = "0.1.0-SNAPSHOT"
}

plugins {
    id("com.android.application") version "${extra["agpVersion"]}" apply false
    id("com.android.library") version "${extra["agpVersion"]}" apply false
    id("org.jetbrains.kotlin.android") version "${extra["kotlinVersion"]}" apply false
    id("org.jetbrains.kotlin.jvm") version "${extra["kotlinVersion"]}" apply false
    id("com.google.devtools.ksp") version "${extra["kspVersion"]}" apply false
}
