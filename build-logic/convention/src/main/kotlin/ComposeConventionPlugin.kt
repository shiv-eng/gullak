import com.android.build.api.dsl.CommonExtension
import com.gullak.buildlogic.libs
import com.gullak.buildlogic.library
import com.gullak.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Enables Compose on an Android module that already applies the application or library convention. */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-compose"))

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            dependencies {
                val bom = libs.library("androidx-compose-bom")
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("testImplementation", platform(bom))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
            }
        }
    }
}
