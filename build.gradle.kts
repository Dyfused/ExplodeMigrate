plugins {
    id("java")
    kotlin("jvm") version "1.7.20"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "explode"
version = "1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.slf4j:slf4j-api:2.0.3")
    implementation("ch.qos.logback:logback-classic:1.4.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation(fileTree("libs"))
    implementation("org.litote.kmongo:kmongo-coroutine:4.7.1")
    implementation("com.google.code.gson:gson:2.9.0")
    //implementation("org.mongodb:mongodb-driver-sync:4.7.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("explode.migrate.Main")
}