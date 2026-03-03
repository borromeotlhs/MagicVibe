/*
 * projectUsageToCSV.groovy
 *
 * Export a summary of the current project and any used projects (modules) to a CSV file.
 *
 * Each row contains:
 *   - Timestamp: the date/time this macro was run (yyyy‑MM‑dd HH:mm:ss)
 *   - Owner: the nominal owner of the project/module.  For the primary project
 *     this is the project name itself.  For used projects the script attempts
 *     to call common getter methods (getOwner, getOwnerName, getProjectOwner)
 *     via Groovy's method dispatch.  If no owner is available the field is blank.
 *   - ProjectName: the name of the project or used module.
 *   - VersionOrBranch: for Teamwork Cloud (TWC) projects this is the current
 *     version (plus one for the primary project) optionally followed by the branch
 *     name (e.g. "5/main").  For local projects and modules the value is
 *     "local unknown".
 *   - TotalVersions: for TWC projects this is the latest available version
 *     returned by ProjectTool.getLatestVersion().  For local projects/modules
 *     the value is "local - N/A".
 *
 * Notes:
 *   - This script relies on MagicDraw's OpenAPI and the ProjectTool helper
 *     class.  It makes best‑effort attempts to discover owner, version and
 *     branch information without assuming specific implementation classes.
 *   - If methods are missing on the runtime objects the script falls back to
 *     sensible defaults (blank owner, "local unknown" version and
 *     "local - N/A" total versions).
 *   - The primary project is always recorded with itself as the owner.  If the
 *     project is hosted on TWC the version number is incremented by one to
 *     account for the next commit the user intends to perform after running
 *     this script.
 */

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.core.ProjectTool
import com.nomagic.magicdraw.ui.notification.Notification
import com.nomagic.magicdraw.ui.notification.NotificationManager

/**
 * Safely invoke a zero‑argument method on an object if it exists.  Returns
 * null when the method is missing or throws.
 */
def safeInvoke(def obj, String methodName) {
    if (obj == null) return null
    try {
        if (obj.metaClass?.respondsTo(obj, methodName)) {
            return obj."$methodName"()
        }
    } catch (Throwable ignore) {
        // ignore
    }
    return null
}

/**
 * Extract a human friendly name from a project or module.  Tries the most
 * common getter names before falling back to toString().
 */
String extractName(def proj) {
    def name = safeInvoke(proj, "getName")
    if (name == null) name = safeInvoke(proj, "getProjectName")
    if (name == null) name = safeInvoke(proj, "getLabel")
    if (name == null) name = proj?.toString()
    return name?.toString() ?: ""
}

/**
 * Extract an owner name from a project or module.  Tries a handful of
 * reasonable accessor names.
 */
String extractOwner(def proj) {
    def owner = safeInvoke(proj, "getOwner")
    if (owner == null) owner = safeInvoke(proj, "getOwnerName")
    if (owner == null) owner = safeInvoke(proj, "getProjectOwner")
    if (owner != null) {
        // owner could itself be an object; extract its name
        def ownerName = safeInvoke(owner, "getName")
        if (ownerName != null) return ownerName.toString()
        return owner.toString()
    }
    return ""
}

/**
 * Determine if a module is remote (hosted on Teamwork Cloud) by probing
 * commonly available predicates.  Falls back to false when unknown.
 */
boolean isRemote(def module) {
    def remote = safeInvoke(module, "isRemote")
    if (remote instanceof Boolean) return remote
    remote = safeInvoke(module, "isServerProject")
    if (remote instanceof Boolean) return remote
    remote = safeInvoke(module, "isTWC")
    if (remote instanceof Boolean) return remote
    return false
}

/**
 * Extract a version string from a ProjectVersion object.  Looks for common
 * getters such as getVersion(), getVersionNumber(), getNumber() and falls
 * back to toString().  Returns null if no information is available.
 */
String extractVersionString(def pv) {
    if (pv == null) return null
    def v = safeInvoke(pv, "getVersion")
    if (v == null) v = safeInvoke(pv, "getVersionNumber")
    if (v == null) v = safeInvoke(pv, "getNumber")
    if (v == null) v = safeInvoke(pv, "getValue")
    if (v == null) v = pv?.toString()
    return v?.toString()
}

/**
 * Extract a branch name from a ProjectVersion object.  Attempts to call
 * getBranch() or getBranchName().  Returns empty string when no branch
 * information is available.
 */
String extractBranchName(def pv) {
    if (pv == null) return ""
    def branch = safeInvoke(pv, "getBranch")
    if (branch == null) branch = safeInvoke(pv, "getBranchName")
    if (branch != null) {
        def bn = safeInvoke(branch, "getName")
        return (bn != null ? bn.toString() : branch.toString())
    }
    return ""
}

