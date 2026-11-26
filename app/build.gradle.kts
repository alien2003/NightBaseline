import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.ScopedArtifacts
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

// The NightBaseline app: UI (own design layer on Compose, ADR 0003), Hilt wiring and the Health Connect manifest.
// Build setup follows PulseStages :app (version from -P properties, env-var signing, offline Robolectric).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit)
}

// -PversionName=1.2.3 -PversionCode=1020399 (a release workflow derives both from the git tag).
val appVersionName: String = providers.gradleProperty("versionName").getOrElse("0.1.0-dev")
val appVersionCode: Int = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

// Release signing is configured only when all four variables are present; otherwise release stays unsigned.
val releaseKeystorePath = providers.environmentVariable("NIGHTBASELINE_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("NIGHTBASELINE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NIGHTBASELINE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NIGHTBASELINE_KEY_PASSWORD").orNull
val hasReleaseSigning =
    listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.alien2003.nightbaseline"
    compileSdk {
        version =
            release(libs.versions.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
            }
    }

    defaultConfig {
        applicationId = "io.github.alien2003.nightbaseline"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        // The on-device end-to-end suite (src/androidTest, tools/device-test/README.md) runs inside the INSTALLED
        // release app without reinstalling it. E2eRunner installs an in-memory WorkManager before the app's
        // Application.onCreate, so that no worker of the app runs in a test process.
        testInstrumentationRunner = "io.github.alien2003.nightbaseline.e2e.E2eRunner"
    }

    // The test APK is built for the "e2e" build type (below), never for debug: see the comment there.
    testBuildType = "e2e"

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release").apply {
                storeFile = file(checkNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Only the test APK of the on-device suite is built from this build type; its app APK is never installed.
        // The phone runs the owner's RELEASE build (minified, release key). An instrumentation must be signed with the
        // target's key, and a test APK compiled against a minified app cannot link (R8 renamed or removed what the
        // test libraries need). So the test APK carries its own unminified copy of every class of the app
        // (e2eAppClasses below): Android puts the test APK first on the app's class path, so inside the test process
        // the whole app runs from that copy, with the installed app's manifest, resources, uid, permissions and data.
        // The runner refuses to start unless the installed APK's dex equals the local release build of the same
        // sources and every resource id matches this build type (tools/device-test/run-device-tests.sh).
        create("e2e") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += "release"
        }
    }
    // The e2e build type is the release build without R8: it compiles the release-only sources.
    sourceSets.getByName("e2e") {
        kotlin.directories += "src/release/kotlin"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    testOptions {
        unitTests {
            // The screenshot tests run on Robolectric with the app's merged resources.
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

composeCompiler {
    // java.time values are immutable; without this every LocalDate / YearMonth parameter is unstable.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

// Compose compiler stability reports (docs/development.md, "Performance"): -PcomposeReports writes the per-class
// stability and per-composable skippability reports into build/compose-reports and the metrics into
// build/compose-metrics. Off by default; run the compile task with --rerun, or the build cache restores its classes
// without writing the reports.
if (providers.gradleProperty("composeReports").isPresent) {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose-reports")
        metricsDestination = layout.buildDirectory.dir("compose-metrics")
    }
}

/**
 * Hands the merged manifest of every variant (Variant API, SingleArtifact.MERGED_MANIFEST) to the unit-test JVM as
 * `-Dnightbaseline.mergedManifest.<variant>=<path>`. Declaring the files as inputs makes the test task depend on
 * manifest merging, so MergedManifestTest always checks the real merged output.
 */
abstract class MergedManifestsArgumentProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: ConfigurableFileCollection

    @get:Internal
    abstract val manifestPathsByVariant: MapProperty<String, String>

    override fun asArguments(): Iterable<String> =
        manifestPathsByVariant.get().map { (variant, path) -> "-Dnightbaseline.mergedManifest.$variant=$path" }
}

val mergedManifests = objects.newInstance(MergedManifestsArgumentProvider::class.java)

// Robolectric runs offline against the android-all jar resolved by Gradle (as in :data-local and PulseStages).
val robolectricAndroidAll = configurations.dependencyScope("robolectricAndroidAll")
val robolectricAndroidAllClasspath =
    configurations.resolvable("robolectricAndroidAllClasspath") {
        extendsFrom(robolectricAndroidAll.get())
        isTransitive = false
    }
val robolectricDependencyDir = layout.buildDirectory.dir("robolectric-deps")
val prepareRobolectricDependencies =
    tasks.register<Sync>("prepareRobolectricDependencies") {
        description = "Copies the android-all jar resolved by Gradle into the directory used by offline Robolectric."
        from(robolectricAndroidAllClasspath)
        into(robolectricDependencyDir)
    }

// The screenshot tests (synthetic data only) render on the JVM. Every test run renders into build/screenshots; with
// -PrecordScreenshots the images are written to docs/screenshots instead. Record only the README set there:
//   ./gradlew :app:testDebugUnitTest --tests '*ReadmeScreenshotsTest*' -PrecordScreenshots
val recordScreenshots = providers.gradleProperty("recordScreenshots").isPresent
val screenshotDir: String =
    if (recordScreenshots) {
        rootProject.layout.projectDirectory
            .dir("docs/screenshots")
            .asFile.absolutePath
    } else {
        layout.buildDirectory
            .dir("screenshots")
            .get()
            .asFile.absolutePath
    }
// ColorTokensTest compares the Kotlin colour tokens with the tables of docs/design/tokens.md.
val tokensDoc: File = rootProject.layout.projectDirectory.file("docs/design/tokens.md").asFile

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricDependencies)
    inputs.files(prepareRobolectricDependencies).withPropertyName("robolectricDependencies").withNormalizer(ClasspathNormalizer::class.java)
    inputs.files(tokensDoc).withPropertyName("tokensDoc").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDependencyDir.get().asFile.absolutePath)
    // PixelCopy through Robolectric's hardware renderer, so that RenderEffect-based blur can be captured.
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    systemProperty("nightbaseline.screenshots.dir", screenshotDir)
    systemProperty("nightbaseline.tokens.file", tokensDoc.absolutePath)
    if (recordScreenshots) outputs.upToDateWhen { false }
    // CI runners have less memory: .github/workflows/ci.yml passes -PtestMaxHeap.
    maxHeapSize = providers.gradleProperty("testMaxHeap").getOrElse("3g")
    // Robolectric's native graphics keeps memory outside the Java heap; CI starts a fresh JVM per test class.
    forkEvery = providers.gradleProperty("testForkEvery").map(String::toLong).getOrElse(0L)
}

