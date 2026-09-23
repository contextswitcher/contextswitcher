// Root project: cross-cutting configuration. Application code lives in :app.

import org.itsallcode.openfasttrace.api.DetailsSectionDisplay
import org.itsallcode.openfasttrace.api.report.ReportVerbosity
import org.itsallcode.openfasttrace.gradle.task.CollectTask
import org.itsallcode.openfasttrace.gradle.task.TraceTask

plugins {
    id("org.itsallcode.openfasttrace") version "3.2.0"
}

requirementTracing {
    inputDirectories = files("docs/requirements", "app/src", "core/src", "extension", "android/src")
}

// Platform targets of the requirement tags (docs/requirements/README.md,
// "Platform targets"): a `req` holds per target one of `<t>`, `<t>_later`,
// `<t>_no`, or none (undecided); a `dsn` names the targets it implements.
val requirementTargets = listOf("windows", "linux", "android")

data class SpecItem(val id: String, val title: String, val location: String, val tags: List<String>)

object SpecItems {
    /// Every `feat`/`req`/`dsn` item of the requirement files with its `Tags:` values.
    fun read(dir: File): List<SpecItem> = dir.listFiles { f -> f.name.endsWith(".md") }!!.sorted().flatMap { file ->
        val lines = file.readLines()
        val starts = lines.indices.filter { Regex("^`(feat|req|dsn)~[a-z0-9-]+~\\d+`$").matches(lines[it]) }
        starts.mapIndexed { n, start ->
            val end = starts.getOrElse(n + 1) { lines.size }
            val tagLine = (start until end).firstOrNull { lines[it].startsWith("Tags:") }
            SpecItem(
                id = lines[start].trim('`'),
                title = (start - 1 downTo 0).map { lines[it] }.firstOrNull { it.startsWith("#") }?.trimStart('#', ' ') ?: "",
                location = "${file.name}:${(tagLine ?: start) + 1}",
                tags = tagLine?.let { lines[it].removePrefix("Tags:").split(",").map(String::trim).filter(String::isNotEmpty) } ?: emptyList(),
            )
        }
    }

    fun states(target: String) = setOf(target, "${target}_later", "${target}_no")
}

abstract class CheckRequirementTags : DefaultTask() {
    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    @get:Input
    abstract val targets: ListProperty<String>

