import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.internal.os.OperatingSystem

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.10"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()
val imgui_version = providers.gradleProperty("imgui_version").get()

base {
	archivesName = providers.gradleProperty("archives_base_name")
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	maven(url = "https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("kittycat") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}

	accessWidenerPath = file("src/client/resources/kittycat.accesswidener")

	runConfigs.named("client") {
		isIdeConfigGenerated = true
		vmArgs.addAll(
			arrayOf(
				"-Ddevauth.enabled=true",
				"-Ddevauth.account=main"
			)
		)
	}
}

fabricApi {
	configureDataGeneration {
		client = true
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

	// NanoVG for custom font rendering
	modImplementation("org.lwjgl:lwjgl-nanovg:3.3.3")
	include("org.lwjgl:lwjgl-nanovg:3.3.3")
	listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
		modImplementation("org.lwjgl:lwjgl-nanovg:3.3.3:natives-$os")
		include("org.lwjgl:lwjgl-nanovg:3.3.3:natives-$os")
	}

	val includeImplementation = fun(str: String) {
		implementation(str)
		include(str)
	}

	includeImplementation("org.reflections:reflections:0.10.2")
	includeImplementation("org.javassist:javassist:3.29.2-GA")

	arrayOf(
		"io.github.spair:imgui-java-binding:$imgui_version",
		"io.github.spair:imgui-java-lwjgl3:$imgui_version",
		"io.github.spair:imgui-java-natives-windows:$imgui_version",
		"io.github.spair:imgui-java-natives-linux:$imgui_version",
		"io.github.spair:imgui-java-natives-macos:$imgui_version"
	).forEach(includeImplementation)
}

tasks.processResources {
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	inputs.property("archivesName", base.archivesName)

	from("LICENSE") {
		rename { "${it}_${base.archivesName.get()}" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			artifactId = base.archivesName.get()
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
