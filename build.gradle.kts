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

application {
    mainClass.set("io.ktor.server.netty.DevelopmentEngine")
}

repositories {
    jcenter()
    mavenCentral()
    maven {
        setUrl("https://dl.bintray.com/kotlin/ktor")
    }
}

dependencies {
    val ktorVersion = "1.5.2"
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.apache.pdfbox:pdfbox:2.0.9")
    implementation("org.bouncycastle:bcmail-jdk15on:1.59")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.2.1")
    implementation("com.google.guava:guava:27.1-jre")
    implementation("com.sun.xml.ws:jaxws-ri:2.3.2")
    testImplementation("org.amshove.kluent:kluent:1.32")
    testImplementation("junit:junit:4.12")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
