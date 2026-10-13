plugins {
    id("java")
}

group = "org.labs"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// ./gradlew benchmark --args="double-buffer 1 2 4 8 10"
tasks.register<JavaExec>("benchmark") {
    group = "lab"
    description = "Замер пропускной способности одного коллектора (отдельная JVM на вызов)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.labs.benchmark.Benchmark"
}

// ./gradlew consistency --args="striped-lock"
tasks.register<JavaExec>("consistency") {
    group = "lab"
    description = "Стресс-тест согласованности снимков sum(buckets) == count"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.labs.benchmark.ConsistencyCheck"
}
