import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.internal.classpath.Instrumented.systemProperty
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

plugins {
  kotlin ("jvm") version "1.9.0"
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.jameskbride.localsns"
version = project.version

repositories {
  mavenCentral()
}

val vertxVersion = "4.4.4"
val camelVersion = "3.21.0"
val junitJupiterVersion = "5.9.2"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set("com.jameskbride.localsns.Main")
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-camel-bridge:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
  implementation("org.apache.camel:camel-core:$camelVersion")
  implementation("org.apache.camel:camel-file:$camelVersion")
  implementation("org.apache.camel:camel-aws2-sqs:$camelVersion")
  implementation("org.apache.camel:camel-http:$camelVersion")
  implementation("org.apache.camel:camel-rabbitmq:$camelVersion")
  implementation("org.apache.camel:camel-slack:$camelVersion")
  implementation("org.apache.camel:camel-aws2-lambda:$camelVersion")
  implementation("com.typesafe:config:1.4.2")
  implementation("org.apache.logging.log4j:log4j-core:2.20.0")
  implementation("org.apache.logging.log4j:log4j-api:2.20.0")

  testImplementation("io.vertx:vertx-junit5:4.4.5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  testImplementation("org.danilopianini:khttp:1.3.2")
  testImplementation("org.jsoup:jsoup:1.16.1")
  testImplementation("org.elasticmq:elasticmq-server_2.13:1.4.4")
  testImplementation("software.amazon.awssdk:sns:2.21.0")
  testImplementation("software.amazon.awssdk:sqs:2.21.0")

}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "11"

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions.jvmTarget = "11"

tasks.withType<ShadowJar> {
  archiveClassifier.set("")
  dependsOn("distTar", "distZip")
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("--redeploy=$watchForChange", "--on-redeploy=$doOnChange")
}

java.targetCompatibility = JavaVersion.VERSION_11
java.sourceCompatibility = JavaVersion.VERSION_11