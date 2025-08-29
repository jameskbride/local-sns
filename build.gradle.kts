import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.typesafe.config.ConfigFactory

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("com.typesafe:config:1.4.2")
  }
}

plugins {
  kotlin ("jvm") version "1.9.0"
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.jameskbride.localsns"
val typesafeConf = ConfigFactory.parseFile(File("src/main/resources/application.conf"))
version = typesafeConf.getString("version")

repositories {
  mavenCentral()
}

val vertxVersion = "4.5.1"
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
  implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
  implementation("org.apache.camel:camel-core:$camelVersion")
  implementation("org.apache.camel:camel-file:$camelVersion")
  implementation("org.apache.camel:camel-aws2-sqs:$camelVersion")
  implementation("org.apache.camel:camel-http:$camelVersion")
  implementation("org.apache.camel:camel-rabbitmq:$camelVersion")
  implementation("org.apache.camel:camel-slack:$camelVersion")
  implementation("org.apache.camel:camel-aws2-lambda:$camelVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
  implementation("com.typesafe:config:1.4.3")
  implementation("org.apache.logging.log4j:log4j-core:2.22.1")
  implementation("org.apache.logging.log4j:log4j-api:2.22.1")
  implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1")
  implementation("com.google.code.gson:gson:2.10.1")

  testImplementation("io.vertx:vertx-junit5:$vertxVersion")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  testImplementation("org.danilopianini:khttp:1.4.2")
  testImplementation("org.jsoup:jsoup:1.17.1")
  testImplementation("org.elasticmq:elasticmq-server_3:1.5.4")
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
  useJUnitPlatform {
    if (System.getenv("CI") != null) {
      excludeTags("skipForCI")
    }
  }
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("--redeploy=$watchForChange", "--on-redeploy=$doOnChange")
}

java.targetCompatibility = JavaVersion.VERSION_11
java.sourceCompatibility = JavaVersion.VERSION_11