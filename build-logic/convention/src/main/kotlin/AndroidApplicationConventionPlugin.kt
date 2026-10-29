import com.android.build.api.dsl.ApplicationExtension
import com.gullak.buildlogic.configureKotlinAndroid
import com.gullak.buildlogic.configureStaticAnalysis
import com.gullak.buildlogic.libs
import com.gullak.buildlogic.pluginId
import com.gullak.buildlogic.versionOf
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-application"))
            pluginManager.apply("gullak.test")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.versionOf("targetSdk").toInt()
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            configureStaticAnalysis()
        }
    }
}
