plugins {
    java
    checkstyle
    jacoco
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.owasp.dependencycheck") version "12.1.0"
}

group = "com.example.ai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2025.1.1"
extra["springDocVersion"] = "3.0.2"

checkstyle {
    toolVersion = "13.3.0"
    configFile = file("config/checkstyle/google_checks.xml")
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:${property("springDocVersion")}")

    // HashiCorp Vault for secrets management
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Dev tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencyCheck {
    failBuildOnCVSS = 9.0f
    formats = listOf("HTML", "JSON")
    suppressionFile = file("config/dependency-check-suppressions.xml").absolutePath
    analyzers {
        assemblyEnabled = false
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            exclude(
                "**/client/**",
                "**/dto/**",
                "**/domain/**",
                "**/config/**"
            )
        }
    }))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            exclude(
                "**/client/**",
                "**/dto/**",
                "**/domain/**",
                "**/config/**"
            )
        }
    }))
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = 0.8.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