/**
 * Merges every class of the e2e variant (the app's own classes, the Hilt-generated ones, R and all dependencies, before
 * dexing; Variant API, ScopedArtifacts.Scope.ALL) into one jar that is appended to the classes of its test APK. AGP
 * leaves the tested app's classes out of a test APK; this puts them back, so that the test APK links on its own
 * (tools/device-test/README.md, "How it runs against the installed release"). Java resources are not copied.
 */
abstract class E2eAppClassesTask : DefaultTask() {
    @get:InputFiles
    @get:Classpath
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:Classpath
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun merge() {
        val seen = HashSet<String>()
        JarOutputStream(output.get().asFile.outputStream().buffered()).use { out ->
            fun put(
                name: String,
                bytes: () -> ByteArray,
            ) {
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || !seen.add(name)) return
                out.putNextEntry(JarEntry(name).apply { time = 0 })
                out.write(bytes())
                out.closeEntry()
            }
            allDirectories.get().forEach { dir ->
                val root = dir.asFile
                root.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                    put(f.relativeTo(root).invariantSeparatorsPath) { f.readBytes() }
                }
            }
            allJars.get().forEach { jar ->
                ZipFile(jar.asFile).use { zip ->
                    zip.entries().toList().sortedBy { it.name }.filter { !it.isDirectory }.forEach { e ->
                        put(e.name) { zip.getInputStream(e).readBytes() }
                    }
                }
            }
        }
    }
}

