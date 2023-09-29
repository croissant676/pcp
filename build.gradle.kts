val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
	kotlin("jvm") version "1.9.10"
	id("io.ktor.plugin") version "2.3.4"
	id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
}

group = "dev.kason"
version = "0.0.1"

application {
	mainClass.set("dev.kason.ApplicationKt")

	val isDevelopment: Boolean = project.ext.has("development")
	applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("io.ktor:ktor-server-core-jvm")
	implementation("io.ktor:ktor-server-host-common-jvm")
	implementation("io.ktor:ktor-server-status-pages-jvm")
	implementation("io.ktor:ktor-server-content-negotiation-jvm")
	implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
	implementation("io.ktor:ktor-server-websockets-jvm")
	implementation("io.ktor:ktor-server-netty-jvm")
	implementation("ch.qos.logback:logback-classic:$logback_version")
	implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
	implementation("io.ktor:ktor-server-sessions-jvm:2.3.4")
	testImplementation("io.ktor:ktor-server-tests-jvm")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
	testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.4")
}
