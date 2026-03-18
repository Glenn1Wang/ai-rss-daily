plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.rss"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // RSS parsing
    implementation("com.rometools:rome:2.1.0")

    // LangChain4j
    implementation("dev.langchain4j:langchain4j:0.36.2")
    implementation("dev.langchain4j:langchain4j-open-ai:0.36.2")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // HTTP client for RSS
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("com.rss.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.named<JavaExec>("run") {
    // Pass environment variables to the application
    environment(System.getenv())
}
