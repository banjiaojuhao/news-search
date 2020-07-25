import io.github.banjiaojuhao.search.gradle.Versions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

plugins {
    id("org.jetbrains.kotlin.jvm") apply true
    application
}

dependencies {
    api(project(":search-db"))
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Versions.kotlin)
    implementation("org.jetbrains.exposed", "exposed", Versions.exposed)
    implementation("mysql", "mysql-connector-java", Versions.mysql)

    implementation("org.jsoup", "jsoup", Versions.jsoup)

    implementation("io.netty", "netty-transport-native-epoll", Versions.epoll)
    implementation("io.vertx", "vertx-core", Versions.vertx)
    implementation("io.vertx", "vertx-web", Versions.vertx)
    implementation("io.vertx", "vertx-web-client", Versions.vertx)
    implementation("io.vertx", "vertx-lang-kotlin", Versions.vertx)
    implementation("io.vertx", "vertx-lang-kotlin-coroutines", Versions.vertx)

    implementation("org.apache.lucene", "lucene-core", Versions.lucene)
    implementation("org.apache.lucene", "lucene-analyzers-common", Versions.lucene)
    implementation("org.apache.lucene", "lucene-analyzers-smartcn", Versions.lucene)
    implementation("org.apache.lucene", "lucene-queryparser", Versions.lucene)


    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    // Define the main class for the application.
    mainClassName = "io.github.banjiaojuhao.search.spider.AppKt"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.3"
        languageVersion = "1.3"
    }
}
