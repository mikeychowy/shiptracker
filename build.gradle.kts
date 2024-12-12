plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.4.4"
    id("io.micronaut.test-resources") version "4.4.4"
    id("io.micronaut.aot") version "4.4.4"
    id("com.diffplug.spotless") version "6.25.0"
}

version = "0.1"
group = "com.example"

repositories {
    mavenCentral()
}

val awaitilityVersion = "4.2.2"
val commonsLangVersion = "3.17.0"
val commonsIOVersion = "2.18.0"
val jtsVersion = "1.20.0"
val eclipseCollectionsVersion = "11.1.0"

dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("io.micronaut.data:micronaut-data-document-processor")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micrometer:context-propagation")
    implementation("io.micronaut:micronaut-aop")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-retry")
    implementation("io.micronaut:micronaut-websocket")
    implementation("io.micronaut.cache:micronaut-cache-caffeine")
    implementation("io.micronaut.data:micronaut-data-mongodb")
    implementation("io.micronaut.mongodb:micronaut-mongo-sync")
    implementation("io.micronaut.objectstorage:micronaut-object-storage-local")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.reactor:micronaut-reactor-http-client")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.session:micronaut-session")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-eclipse-collections")
    implementation("org.apache.commons:commons-lang3:$commonsLangVersion")
    implementation("commons-io:commons-io:$commonsIOVersion")
    implementation("org.locationtech.jts:jts-core:$jtsVersion")
    implementation("org.eclipse.collections:eclipse-collections-api:$eclipseCollectionsVersion")
    implementation("org.eclipse.collections:eclipse-collections:$eclipseCollectionsVersion")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.mongodb:mongodb-driver-sync")
    runtimeOnly("org.yaml:snakeyaml")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.hamcrest:hamcrest")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.mockito:mockito-core")
}

application {
    mainClass = "com.example.Application"
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.example.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = true
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint() // has its own section below
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    java {
        importOrder()
        removeUnusedImports("cleanthat-javaparser-unnecessaryimport")
        palantirJavaFormat("2.50.0").style("GOOGLE").formatJavadoc(true)
        formatAnnotations()
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}
