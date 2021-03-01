import org.gradle.internal.impldep.bsh.commands.dir
import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    application
    kotlin("jvm") version "1.4.31"
    id("com.lovelysystems.gradle") version ("1.3.2")
}

lovely {
    gitProject()
    dockerProject("lovelysystems/signpdf")
    with(dockerFiles) {
        from(tasks["distTar"].outputs)
        from("docker/Dockerfile")
    }
}

group = "com.lovelysystems"

application {
    mainClassName = "io.ktor.server.netty.DevelopmentEngine"
}

repositories {
    jcenter()
    mavenCentral()
    maven {
        setUrl("https://dl.bintray.com/kotlin/ktor")
    }
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}

dependencies {
    val ktorVersion = "1.5.2"
    compile(kotlin("stdlib-jdk8"))
    compile("org.apache.pdfbox:pdfbox:2.0.9")
    compile("org.bouncycastle:bcmail-jdk15on:1.59")
    compile("io.ktor:ktor-server-core:$ktorVersion")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("ch.qos.logback:logback-classic:1.2.1")
    compile("com.google.guava:guava:27.1-jre")
    compile("com.sun.xml.ws:jaxws-ri:2.3.2")
    testCompile("org.amshove.kluent:kluent:1.32")
    testCompile("junit:junit:4.12")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion")
}