/**
 * Adds the second instrumentation of the test APK, SelfE2eRunner targeting the test package itself (only
 * WriteDeleteE2e runs there, as the test package, with its own test-only WRITE_STEPS permission). It cannot be
 * declared in src/androidTest/AndroidManifest.xml: AGP's test manifest merger folds every `<instrumentation>` into the
 * one generated from testInstrumentationRunner.
 */
abstract class E2eSelfInstrumentationTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val testPackage: Property<String>

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun add() {
        val text = mergedManifest.get().asFile.readText()
        val at = text.indexOf("<application")
        check(at > 0 && text.indexOf("<application", at + 1) < 0) { "unexpected test manifest: one <application> expected" }
        val element =
            """
            |<instrumentation
            |        android:name="io.github.alien2003.nightbaseline.e2e.SelfE2eRunner"
            |        android:label="NightBaseline E2E (test package itself)"
            |        android:targetPackage="${testPackage.get()}" />
            |
            |
            """.trimMargin()
        updatedManifest.get().asFile.writeText(text.substring(0, at) + element + text.substring(at))
    }
}

androidComponents {
    onVariants(selector().withBuildType("e2e")) { variant ->
        val classes =
            tasks.register<E2eAppClassesTask>("e2eAppClasses${variant.name.replaceFirstChar(Char::uppercase)}") {
                output.set(layout.buildDirectory.file("intermediates/e2e-app-classes/${variant.name}/classes.jar"))
            }
        variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(classes).toGet(
            ScopedArtifact.CLASSES,
            E2eAppClassesTask::allJars,
            E2eAppClassesTask::allDirectories,
        )
        variant.androidTest?.artifacts?.forScope(ScopedArtifacts.Scope.PROJECT)?.use(classes)?.toAppend(
            ScopedArtifact.CLASSES,
            E2eAppClassesTask::output,
        )
        variant.androidTest?.let { test ->
            val self =
                tasks.register<E2eSelfInstrumentationTask>("e2eSelfInstrumentation${variant.name.replaceFirstChar(Char::uppercase)}") {
                    testPackage.set(test.applicationId)
                }
            test.artifacts
                .use(self)
                .wiredWithFiles(E2eSelfInstrumentationTask::mergedManifest, E2eSelfInstrumentationTask::updatedManifest)
                .toTransform(SingleArtifact.MERGED_MANIFEST)
        }
    }
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        mergedManifests.manifestFiles.from(manifest)
        mergedManifests.manifestPathsByVariant.put(variant.name, manifest.map { it.asFile.absolutePath })
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.configureTestTask { testTask ->
            testTask.jvmArgumentProviders.add(mergedManifests)
        }
    }
}

dependencies {
    implementation(project(":core-algo"))
    implementation(project(":data-local"))
    implementation(project(":data-hc"))
    implementation(project(":work"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.haze)
    implementation(libs.haze.blur)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(testFixtures(project(":data-local")))
    testImplementation(testFixtures(project(":data-hc")))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Screenshots run on Robolectric (JUnit 4 through the Vintage engine) with Compose's test rule.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    robolectricAndroidAll(libs.robolectric.android.all.instrumented)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // The on-device end-to-end suite (src/androidTest, JUnit 4): only the test APK carries these; nothing is added to
    // the app (its merged manifest, and MergedManifestTest, stay unchanged).
    androidTestImplementation(testFixtures(project(":data-local")))
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // The in-memory WorkManager of the suite's processes (E2eWorkManager); the app keeps its own WorkManager.
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Raises Compose's transitive espresso-core 3.5.0, which fails on Android 17 (see libs.versions.toml).
    androidTestImplementation(libs.androidx.test.espresso.core)
}
