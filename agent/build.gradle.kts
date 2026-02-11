import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("org.besomontro.MainKt")
}

group = "org.besomontro"
version = "2.1"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.planner)
    runtimeOnly(libs.koog.tools)
    implementation(libs.koog.executor.openai.client)
    implementation(libs.koog.executor.google.client)
    implementation(libs.koog.executor.ollama.client)
    implementation(libs.koog.executor.openrouter.client)
    implementation(libs.koog.features.event.handler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql.driver)

    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(
        listOf(
            "-Xannotation-default-target=param-property"
        )
    )
}