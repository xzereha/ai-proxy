plugins {
	java
    checkstyle
    jacoco
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
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
extra["bucket4jVersion"] = "8.7.0"

checkstyle {
    toolVersion = "13.3.0"
    configFile = file("config/checkstyle/google_checks.xml")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:${property("springDocVersion")}")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("com.bucket4j:bucket4j-core:${property("bucket4jVersion")}")
	implementation("io.github.resilience4j:resilience4j-circuitbreaker")
	implementation("io.github.resilience4j:resilience4j-retry")
	implementation("io.github.resilience4j:resilience4j-reactor")
	implementation("org.springframework.retry:spring-retry")
	implementation("org.springframework:spring-aspects")
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
				"**/domain/**"
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
				"**/domain/**"
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
