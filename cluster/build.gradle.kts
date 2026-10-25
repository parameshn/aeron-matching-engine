plugins { java }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

// Use the latest release from Maven Central (group io.aeron). I haven't verified the current number.
val aeronVersion = "1.46.0"

dependencies {
    implementation(project(":engine"))
    implementation(project(":codec"))
    implementation("io.aeron:aeron-driver:$aeronVersion")
    implementation("io.aeron:aeron-client:$aeronVersion")
    implementation("io.aeron:aeron-archive:$aeronVersion")
    implementation("io.aeron:aeron-cluster:$aeronVersion")
    implementation("io.aeron:aeron-samples:$aeronVersion")   // for the ClusterConfig helper
}

val jvmFlags = listOf(
    "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
)

tasks.register<JavaExec>("runNode") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("engine.cluster.ClusterNode")
    jvmArgs(jvmFlags)
    args = listOf((findProperty("node") ?: "0").toString())
}

tasks.register<JavaExec>("runClient") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("engine.cluster.OrderClient")
    jvmArgs(jvmFlags)
}