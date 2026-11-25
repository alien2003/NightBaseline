import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

// Pure Kotlin/JVM: no Android plugin and no androidx/Android artifacts (enforced below and by :verifyModuleGraph).
// Pattern taken from PulseStages :core-algo.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val verifyPureJvmRuntimeClasspath =
    tasks.register("verifyPureJvmRuntimeClasspath") {
        group = "verification"
        description = "Fails if :core-algo's runtimeClasspath contains androidx.*, com.android.* or com.google.android.* artifacts."

        val forbiddenGroupPrefixes = listOf("androidx.", "com.android.", "com.google.android.")
        val forbiddenExactGroups = listOf("androidx", "com.android", "com.google.android")
        val runtimeModules: Provider<List<String>> =
            configurations.runtimeClasspath
                .flatMap { it.incoming.resolutionResult.rootComponent }
                .map { root ->
                    val visited = mutableSetOf<ComponentIdentifier>()
                    val modules = sortedSetOf<String>()
                    val queue = ArrayDeque<ResolvedComponentResult>()
                    queue.add(root)
                    while (queue.isNotEmpty()) {
                        val component = queue.removeFirst()
                        if (!visited.add(component.id)) continue
                        val id = component.id
                        if (id is ModuleComponentIdentifier) modules += "${id.group}:${id.module}:${id.version}"
                        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
                    }
                    modules.toList()
                }
        inputs.property("runtimeModules", runtimeModules)
        doLast {
            val offending =
                runtimeModules.get().filter { coordinate ->
                    val group = coordinate.substringBefore(':')
                    group in forbiddenExactGroups || forbiddenGroupPrefixes.any { group.startsWith(it) }
                }
            if (offending.isNotEmpty()) {
                throw GradleException(":core-algo runtimeClasspath must be Android-free, found: ${offending.joinToString()}")
            }
        }
    }

tasks.test {
    useJUnitPlatform()
    dependsOn(verifyPureJvmRuntimeClasspath)
}
