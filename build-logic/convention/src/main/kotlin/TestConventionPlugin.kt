import com.shivangi.gullak.buildlogic.libs
import com.shivangi.gullak.buildlogic.library
import com.shivangi.gullak.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Unit-test dependencies shared by every module, plus Kover for coverage reports. */
class TestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kover"))

            dependencies {
                add("testImplementation", libs.library("junit4"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
            }

            tasks.withType<Test>().configureEach {
                // Modules are scaffolded before their tests exist; an empty module must not fail `check`.
                failOnNoDiscoveredTests.set(false)
                testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}
