import com.shivangi.gullak.buildlogic.libs
import com.shivangi.gullak.buildlogic.library
import com.shivangi.gullak.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: an Android library with Compose, Hilt and type-safe navigation.
 * Features see only the domain, the model and the design system, never each other or the data layer.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("gullak.android.library")
            pluginManager.apply("gullak.compose")
            pluginManager.apply("gullak.hilt")
            pluginManager.apply(libs.pluginId("kotlin-serialization"))

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))

                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))

                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
