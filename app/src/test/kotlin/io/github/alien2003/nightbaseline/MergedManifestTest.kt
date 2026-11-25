package io.github.alien2003.nightbaseline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the MERGED manifests (debug and release) produced by AGP; the paths come from app/build.gradle.kts
 * (Variant API, SingleArtifact.MERGED_MANIFEST), so the test task depends on manifest merging. Pattern from
 * PulseStages.
 */
class MergedManifestTest {
    private val mergedManifests: Map<String, File> =
        System
            .getProperties()
            .stringPropertyNames()
            .filter { it.startsWith(PROPERTY_PREFIX) }
            .associate { it.removePrefix(PROPERTY_PREFIX) to File(checkNotNull(System.getProperty(it))) }

    @Test
    fun `merged manifests are provided for debug and release`() {
        assertTrue(mergedManifests.keys.containsAll(REQUIRED_VARIANTS), "expected $REQUIRED_VARIANTS, got ${mergedManifests.keys}")
        mergedManifests.forEach { (variant, file) -> assertTrue(file.isFile, "$variant merged manifest missing: $file") }
    }

    @Test
    fun `merged manifests do not request the INTERNET permission`() {
        forEachManifest { variant, manifest ->
            val requested = manifest.requestedPermissions()
            assertFalse(INTERNET in requested, "$variant merged manifest requests $INTERNET: $requested")
            assertFalse(ACCESS_NETWORK_STATE in requested, "$variant merged manifest requests $ACCESS_NETWORK_STATE")
        }
    }

    @Test
    fun `exactly the read permissions the app needs, and no write permission`() {
        forEachManifest { variant, manifest ->
            val health = manifest.requestedPermissions().filter { it.startsWith(HEALTH_PREFIX) }.toSet()
            assertEquals(HEALTH_READ, health, "$variant health permissions")
            assertTrue(POST_NOTIFICATIONS in manifest.requestedPermissions(), "$variant lacks $POST_NOTIFICATIONS")
            manifest
                .elements("uses-permission")
                .filter { it.androidAttr("name").startsWith(HEALTH_PREFIX) }
                .forEach { assertEquals("manifest", it.parentNode.nodeName, "$variant: ${it.androidAttr("name")} outside <manifest>") }
        }
    }

    @Test
    fun `Health Connect and the default source are visible through queries`() {
        forEachManifest { variant, manifest ->
            val packages = manifest.elements("queries").flatMap { it.children("package") }.map { it.androidAttr("name") }
            assertTrue(HEALTH_CONNECT_PACKAGE in packages, "$variant: $packages")
            assertTrue(OHEALTH_PACKAGE in packages, "$variant: $packages")
            assertFalse(QUERY_ALL_PACKAGES in manifest.requestedPermissions(), "$variant requests $QUERY_ALL_PACKAGES")
        }
    }

    @Test
    fun `rationale activity and the Android 14+ alias are declared`() {
        forEachManifest { variant, manifest ->
            val activity = manifest.elements("activity").firstOrNull { qualified(it.androidAttr("name")) == RATIONALE_ACTIVITY }
            assertNotNull(activity, "$variant: $RATIONALE_ACTIVITY missing")
            checkNotNull(activity)
            assertEquals("true", activity.androidAttr("exported"), variant)
            assertTrue(SHOW_RATIONALE in activity.intentActions(), "$variant: rationale activity lacks $SHOW_RATIONALE")
            val alias =
                manifest.elements("activity-alias").firstOrNull { qualified(it.androidAttr("targetActivity")) == RATIONALE_ACTIVITY }
            assertNotNull(alias, "$variant: no activity-alias targets $RATIONALE_ACTIVITY")
            checkNotNull(alias)
            assertEquals(START_VIEW_PERMISSION_USAGE, alias.androidAttr("permission"), variant)
            assertTrue(VIEW_PERMISSION_USAGE in alias.intentActions(), variant)
            assertTrue(HEALTH_PERMISSIONS_CATEGORY in alias.intentCategories(), variant)
        }
    }

    @Test
    fun `main activity is the single launcher, resizable, without orientation lock`() {
        forEachManifest { variant, manifest ->
            val activity = manifest.elements("activity").first { qualified(it.androidAttr("name")) == MAIN_ACTIVITY }
            assertEquals("", activity.androidAttr("screenOrientation"), "$variant: orientation must not be locked")
            assertTrue(activity.androidAttr("resizeableActivity") != "false", variant)
            assertTrue("smallestScreenSize" in activity.androidAttr("configChanges"), variant)
            val launchers =
                (manifest.elements("activity") + manifest.elements("activity-alias"))
                    .filter { it.isLauncherEntry() }
                    .map { qualified(it.androidAttr("name")) }
            assertEquals(listOf(MAIN_ACTIVITY), launchers, "$variant launcher entries")
        }
    }

    private fun forEachManifest(check: (String, Document) -> Unit) {
        assertTrue(mergedManifests.isNotEmpty(), "no merged manifests passed to the test")
        mergedManifests.forEach { (variant, file) -> check(variant, parse(file)) }
    }

    private fun parse(file: File): Document =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun Document.elements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.children(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun Element.intentActions(): Set<String> =
        children("intent-filter").flatMap { filter -> filter.children("action").map { it.androidAttr("name") } }.toSet()

    private fun Element.intentCategories(): Set<String> =
        children("intent-filter").flatMap { filter -> filter.children("category").map { it.androidAttr("name") } }.toSet()

    private fun Element.isLauncherEntry(): Boolean =
        children("intent-filter").any { filter ->
            ACTION_MAIN in filter.children("action").map { it.androidAttr("name") } &&
                CATEGORY_LAUNCHER in filter.children("category").map { it.androidAttr("name") }
        }

    private fun Document.requestedPermissions(): Set<String> =
        (elements("uses-permission") + elements("uses-permission-sdk-23")).map { it.androidAttr("name") }.toSet()

    private fun qualified(className: String): String = if (className.startsWith(".")) APPLICATION_ID + className else className

    private companion object {
        const val PROPERTY_PREFIX = "nightbaseline.mergedManifest."
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val APPLICATION_ID = "io.github.alien2003.nightbaseline"
        const val INTERNET = "android.permission.INTERNET"
        const val ACCESS_NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE"
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        const val HEALTH_PREFIX = "android.permission.health."
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        const val OHEALTH_PACKAGE = "com.heytap.health.international"
        const val MAIN_ACTIVITY = "$APPLICATION_ID.MainActivity"
        const val RATIONALE_ACTIVITY = "$APPLICATION_ID.PermissionsRationaleActivity"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
        const val SHOW_RATIONALE = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
        const val VIEW_PERMISSION_USAGE = "android.intent.action.VIEW_PERMISSION_USAGE"
        const val HEALTH_PERMISSIONS_CATEGORY = "android.intent.category.HEALTH_PERMISSIONS"
        const val START_VIEW_PERMISSION_USAGE = "android.permission.START_VIEW_PERMISSION_USAGE"
        val REQUIRED_VARIANTS = setOf("debug", "release")
        val HEALTH_READ =
            setOf(
                "READ_SLEEP",
                "READ_HEART_RATE",
                "READ_OXYGEN_SATURATION",
                "READ_RESPIRATORY_RATE",
                "READ_HEART_RATE_VARIABILITY",
                "READ_SKIN_TEMPERATURE",
                "READ_STEPS",
                "READ_HEALTH_DATA_HISTORY",
                "READ_HEALTH_DATA_IN_BACKGROUND",
            ).map { HEALTH_PREFIX + it }.toSet()
    }
}