    @TaskAction
    fun check() {
        val targets = targets.get()
        val problems = mutableListOf<String>()
        for (item in SpecItems.read(specDir.get().asFile)) {
            for (tag in item.tags) {
                // OpenFastTrace silently drops a whole Tags: line holding a hyphenated tag.
                if (!Regex("[A-Za-z][A-Za-z0-9_]*").matches(tag)) {
                    problems += "${item.location} ${item.id}: tag '$tag' is not a plain word (use _ instead of -)"
                } else if (targets.none { tag in SpecItems.states(it) }) {
                    problems += "${item.location} ${item.id}: unknown tag '$tag' (targets: $targets)"
                }
            }
            when {
                item.id.startsWith("dsn~") && item.tags.isEmpty() ->
                    problems += "${item.location} ${item.id}: a design names the targets it implements (Tags:)"
                item.id.startsWith("dsn~") && item.tags.any { it.endsWith("_later") || it.endsWith("_no") } ->
                    problems += "${item.location} ${item.id}: _later/_no are decisions for requirements, not designs"
                item.id.startsWith("req~") -> for (target in targets) {
                    val states = item.tags.filter { it in SpecItems.states(target) }
                    if (states.size > 1) problems += "${item.location} ${item.id}: more than one state for $target: $states"
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Requirement tag problems:\n" + problems.joinToString("\n"))
        }
    }
}

abstract class RequirementReport : DefaultTask() {
    @get:Internal
    abstract val specDir: DirectoryProperty

    @get:Input
    abstract val target: Property<String>

    @get:Internal
    abstract val wantedNowTrace: RegularFileProperty

    @get:Internal
    abstract val wantedLaterTrace: RegularFileProperty

    @TaskAction
    fun report() {
        val target = target.get()
        val items = SpecItems.read(specDir.get().asFile).associateBy { it.id }
        fun gaps(trace: RegularFileProperty) = trace.get().asFile.readLines()
            .mapNotNull { Regex("""^not ok .*\] (req~[a-z0-9-]+~\d+)""").find(it)?.groupValues?.get(1) }
        fun print(heading: String, ids: List<String>) {
            println("$heading (${ids.size}):")
            ids.forEach { println("  $it  ${items[it]?.title ?: ""}") }
        }
        print("Wanted on $target, no $target design yet", gaps(wantedNowTrace))
        print("Wanted on $target later, no $target design yet", gaps(wantedLaterTrace))
        print("Undecided for $target", items.values
            .filter { item -> item.id.startsWith("req~") && item.tags.none { it in SpecItems.states(target) } }
            .map { it.id })
    }
}

val checkRequirementTags = tasks.register<CheckRequirementTags>("checkRequirementTags") {
    group = "verification"
    description = "Checks the platform tags of the requirements (syntax, known targets, one state per target)"
    specDir.set(layout.projectDirectory.dir("docs/requirements"))
    targets.set(requirementTargets)
}
tasks.named("traceRequirements") { dependsOn(checkRequirementTags) }

// One report per target: `gradlew requirementReportAndroid` lists what is
// wanted now but has no design for the target, what is wanted later, and what
// is undecided. The two lists of gaps come from OpenFastTrace itself, traced
// over only that tag's requirements and designs; the build never fails on them.
val collectRequirements = tasks.named<CollectTask>("collectRequirements")
for (target in requirementTargets) {
    val (now, later) = listOf(target, "${target}_later").map { tag ->
        tasks.register<TraceTask>("trace" + tag.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }) {
            description = "Traces only the requirements and designs tagged $tag"
            dependsOn(collectRequirements)
            requirementsFile.set(collectRequirements.flatMap { it.outputFile })
            outputFile.set(layout.buildDirectory.file("reports/tracing-$tag.txt"))
            reportFormat.set("plain")
            reportVerbosity.set(ReportVerbosity.FAILURE_DETAILS)
            detailsSectionDisplay.set(DetailsSectionDisplay.COLLAPSE)
            importedRequirements.set(emptySet())
            filteredTags.set(setOf(tag))
            // Code tags carry no platform tags; tracing them here would only add noise.
            filteredArtifactTypes.set(setOf("req", "dsn"))
            filterAcceptsItemsWithoutTag.set(false)
            failBuild.set(false)
        }
    }
    tasks.register<RequirementReport>("requirementReport" + target.replaceFirstChar(Char::uppercase)) {
        group = "verification"
        description = "Lists the requirements wanted now, wanted later, and undecided for $target"
        dependsOn(now, later)
        specDir.set(layout.projectDirectory.dir("docs/requirements"))
        this.target.set(target)
        wantedNowTrace.set(now.flatMap { it.outputFile })
        wantedLaterTrace.set(later.flatMap { it.outputFile })
    }
}

// The browser extensions as installable archives (a plain zip of the extension
// dir; MADR 0012). Unsigned — see each extension/*/README.md for how to install.
val manifestVersion = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
fun extensionVersionOf(browser: String) =
    manifestVersion.find(file("extension/$browser/manifest.json").readText())!!.groupValues[1]

val extensionVersion = extensionVersionOf("firefox")
// The two extensions are one product in two packagings; a version that got
// bumped in only one of them would ship as two different releases.
require(extensionVersionOf("chrome") == extensionVersion) {
    "extension/chrome/manifest.json says ${extensionVersionOf("chrome")}, firefox says $extensionVersion"
}

val packageFirefoxExtension = tasks.register<Zip>("packageFirefoxExtension") {
    group = "distribution"
    description = "Packages the Firefox extension as an unsigned .xpi"
    from("extension/firefox") { exclude("README.md") }
    archiveFileName = "contextswitcher-firefox-$extensionVersion.xpi"
    destinationDirectory = layout.buildDirectory.dir("distributions")
}

// Chrome installs the *directory* (Load unpacked), so this zip is only for
// handing the extension around — the Web Store's upload shape.
val packageChromeExtension = tasks.register<Zip>("packageChromeExtension") {
    group = "distribution"
    description = "Packages the Chrome extension as a .zip"
    from("extension/chrome") { exclude("README.md") }
    archiveFileName = "contextswitcher-chrome-$extensionVersion.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
}

tasks.register("packageExtension") {
    group = "distribution"
    description = "Packages both browser extensions"
    dependsOn(packageFirefoxExtension, packageChromeExtension)
}
