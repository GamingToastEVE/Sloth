plugins {
    application
    java
    id ("com.github.johnrengelman.shadow") version "8.1.1"
}

application.mainClass = "org.ToastiCodingStuff.Sloth.Sloth"
group = "org.ToastiCodingStuff"
version = "1.0"

val jdaVersion = "6.1.3" //

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.json:json:20240303")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    sourceCompatibility = "14"
}

tasks.jar {
    manifest.attributes["Main-Class"] = application.mainClass
}