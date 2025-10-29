import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.util.internal.VersionNumber
import java.net.URI

private const val HTTPS_SERVICES_GRADLE_ORG_VERSIONS = "https://services.gradle.org/versions"

@UntrackedTask(because = "when executed locally we want it to always fetch the data, in CI we don't care about caching this for a once a day execution")
abstract class FetchLatestGradleVersionTask : DefaultTask() {

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    init {
        group = "gradle wrapper"
        description = "Fetches the latest Gradle version (comparing stable release and RC)"
    }

    @TaskAction
    fun fetchVersion() {
        val currentRelease = fetchVersionFromEndpoint("$HTTPS_SERVICES_GRADLE_ORG_VERSIONS/current")
        val releaseCandidate = fetchVersionFromEndpoint("$HTTPS_SERVICES_GRADLE_ORG_VERSIONS/release-candidate")

        logger.lifecycle("Latest stable release: $currentRelease")
        logger.lifecycle("Latest release candidate: $releaseCandidate")

        val selectedVersion = calculateHigherVersion(currentRelease, releaseCandidate)
        logger.lifecycle("Selected newer version: $selectedVersion")

        versionFile.get().asFile.writeText(selectedVersion)
        logger.lifecycle("Written version to file: $selectedVersion")
    }

    private fun fetchVersionFromEndpoint(urlString: String): String {
        return try {
            val jsonResponse = URI(urlString).toURL().readText()
            parseVersion(jsonResponse)
        } catch (e: java.io.IOException) {
            throw IllegalStateException(
                "Failed to fetch Gradle version from $urlString",
                e
            )
        } catch (e: JsonSyntaxException) {
            throw IllegalStateException(
                "Failed to parse Gradle version response from $urlString",
                e
            )
        }
    }

    private val gson = Gson()

    private fun parseVersion(jsonResponse: String): String {
        val versionInfo = gson.fromJson(jsonResponse, GradleVersionInfo::class.java)
        if (versionInfo == null || versionInfo.version == null) {
            throw IllegalStateException("Failed to parse version info from response")
        }
        return versionInfo.version
    }

    private fun calculateHigherVersion(version1: String, version2: String): String {
        val v1 = VersionNumber.parse(version1)
        val v2 = VersionNumber.parse(version2)

        return when {
            v2 > v1 -> version2
            else -> version1
        }
    }

    private data class GradleVersionInfo(
        val version: String? = null,
    )
}
