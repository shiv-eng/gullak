import com.android.build.api.dsl.LibraryExtension
import com.shivangi.gullak.buildlogic.configureKotlinAndroid
import com.shivangi.gullak.buildlogic.configureStaticAnalysis
import com.shivangi.gullak.buildlogic.libs
import com.shivangi.gullak.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-library"))
            pluginManager.apply("gullak.test")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            configureStaticAnalysis()
        }
    }
}
