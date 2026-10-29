import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.gullak.buildlogic.libs
import com.gullak.buildlogic.library
import com.gullak.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room with KSP, Kotlin code generation and exported schemas under `schemas/`. */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("room"))
            pluginManager.apply(libs.pluginId("ksp"))

            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.library("androidx-room-runtime"))
                add("implementation", libs.library("androidx-room-ktx"))
                add("ksp", libs.library("androidx-room-compiler"))
                add("testImplementation", libs.library("androidx-room-testing"))
            }
        }
    }
}
