plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Bytecode :app can load and Android's D8 provably accepts;
// the toolchain stays 25, only the emitted class-file level is pinned.
tasks.compileJava {
    options.release = 21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.1")
    implementation("org.yaml:snakeyaml:2.7")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("org.tinylog:tinylog-api:2.8.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.tinylog:tinylog-impl:2.8.0")
}

tasks.test {
    useJUnitPlatform()
}
