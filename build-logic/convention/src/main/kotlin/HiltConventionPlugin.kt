import com.gullak.buildlogic.libs
import com.gullak.buildlogic.library
import com.gullak.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt with KSP for an Android module. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ksp"))
            pluginManager.apply(libs.pluginId("hilt"))

            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
            }
        }
    }
}
