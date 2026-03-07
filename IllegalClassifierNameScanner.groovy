#!/usr/bin/env groovy
this.metaClass = null

/*******************************************************************************************
 * IllegalClassifierNameScanner.groovy
 *
 * Scans classifier names (including SysML Blocks) for characters that can break creation of
 * part properties / instances / usages in MagicDraw 2022xR2.
 *
 * Modes (selected by modal dialog):
 *   - Find/Log: report illegal names only.
 *   - Clean: report + strip illegal characters from classifier names.
 *
 * Logging:
 *   - MagicDraw Notification Window (GUILog)
 *   - CSV file under logs/ (or plugins/macros/logs/)
 *******************************************************************************************/

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.sysml.util.SysMLProfile
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier

import javax.swing.JOptionPane

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat

// =========================================================================================
// LOGGING HELPERS (GUILog + console)
// =========================================================================================
def guiLog = { String text ->
    try {
        Application.getInstance().getGUILog().log(text)
    } catch (Throwable t) {
        println text
    }
}

def INFO = { String msg ->
    guiLog("[IllegalNameScan] ${msg}")
}

def WARN = { String msg ->
    guiLog("[IllegalNameScan][WARN] ${msg}")
}

def ERR = { String msg ->
    guiLog("[IllegalNameScan][ERROR] ${msg}")
}

// =========================================================================================
// Helpers
// =========================================================================================
def csvEscape = { String v ->
    if (v == null) return ""
    return v.replace('"', '\'')
}

def resolveLogsFile = { Project project, String suffix ->
    def baseDir = new File(System.getProperty("user.dir"))
    def candidates = [
        new File(baseDir, "plugins/macros/logs"),
        new File(baseDir, "logs")
    ]

    File logsDir = candidates.find { it.exists() && it.isDirectory() }
    if (!logsDir) {
        logsDir = candidates[0]
        if (!logsDir.exists()) logsDir.mkdirs()
    }

    String projectBaseName = "Project"
    try {
        def primaryProject = project?.getPrimaryProject()
        def locPath = primaryProject?.getLocation()?.getPath()
        if (locPath) {
            projectBaseName = new File(locPath).getName()
            int dotIdx = projectBaseName.lastIndexOf('.')
            if (dotIdx > 0) projectBaseName = projectBaseName.substring(0, dotIdx)
        } else {
            projectBaseName = primaryProject?.getName() ?: project?.getName() ?: "Project"
        }
    } catch (Throwable ignored) {
        projectBaseName = project?.getName() ?: "Project"
    }

    def ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
    return new File(logsDir, "${projectBaseName}_${suffix}_${ts}.csv")
}

def getUsedProjectRoots = { Project proj ->
    if (proj == null) return []
    def primary = proj.getPrimaryModel()
    def roots = proj.getModels()
    if (roots == null) return []
    def used = []
    roots.each { m ->
        if (m != null && !m.is(primary)) {
            used << m
        }
    }
    return used
}

def sanitizeName = { String original ->
    String src = original ?: ""
    // Keep letters/digits/underscore only.
    String cleaned = src.replaceAll(/[^A-Za-z0-9_]/, "")

    // For downstream part/instance/usage name safety, avoid leading digit.
    if (cleaned && cleaned[0].isNumber()) {
        cleaned = "_" + cleaned
    }

    if (!cleaned) {
        cleaned = "Unnamed"
    }
    return cleaned
}

def hasIllegalChars = { String name ->
    if (name == null || name.length() == 0) return true
    return (name !=~ /[A-Za-z_][A-Za-z0-9_]*/)
}

def collectAllElements = { Element root, List<Element> bucket ->
    if (root == null) return
    bucket << root
    def owned = root.getOwnedElement()
    if (owned == null) return
    owned.each { child ->
        if (child instanceof Element) collectAllElements(child as Element, bucket)
    }
}

// =========================================================================================
// Main
// =========================================================================================
def app = Application.getInstance()
def project = app?.getProject()
if (project == null) {
    ERR("No active project found.")
    return
}

