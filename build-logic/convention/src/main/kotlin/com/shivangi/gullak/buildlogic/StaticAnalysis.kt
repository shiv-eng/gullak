package com.shivangi.gullak.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Applies detekt and ktlint with the shared configuration in `config/detekt`. */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply(libs.pluginId("detekt"))
    pluginManager.apply(libs.pluginId("ktlint"))

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    // detekt 1.23 is built against an older Kotlin compiler than the project uses;
    // keep detekt's own classpath on the version it supports (documented detekt workaround).
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(getSupportedKotlinVersion())
            }
        }
    }

    extensions.configure<KtlintExtension> {
        version.set(libs.versionOf("ktlint"))
        filter {
            exclude { it.file.path.contains("${java.io.File.separator}build${java.io.File.separator}") }
        }
    }

    dependencies {
        add("detektPlugins", libs.library("detekt-compose-rules"))
    }
}
