plugins { java }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }


val aeronVersion = "1.46.0"

dependencies {
    implementation(project(":engine"))
    implementation(project(":codec"))
    implementation("io.aeron:aeron-driver:$aeronVersion")
    implementation("io.aeron:aeron-client:$aeronVersion")
    implementation("io.aeron:aeron-archive:$aeronVersion")
    implementation("io.aeron:aeron-cluster:$aeronVersion")
    implementation("io.aeron:aeron-samples:$aeronVersion")   
}

val jvmFlags = listOf(
    "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
)

tasks.register<JavaExec>("runNode") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("engine.cluster.ClusterNode")
    jvmArgs(jvmFlags)
    systemProperty("engine.log", (findProperty("log") ?: "false").toString())
    args = listOf((findProperty("node") ?: "0").toString())
}

// Asks a node to take a snapshot. Use the node that is currently the leader.
tasks.register<JavaExec>("snapshot") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.aeron.cluster.ClusterTool")
    jvmArgs(jvmFlags)
    args = listOf("cluster-data/node${findProperty("node") ?: "0"}/cluster", "snapshot")
}

tasks.register<JavaExec>("runClient") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("engine.cluster.OrderClient")
    jvmArgs(jvmFlags)
}