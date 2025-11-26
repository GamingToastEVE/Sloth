plugins {
    application
    java
    id ("com.github.johnrengelman.shadow") version "8.1.1"
}

application.mainClass = "org.ToastiCodingStuff.Sloth.Sloth"
group = "org.ToastiCodingStuff"
version = "1.0"

val jdaVersion = "5.0.0-beta.24" //

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    // Set this to the version of java you want to use,
    // the minimum required for JDA is 1.8
    sourceCompatibility = "11"
}

tasks.jar {
    manifest.attributes["Main-Class"] = application.mainClass
}