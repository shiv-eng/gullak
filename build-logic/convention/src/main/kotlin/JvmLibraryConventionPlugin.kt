import com.shivangi.gullak.buildlogic.configureKotlin
import com.shivangi.gullak.buildlogic.configureStaticAnalysis
import com.shivangi.gullak.buildlogic.libs
import com.shivangi.gullak.buildlogic.pluginId
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/** A pure Kotlin module with no Android dependencies (domain and model). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply("gullak.test")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            configureKotlin()
            configureStaticAnalysis()
        }
    }
}