/**
 * Try to increment a version represented as a string.  Extracts the first
 * integer found in the string, adds one and returns the result.  If the
 * string contains no digits the original value is returned unchanged.
 */
String incrementVersion(String version) {
    if (version == null) return null
    def matcher = (version =~ /\d+/)
    if (matcher.find()) {
        try {
            int num = Integer.parseInt(matcher.group(0))
            def inc = (num + 1).toString()
            // replace first occurrence of the number with its increment
            return version.replaceFirst(/\d+/, inc)
        } catch (NumberFormatException ignore) {
            // fall through
        }
    }
    // no digits found; append " +1" to hint the user
    return version + " +1"
}

/**
 * CSV helper: escape a value by doubling quotes and surrounding with quotes.
 */
String csvCell(def v) {
    String s = (v == null) ? "" : v.toString()
    s = s.replace("\"", "\"\"")
    return "\"${s}\""
}

// -------------------------------------------------------------------------
// Main logic
// -------------------------------------------------------------------------
def project = Application.getInstance().getProject()
def guiLog = Application.getInstance().getGUILog()
if (project == null) {
    guiLog.showError("No open project. Please open a project before running this script.")
    return
}

// Determine output directory and file name
def baseDir = new File(System.getProperty("user.dir"))
def logsDir = new File(baseDir, "logs")
if (!logsDir.exists()) logsDir.mkdirs()
def ts = java.time.LocalDateTime.now().format(
    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
)
def outFile = new File(logsDir, "project_usage_${ts}.csv")

// Acquire ProjectTool instance.  The API exposes a static method getInstance().
def projectTool = ProjectTool.getInstance()

// Build rows.  Each row is a list of column values.
def rows = []

// Capture current date/time string once for all rows.
def nowString = java.time.LocalDateTime.now().format(
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
)

// Helper to assemble a row for a given project module.
def addRowForModule = { def mod, boolean isPrimary ->
    String ownerName
    String projectName
    String versionBranch
    String totalVersions
    boolean remote = false

    // Extract names
    projectName = extractName(mod)
    if (isPrimary) {
        // Primary project owns itself
        ownerName = projectName
    } else {
        ownerName = extractOwner(mod)
    }

    // Determine if module is remote
    remote = isRemote(mod)

    if (remote && projectTool != null) {
        try {
            // Retrieve current and latest version info
            def pv = projectTool.getProjectVersion(mod)
            def latest = projectTool.getLatestVersion(mod)
            String currentV = extractVersionString(pv)
            String branch = extractBranchName(pv)
            String latestV = extractVersionString(latest)
            // For primary project we add one to the version
            if (isPrimary) {
                currentV = incrementVersion(currentV)
            }
            // Compose version/branch
            if (branch != null && branch.trim() != "") {
                versionBranch = currentV + "/" + branch
            } else {
                versionBranch = currentV
            }
            totalVersions = latestV
        } catch (Throwable t) {
            // If anything fails, fall back to local semantics
            versionBranch = "local unknown"
            totalVersions = "local - N/A"
        }
    } else {
        // Local project/module
        versionBranch = "local unknown"
        totalVersions = "local - N/A"
    }
    rows << [nowString, ownerName ?: "", projectName ?: "", versionBranch ?: "", totalVersions ?: ""]
}

// Add primary project row
def primary = project.getPrimaryProject()
addRowForModule(primary != null ? primary : project, true)

// Add rows for used projects/modules
def used = []
try {
    used = project.getUsedProjects()
} catch (Throwable ignore) {
    // Some versions expose getModules() instead; try that
    try {
        used = project.getModules()
    } catch (Throwable ignore2) {
        used = []
    }
}
if (used != null) {
    used.each { mod ->
        if (mod != null) {
            addRowForModule(mod, false)
        }
    }
}

// Write CSV
outFile.withWriter("UTF-8") { w ->
    // Write header
    def header = ["Timestamp", "Owner", "ProjectName", "VersionOrBranch", "TotalVersions"]
    w.writeLine(header.collect { csvCell(it) }.join(","))
    // Write data rows
    rows.each { row ->
        w.writeLine(row.collect { csvCell(it) }.join(","))
    }
}

// Notify user of completion
def absPath = outFile.getCanonicalPath()
guiLog.log("Project usage CSV saved (" + rows.size() + " rows): " + absPath)
def noteId = "projectUsageToCSV_" + ts
def note = new Notification(noteId, "Project usage exported", "CSV saved (" + rows.size() + " rows): " + absPath)
NotificationManager.getInstance().openNotificationWindow(note, true)

// Also print to console
println("Project usage CSV saved (" + rows.size() + " rows): " + absPath)