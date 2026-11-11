plugins { java }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    implementation(project(":engine"))
    implementation("org.hdrhistogram:HdrHistogram:2.2.2")   
}

tasks.register<JavaExec>("runBench") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("engine.bench.OrderBookBenchmark")
    jvmArgs("-Xms1g", "-Xmx1g", "-Xlog:gc:file=build/gc.log")
}