String[] options = ["Find/Log", "Clean", "Cancel"]
int choice = JOptionPane.showOptionDialog(
    null,
    "Choose mode for illegal classifier-name handling:",
    "Illegal Classifier Name Scanner",
    JOptionPane.DEFAULT_OPTION,
    JOptionPane.QUESTION_MESSAGE,
    null,
    options,
    options[0]
)

if (choice < 0 || choice == 2) {
    INFO("Canceled by user.")
    return
}

boolean cleanMode = (choice == 1)
INFO("Mode selected: ${cleanMode ? 'Clean' : 'Find/Log'}")

File logFile = resolveLogsFile(project, cleanMode ? "illegal_classifier_name_clean" : "illegal_classifier_name_find")
PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))
writer.println('"Mode","ElementID","QualifiedName","ElementType","IsBlock","OriginalName","SanitizedName","Action"')

int scanned = 0
int flagged = 0
int renamed = 0
int unchangedAfterSanitize = 0

String sessionName = "Clean Illegal Classifier Names"
boolean sessionOpen = false

try {
    if (cleanMode) {
        SessionManager.getInstance().createSession(project, sessionName)
        sessionOpen = true
    }

    List<Element> roots = []
    if (project.getPrimaryModel() != null) roots << project.getPrimaryModel()
    roots.addAll(getUsedProjectRoots(project))

    Set<String> usedNamesByOwner = [] as Set<String>

    roots.each { root ->
        List<Element> all = []
        collectAllElements(root, all)

        all.each { Element e ->
            if (!(e instanceof Classifier)) return
            if (!(e instanceof NamedElement)) return

            scanned++
            NamedElement ne = e as NamedElement
            String name = ne.name ?: ""
            boolean bad = hasIllegalChars(name)
            if (!bad) return

            flagged++
            boolean isBlock = false
            try {
                isBlock = StereotypesHelper.hasStereotypeOrDerived(ne, SysMLProfile.BLOCK_STEREOTYPE)
            } catch (Throwable ignored) {
                isBlock = StereotypesHelper.hasStereotypeOrDerived(ne, "Block")
            }

            String sanitized = sanitizeName(name)
            String qn = ne.qualifiedName ?: name
            String typeName = ne.getClass().getSimpleName()
            String action = "LOGGED"

            if (cleanMode) {
                if (sanitized == name) {
                    unchangedAfterSanitize++
                    action = "NO_CHANGE"
                } else {
                    def owner = ne.owner
                    String ownerId = owner?.getID() ?: "<no-owner>"
                    String uniqueKey = ownerId + "::" + sanitized

                    if (usedNamesByOwner.contains(uniqueKey)) {
                        String suffix = "_" + ne.getID().take(8)
                        sanitized = sanitized + suffix
                        uniqueKey = ownerId + "::" + sanitized
                    }

                    ne.setName(sanitized)
                    renamed++
                    usedNamesByOwner << uniqueKey
                    action = "RENAMED"
                }
            }

            writer.println([
                '"' + csvEscape(cleanMode ? "CLEAN" : "FIND") + '"',
                '"' + csvEscape(ne.getID()) + '"',
                '"' + csvEscape(qn) + '"',
                '"' + csvEscape(typeName) + '"',
                '"' + csvEscape(isBlock.toString()) + '"',
                '"' + csvEscape(name) + '"',
                '"' + csvEscape(sanitized) + '"',
                '"' + csvEscape(action) + '"'
            ].join(","))

            INFO("${action}: ${typeName} | ${qn} | '${name}' -> '${sanitized}'")
        }
    }

    if (sessionOpen) {
        SessionManager.getInstance().closeSession(project)
        sessionOpen = false
    }

    writer.flush()
    INFO("Scan complete. Classifiers scanned=${scanned}, illegal=${flagged}, renamed=${renamed}, no-change=${unchangedAfterSanitize}")
    INFO("CSV log: ${logFile.absolutePath}")

} catch (Throwable t) {
    if (sessionOpen) {
        try {
            SessionManager.getInstance().cancelSession(project)
        } catch (Throwable ignored) {}
        sessionOpen = false
    }
    ERR("Failed: ${t.class.simpleName}: ${t.message}")
    throw t
} finally {
    try {
        writer?.close()
    } catch (Throwable ignored) {}
}
