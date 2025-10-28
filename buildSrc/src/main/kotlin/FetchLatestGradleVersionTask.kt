import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.internal.VersionNumber
import java.net.URL

private const val HTTPS_SERVICES_GRADLE_ORG_VERSIONS = "https://services.gradle.org/versions"

abstract class FetchLatestGradleVersionTask : DefaultTask() {

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    init {
        group = "gradle wrapper"
        description = "Fetches the latest Gradle version (comparing stable release and RC)"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun fetchVersion() {
        val currentRelease = fetchVersionFromEndpoint("$HTTPS_SERVICES_GRADLE_ORG_VERSIONS/current")
        val releaseCandidate = fetchVersionFromEndpoint("$HTTPS_SERVICES_GRADLE_ORG_VERSIONS/release-candidate")

        logger.lifecycle("Latest stable release: $currentRelease")
        logger.lifecycle("Latest release candidate: ${releaseCandidate ?: "none"}")

        val selectedVersion = when {
            currentRelease == null -> {
                throw IllegalStateException("Failed to fetch current Gradle release version")
            }

            releaseCandidate == null -> {
                logger.lifecycle("No RC available, using stable release: $currentRelease")
                currentRelease
            }

            else -> {
                val newerVersion = compareVersions(currentRelease, releaseCandidate)
                logger.lifecycle("Selected newer version: $newerVersion")
                newerVersion
            }
        }

        versionFile.get().asFile.writeText(selectedVersion)
        logger.lifecycle("Written version to file: $selectedVersion")
    }

    private fun fetchVersionFromEndpoint(urlString: String): String? {
        return try {
            val jsonResponse = URL(urlString).readText()
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

    private fun parseVersion(jsonResponse: String): String? {
        val gson = Gson()
        val versionInfo = gson.fromJson(jsonResponse, GradleVersionInfo::class.java)
        return versionInfo?.version
    }

    private fun compareVersions(version1: String, version2: String): String {
        val v1 = VersionNumber.parse(version1)
        val v2 = VersionNumber.parse(version2)

        return when {
            v1 > v2 -> {
                logger.lifecycle("$version1 is newer than $version2")
                version1
            }

            v2 > v1 -> {
                logger.lifecycle("$version2 is newer than $version1")
                version2
            }

            else -> {
                logger.lifecycle("Versions are equal, using first: $version1")
                version1
            }
        }
    }

    private data class GradleVersionInfo(
        val version: String? = null,
        val buildTime: String? = null,
        val current: Boolean? = null,
        val snapshot: Boolean? = null,
        val nightly: Boolean? = null,
        val releaseNightly: Boolean? = null,
        val activeRc: Boolean? = null,
        val rcFor: String? = null,
        val milestoneFor: String? = null,
        val broken: Boolean? = null,
        val downloadUrl: String? = null,
        val checksumUrl: String? = null,
        val wrapperChecksumUrl: String? = null
    )
}
