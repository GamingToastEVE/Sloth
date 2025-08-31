plugins {
    application
    java
}

application.mainClass = "org.ToastiCodingStuff.Delta.CombinedApplication" //
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
    
    // Web framework dependencies
    implementation("org.springframework.boot:spring-boot-starter-web:3.1.5")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf:3.1.5")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client:3.1.5")
    implementation("org.springframework.boot:spring-boot-starter-security:3.1.5")
    implementation("org.springframework.session:spring-session-core:3.1.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    // Set this to the version of java you want to use,
    // the minimum required for JDA is 1.8, Spring Boot 3.x requires 17
    sourceCompatibility = "17"
}