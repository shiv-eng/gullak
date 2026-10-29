package com.gullak.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import java.util.Properties

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

internal fun VersionCatalog.versionOf(alias: String): String =
    findVersion(alias).get().requiredVersion

internal fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).get().get().pluginId

/** Reads `local.properties` from the root project; returns an empty set if the file is absent. */
fun Project.localProperties(): Properties {
    val file = rootProject.file("local.properties")
    return Properties().apply {
        if (file.exists()) file.inputStream().use(::load)
    }
}
