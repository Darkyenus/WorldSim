@file:BuildDependencyRepository("jitpack", "https://jitpack.io/")
@file:BuildDependency("com.darkyen:ResourcePacker:2.5")

@file:Suppress("unused")
import com.darkyen.resourcepacker.PackingOperation
import org.jline.utils.OSUtils
import wemi.*
import wemi.boot.BuildDependency
import wemi.boot.BuildDependencyRepository
import wemi.boot.WemiRootFolder
import wemi.compile.KotlinCompilerFlags
import wemi.dependency.Jitpack
import wemi.util.*

val packResources by key<Path>("Pack resources yo")

val WorldSim by project {

	projectGroup set { "com.darkyen.worldSim" }
	projectName set { "WorldSim" }
	projectVersion set { "0.0" }

	compilerOptions[KotlinCompilerFlags.customFlags] = { it + "-Xinline-classes" }

	mainClass set { "com.darkyen.worldSim.Main" }

	extend(running) {
		if (OSUtils.IS_OSX) {
				runOptions add { "-XstartOnFirstThread" }
		}
		runOptions add { "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" }
	}

	repositories add { Jitpack }

	val gdxVersion = "1.9.10"
	libraryDependencies add { dependency("com.badlogicgames.gdx", "gdx", gdxVersion) }
	libraryDependencies add { dependency("com.badlogicgames.gdx", "gdx-backend-lwjgl3", gdxVersion) }
	libraryDependencies add { dependency("com.badlogicgames.gdx", "gdx-platform", gdxVersion, classifier = "natives-desktop") }
	libraryDependencies add { dependency("com.darkyen", "retinazer", /*"retinazer-0.2.5"*/"master-SNAPSHOT") }

	val assets = (WemiRootFolder / "assets")

	packResources set {
		val resources = (WemiRootFolder / "resources")
		expiresWith(resources)
		resourcePack(PackingOperation(resources.toFile(), assets.toFile()))
		assets
	}

	resources modify {
		it + FileSet(assets)
	}
}
