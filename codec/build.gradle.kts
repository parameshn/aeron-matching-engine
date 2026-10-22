plugins {
    java
}

group = "engine"
version = "2.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val sbeVersion = "1.35.0"
val sbeGen = configurations.create("sbeGen")

val agronaJvmArgs = listOf(
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)

dependencies {
    sbeGen("uk.co.real-logic:sbe-tool:$sbeVersion")

    implementation("org.agrona:agrona:2.2.0")
    implementation(project(":engine"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generatedDir = layout.buildDirectory.dir("generated/sbe")

val generateCodecs = tasks.register<JavaExec>("generateCodecs") {
    val schema = file("src/main/resources/messages.xml")

    inputs.file(schema)
    outputs.dir(generatedDir)

    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    classpath = sbeGen

    // Run the generator on the same JDK as compile and test
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))

    jvmArgs(agronaJvmArgs)

    systemProperty("sbe.output.dir", generatedDir.get().asFile.absolutePath)
    systemProperty("sbe.target.language", "Java")

    args(schema.absolutePath)
}

sourceSets {
    main {
        java.srcDir(generatedDir)
    }
}

tasks.compileJava {
    dependsOn(generateCodecs)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(agronaJvmArgs)
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}