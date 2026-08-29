plugins {
    application
    java
    id ("com.github.johnrengelman.shadow") version "8.1.1"
}

application.mainClass = "org.ToastiCodingStuff.Sloth.Sloth"
group = "org.ToastiCodingStuff"
version = "1.0"

val jdaVersion = "6.2.0" //

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.json:json:20240303")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    sourceCompatibility = "14"
}

tasks.jar {
    manifest.attributes["Main-Class"] = application.mainClass
}

// Holt vor jedem Bot-Start automatisch die neueste Version des Haupt-Branches.
// Deaktivieren mit:  ./gradlew run -PskipUpdate    oder  SLOTH_SKIP_UPDATE=1
val updateMain = tasks.register<Exec>("updateMain") {
    group = "git"
    description = "Pullt die neueste Version des Haupt-Branches."

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    if (isWindows) {
        commandLine(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", file("scripts/update-main.ps1").absolutePath
        )
    } else {
        commandLine("bash", file("scripts/update-main.sh").absolutePath)
    }

    // Ein fehlgeschlagener Pull (offline, uncommittete Aenderungen, Merge-Konflikt)
    // darf den Bot-Start nicht verhindern - es gibt nur eine Warnung.
    isIgnoreExitValue = true

    onlyIf {
        !project.hasProperty("skipUpdate") && System.getenv("SLOTH_SKIP_UPDATE") != "1"
    }

    doLast {
        val exit = executionResult.get().exitValue
        if (exit != 0) {
            logger.lifecycle("!! update-main fehlgeschlagen (Exit $exit) - der Bot startet mit dem aktuellen lokalen Stand.")
        }
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(updateMain)
}

// Erst pullen, dann kompilieren - sonst laeuft der Bot mit altem Code weiter.
tasks.named("compileJava") {
    mustRunAfter(updateMain)
}
