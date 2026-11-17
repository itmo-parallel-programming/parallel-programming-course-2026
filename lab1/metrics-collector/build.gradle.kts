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

// Все параметры экспериментов задаются в org.labs.Main перед запуском.
tasks.register<JavaExec>("run") {
    group = "lab"
    description = "Запуск режима, выбранного в Main.java"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.labs.Main"
}

// ./gradlew benchmark
tasks.register<JavaExec>("benchmark") {
    group = "lab"
    description = "Замер пропускной способности одного коллектора (отдельная JVM на вызов)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.labs.benchmark.Benchmark"
}

// ./gradlew consistency
tasks.register<JavaExec>("consistency") {
    group = "lab"
    description = "Стресс-тест согласованности снимков sum(buckets) == count"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.labs.benchmark.ConsistencyCheck"
}

tasks.register<JavaExec>("runAll") {
    group = "lab"
    description = "Полный прогон с настройками Main.java и отдельной JVM на коллектор"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.labs.Main\$AllRuns"
}
