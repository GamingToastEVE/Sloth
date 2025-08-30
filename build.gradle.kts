plugins {
    application
    java
}

application.mainClass = "org.ToastiCodingStuff.Delta.Delta" //
group = "org.ToastiCodingStuff"
version = "1.0"

val jdaVersion = "5.0.0-beta.24" //

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    // Set this to the version of java you want to use,
    // the minimum required for JDA is 1.8
    sourceCompatibility = "1.8"
}