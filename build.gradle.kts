import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val junit5Version = "5.4.0"
val junitPlatformVersion = "1.4.0"

plugins {
    java
    kotlin("jvm") version "1.3.21"
    id("com.github.ben-manes.versions") version "0.20.0"

}

group = "group"
version = "1.0-SNAPSHOT"

repositories {
//    maven { setUrl("http://dl.bintray.com/kotlin/kotlin-eap") }
    jcenter()
    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    testImplementation("io.strikt:strikt-core:0.17.2")
    testImplementation("dev.minutest:minutest:1.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5Version")

}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}
