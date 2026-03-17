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

@UntrackedTask(because = "Relies on external state from services.gradle.org")
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

        if (currentRelease == null) {
            throw IllegalStateException("Failed to fetch current Gradle release version")
        }
        logger.lifecycle("Latest stable release: $currentRelease")
        logger.lifecycle("Latest release candidate: ${releaseCandidate ?: "none available"}")

        val selectedVersion = calculateHigherVersion(currentRelease, releaseCandidate)
        logger.lifecycle("Selected newer version: $selectedVersion")

        versionFile.get().asFile.writeText(selectedVersion)
        logger.lifecycle("Written version to file: $selectedVersion")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun fetchVersionFromEndpoint(urlString: String): String? =
        try {
            val jsonResponse = URI(urlString).toURL().readText()
            parseVersion(jsonResponse)
        } catch (e: Exception) {
            throw when (e) {
                is java.io.IOException -> IllegalStateException(
                    "Failed to fetch Gradle version from $urlString",
                    e
                )

                is JsonSyntaxException -> IllegalStateException(
                    "Failed to parse Gradle version response from $urlString",
                    e
                )

                is IllegalStateException -> IllegalStateException(
                    "Failed to fetch Gradle version from $urlString",
                    e
                )

                else -> e
            }
        }


    private fun parseVersion(jsonResponse: String): String? {
        val gson = Gson()
        val versionInfo = gson.fromJson(jsonResponse, GradleVersionInfo::class.java)
            ?: throw IllegalStateException("Failed to parse version info from response")
        return versionInfo.version
    }

    private fun calculateHigherVersion(releaseVersion: String, releaseCandidate: String?): String {
        if (releaseCandidate == null) {
            return releaseVersion
        }
        val releaseVersionVersion = VersionNumber.parse(releaseVersion)
        val releaseCandidateVersion = VersionNumber.parse(releaseCandidate)

        return when {
            releaseCandidateVersion > releaseVersionVersion -> releaseCandidate
            else -> releaseVersion
        }
    }

    private data class GradleVersionInfo(
        val version: String? = null,
    )
}
