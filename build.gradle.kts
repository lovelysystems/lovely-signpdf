import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    application
    kotlin("jvm") version "1.2.41"
    id("com.lovelysystems.gradle") version ("0.0.6")
}

lovely {
    gitProject()
    dockerProject("lovelysystems/signpdf")
}

group = "com.lovelysystems"

application {
    mainClassName = "io.ktor.server.netty.DevelopmentEngine"
}

repositories {
    jcenter()
    mavenCentral()
    // maven { url{"https://dl.bintray.com/kotlin/ktor"} }
    maven {
        setUrl("https://dl.bintray.com/kotlin/ktor")
    }
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}

dependencies {
    val ktorVersion = "0.9.2"
    compile(kotlin("stdlib-jdk8"))
    compile("org.apache.pdfbox:pdfbox:2.0.9")
    compile("org.bouncycastle:bcmail-jdk15on:1.59")
    compile("io.ktor:ktor-server-core:$ktorVersion")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("org.slf4j:slf4j-simple:1.7.25")
    testCompile("org.amshove.kluent:kluent:1.32")
    testCompile("junit:junit:4.12")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion")
}
