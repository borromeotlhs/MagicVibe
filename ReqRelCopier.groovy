#!/usr/bin/env groovy
this.metaClass = null

/*******************************************************************************************
 *  ReqRelCopier.groovy
 *  Requirements Relationship Duplicator (FROM → TO), WORK_ENV Edition
 *
 *  Behavior:
 *    - DOES NOT modify or delete existing relationships.
 *    - For each relationship in SCOPE that touches a FROM requirement, and if a
 *      corresponding TO requirement exists (matched by name), create a NEW relationship
 *      of the same UML kind (Abstraction) under the same owner, but pointing to the TO
 *      requirement instead of the FROM requirement.
 *
 *  CSV Log format:
 *      Status,FromID,FromName,ToID,ToName,RelationshipType
 *
 *    Status values:
 *      Ignored – no TO requirement found for FROM requirement (logged once per unmatched FROM)
 *      Skipped – relationship already exists on TO requirement
 *      Copied  – relationship created (or would be, in DRY_RUN)
 *******************************************************************************************/


// =========================================================================================
// IMPORTS
// =========================================================================================
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.sysml.util.SysMLProfile
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Relationship

import groovy.lang.GroovyShell

import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.JCheckBox

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat


// =========================================================================================
// GLOBAL CONFIG
// =========================================================================================
def DRY_RUN = false   // Will be set by askDryRun()

PrintWriter logWriter = null
File logFile = null

// Counters
int ignoredCount   = 0   // FROM req has no TO match
int skippedCount   = 0   // relationship already exists on TO
int copiedCount    = 0   // relationship created / would be created
int candidateCount = 0   // relationships that matched FROM/TO + type


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
    guiLog("[ReqRelCopier] ${msg}")
}

def WARN = { String msg ->
    guiLog("[ReqRelCopier][WARN] ${msg}")
}

def ERR = { String msg ->
    guiLog("[ReqRelCopier][ERROR] ${msg}")
}


// =========================================================================================
/** CSV helpers */
// =========================================================================================
def csvEscape = { String v ->
    if (v == null) return ""
    // Replace any double quotes with single quotes to keep CSV simple
    return v.replace('"', '\'')
}

def logCsvRow = { PrintWriter writer,
                  String status,
                  Element fromReq,
                  Element toReq,
                  String relType ->

    if (writer == null) return

    def fromId   = fromReq?.getID() ?: ""
    def fromName = (fromReq instanceof NamedElement) ? (fromReq.name ?: "") : ""

    def toId     = toReq?.getID() ?: ""
    def toName   = (toReq instanceof NamedElement) ? (toReq.name ?: "") : ""

    def vals = [
        csvEscape(status),
        csvEscape(fromId),
        csvEscape(fromName),
        csvEscape(toId),
        csvEscape(toName),
        csvEscape(relType ?: "")
    ]

    def line = vals.collect { '"' + it + '"' }.join(",")
    writer.println(line)
    writer.flush()
}


// =========================================================================================
/** Resolve a logs CSV file based on project file name (no Date.format) */
// =========================================================================================
def resolveLogsFile = { Project project, String suffix ->
    def baseDir = new File(System.getProperty("user.dir"))
    def candidates = [
        new File(baseDir, "plugins/macros/logs"),
        new File(baseDir, "logs")
    ]

    File logsDir = candidates.find { it.exists() && it.isDirectory() }
    if (!logsDir) {
        logsDir = candidates[0]
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
    }

    // Determine project base name from project file if possible, else use project name
    String projectBaseName = "Project"
    try {
        def f = project.getFile()  // might not exist; wrapped in try/catch
        if (f instanceof File) {
            projectBaseName = f.getName()
            int dotIdx = projectBaseName.lastIndexOf('.')
            if (dotIdx > 0) {
                projectBaseName = projectBaseName.substring(0, dotIdx)
            }
        } else {
            projectBaseName = project.getName() ?: "Project"
        }
    } catch (Throwable ignored) {
        projectBaseName = project.getName() ?: "Project"
    }

    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
    def ts  = sdf.format(new Date())
    return new File(logsDir, "${projectBaseName}_${suffix}_${ts}.csv")
}


// =========================================================================================
/** LOAD SLMNP UTILITY (WORK_ENV pattern) */
// =========================================================================================
def loadSLMNP = {
    def baseDir = new File(System.getProperty("user.dir"))
    def p1 = new File(baseDir, "plugins/macros/lib/SLMNP.groovy")
    def p2 = new File(baseDir, "lib/SLMNP.groovy")

    if (p1.exists()) {
        INFO("Loading SLMNP from: ${p1.absolutePath}")
        return new GroovyShell().parse(p1)
    }
    if (p2.exists()) {
        INFO("Loading SLMNP from: ${p2.absolutePath}")
        return new GroovyShell().parse(p2)
    }

    ERR("SLMNP.groovy not found in plugins/macros/lib or lib.")
    return null
}


// =========================================================================================
/** MODAL: DRY RUN ON/OFF (ReqRelSwitcher-style) */
// =========================================================================================
def askDryRun = {
    Object[] opts = ["Dry Run (no changes)", "Perform Actual Duplicate"] as Object[]
    int choice = JOptionPane.showOptionDialog(
            null,
            "Choose run mode:",
            "ReqRelCopier Mode",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            opts,
            opts[0]
    )
    return (choice == 0)
}


// =========================================================================================
/** MODAL: RELATIONSHIP TYPE MULTISELECT (ReqRelSwitcher-style) */
// =========================================================================================
def askRelTypes = {

    def types = [
        "Satisfy",
        "Verify",
        "Refine",
        "DeriveReqt",
        "Copy",
        "Trace"
    ]

    def panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))

    def checks = [:]

    types.each { t ->
        def cb = new JCheckBox(t, true)
        checks[t] = cb
        panel.add(cb)
    }

    JOptionPane.showMessageDialog(
            null,
            panel,
            "Select Relationship Types",
            JOptionPane.PLAIN_MESSAGE
    )

    return checks.findAll { k, v -> v.isSelected() }.collect { it.key }
}


// =========================================================================================
/** UTIL: Recursively gather requirements under a root, keyed by name */
// =========================================================================================
def gatherReqs = { Element element, reqStereo ->
    def out = [:] as LinkedHashMap<String, Element>

    def walk
    walk = { Element e ->
        if (e == null) return
        e.ownedElement?.each { child ->
            if (reqStereo && StereotypesHelper.hasStereotypeOrDerived(child, reqStereo)) {
                def nm = child.name ?: "(unnamed)"
                out[nm] = child
            }
            walk(child)
        }
    }
    walk(element)
    return out
}


// =========================================================================================
/** UTIL: Check if a relationship has one of the enabled SysML relationship stereotypes.
 *  Robust: uses SysMLProfile map first, then falls back to stereotype names (case-insensitive).
 */
// =========================================================================================
def hasWantedStereo = { rel, enabledTypes, relStereoMap ->
    def applied = StereotypesHelper.getStereotypes(rel) ?: []

    for (String tName : enabledTypes) {
        def st = relStereoMap[tName]
        if (st != null) {
            if (StereotypesHelper.hasStereotypeOrDerived(rel, st)) {
                return true
            }
        } else {
            // Fallback: match by stereotype name (case-insensitive)
            if (applied.any { it?.name?.equalsIgnoreCase(tName) }) {
                return true
            }
        }
    }
    return false
}


// =========================================================================================
/** UTIL: Determine the "primary" type name for logging, given enabled types.
 *  Robust: uses SysMLProfile map first, then falls back to stereotype names (case-insensitive).
 */
// =========================================================================================
def getRelTypeName = { rel, relStereoMap, enabledTypes ->
    def applied = StereotypesHelper.getStereotypes(rel) ?: []

    for (String tName : enabledTypes) {
        def st = relStereoMap[tName]
        if (st != null) {
            if (StereotypesHelper.hasStereotypeOrDerived(rel, st)) {
                return tName
            }
        } else {
            if (applied.any { it?.name?.equalsIgnoreCase(tName) }) {
                return tName
            }
        }
    }
    return "UnknownType"
}


// =========================================================================================
/** UTIL: Collect all relationships under a scope element */
// =========================================================================================
def collectRelationships = { scopeElement ->
    def result = []
    def walkR
    walkR = { e ->
        if (e == null) return
        e.ownedElement?.each { child ->
            if (child instanceof Relationship) {
                result << child
            }
            walkR(child)
        }
    }
    walkR(scopeElement)
    return result
}


// =========================================================================================
/** UTIL: Check if an equivalent relationship already exists on the TO side */
// =========================================================================================
def hasExistingDuplicateRel = { rel, owner, suppliers, clients,
                                matchFrom, matchTo,
                                fromIsSupplier, fromIsClient,
                                relStereoMap, enabledTypes ->

    if (owner == null) return false

    // Build the endpoint sets that the NEW relationship would have
    def targetSuppliers = new ArrayList(suppliers)
    def targetClients   = new ArrayList(clients)

    if (fromIsSupplier) {
        targetSuppliers.remove(matchFrom)
        targetSuppliers.add(matchTo)
    }
    else if (fromIsClient) {
        targetClients.remove(matchFrom)
        targetClients.add(matchTo)
    }

    def targetSupSet = targetSuppliers as Set
    def targetCliSet = targetClients as Set
    def thisTypeName = getRelTypeName(rel, relStereoMap, enabledTypes)

    return (owner.ownedElement?.any { other ->
        if (other == rel) return false
        if (!(other instanceof Relationship)) return false
        if (other.getClass() != rel.getClass()) return false

        def otherTypeName = getRelTypeName(other, relStereoMap, enabledTypes)
        if (otherTypeName != thisTypeName) return false

        def oSupSet = other.getSupplier().toList() as Set
        def oCliSet = other.getClient().toList() as Set

        return (oSupSet == targetSupSet && oCliSet == targetCliSet)
    }) ?: false
}


// =========================================================================================
// MAIN EXECUTION
// =========================================================================================
INFO("=== ReqRelCopier (FROM→TO duplicator, WORK_ENV) START ===")

def app = Application.getInstance()
def project = app.getProject()
if (!project) {
    ERR("No active project loaded.")
    return
}

// Load SLMNP
def SLMNP = loadSLMNP()
if (!SLMNP) {
    ERR("SLMNP could not be loaded. Exiting.")
    return
}

// Open log file (non-fatal if it fails)
try {
    logFile   = resolveLogsFile(project, "ReqRelCopier")
    logWriter = new PrintWriter(new FileWriter(logFile))
    // CSV header
    logWriter.println('"Status","FromID","FromName","ToID","ToName","RelationshipType"')
    logWriter.flush()
    INFO("Logging to CSV file: ${logFile.absolutePath}")
} catch (Throwable t) {
    logWriter = null
    WARN("Unable to open log file for writing: ${t}. Continuing without CSV logging.")
}

// Helper to ensure logWriter is closed on any early exit
def closeLog = {
    if (logWriter != null) {
        logWriter.flush()
        logWriter.close()
        logWriter = null
    }
}


// SysML profile / stereotypes
def sysml = SysMLProfile.getInstance(project)
def reqStereo = sysml.getRequirement()

// Relationship stereotype map
def relStereos = [
    "Satisfy"    : sysml.getSatisfy(),
    "Verify"     : sysml.getVerify(),
    "Refine"     : sysml.getRefine(),
    "DeriveReqt" : sysml.getDeriveReqt(),
    "Copy"       : sysml.getCopy(),
    "Trace"      : sysml.getTrace()
]


// =========================================================================================
// PICK FROM / TO / SCOPE (via SLMNP)
// =========================================================================================
INFO("Opening FROM picker…")
def fromSel = SLMNP.pick("Select FROM Root", "Pick the top requirement container for FROM")
if (!fromSel) {
    WARN("Canceled at FROM picker.")
    closeLog()
    return
}
def fromRoot = fromSel.element
INFO("FROM root selected: ${fromRoot?.name ?: fromRoot}")

INFO("Opening TO picker…")
def toSel = SLMNP.pick("Select TO Root", "Pick the top requirement container for TO")
if (!toSel) {
    WARN("Canceled at TO picker.")
    closeLog()
    return
}
def toRoot = toSel.element
INFO("TO root selected: ${toRoot?.name ?: toRoot}")

INFO("Opening RELATIONSHIP SCOPE picker…")
def scopeSel = SLMNP.pick("Select Relationship Scope", "Pick the container holding the relationships to duplicate")
if (!scopeSel) {
    WARN("Canceled at SCOPE picker.")
    closeLog()
    return
}
def relScope = scopeSel.element
INFO("Scope selected: ${relScope?.name ?: relScope}")


// =========================================================================================
// DRY RUN + RELATIONSHIP TYPE MODALS (ReqRelSwitcher-style)
// =========================================================================================
DRY_RUN = askDryRun()
INFO("DRY_RUN = ${DRY_RUN}")

def enabledRelTypes = askRelTypes()
if (!enabledRelTypes || enabledRelTypes.isEmpty()) {
    WARN("No relationship types selected; exiting.")
    closeLog()
    return
}
INFO("Enabled relationship types: " + enabledRelTypes.join(", "))


// =========================================================================================
// GATHER REQUIREMENTS AND PAIR FROM→TO BY NAME
// =========================================================================================
INFO("Gathering FROM requirements…")
def fromReqs = gatherReqs(fromRoot, reqStereo)

INFO("Gathering TO requirements…")
def toReqs = gatherReqs(toRoot, reqStereo)

INFO("FROM requirement count = ${fromReqs.size()}")
INFO("TO   requirement count = ${toReqs.size()}")

// Build pairs map: FROM Requirement → TO Requirement
def pairs = [:] as LinkedHashMap<Element, Element>
fromReqs.each { name, fromReq ->
    def toReq = toReqs[name]
    if (toReq != null) {
        pairs[fromReq] = toReq
    } else {
        // Log Ignored: no TO requirement found for this FROM requirement
        logCsvRow(logWriter, "Ignored", fromReq, null, null)
        ignoredCount++
    }
}

INFO("Paired FROM→TO requirements (by name) = ${pairs.size()}")

if (pairs.isEmpty()) {
    WARN("No matching TO requirements (by name) for any FROM requirement. Exiting.")
    closeLog()
    return
}


// =========================================================================================
// COLLECT RELATIONSHIPS UNDER SCOPE
// =========================================================================================
INFO("Collecting relationships under SCOPE…")
def allRels = collectRelationships(relScope)
INFO("Relationships in scope = ${allRels.size()}")


/*******************************************************************************************
 * DUPLICATE RELATIONSHIPS (NO REWIRING, WITH DUPLICATE SUPPRESSION)
 *******************************************************************************************/
def mem  = ModelElementsManager.getInstance()
def sess = SessionManager.getInstance()

sess.createSession("ReqRelCopier – duplicate FROM→TO")

try {
    allRels.each { rel ->

        if (!hasWantedStereo(rel, enabledRelTypes, relStereos)) return

        def suppliers = rel.getSupplier().toList()
        def clients   = rel.getClient().toList()

        def matchFrom  = null
        def matchTo    = null
        def fromIsSupplier = false
        def fromIsClient   = false

        suppliers.each { s ->
            if (pairs.containsKey(s) && matchFrom == null) {
                matchFrom      = s
                matchTo        = pairs[s]
                fromIsSupplier = true
            }
        }
        clients.each { c ->
            if (pairs.containsKey(c) && matchFrom == null) {
                matchFrom    = c
                matchTo      = pairs[c]
                fromIsClient = true
            }
        }

        if (!matchFrom || !matchTo) return

        def typeName = getRelTypeName(rel, relStereos, enabledRelTypes)
        candidateCount++

        def owner = rel.getOwner()
        if (owner == null || !owner.isEditable()) {
            WARN("Skipping relationship with non-editable owner: ${rel}")
            return
        }

        // Duplicate-suppression check
        if (hasExistingDuplicateRel(
                rel,
                owner,
                suppliers,
                clients,
                matchFrom,
                matchTo,
                fromIsSupplier,
                fromIsClient,
                relStereos,
                enabledRelTypes
        )) {
            // Skipped – relationship already exists on TO requirement
            logCsvRow(logWriter, "Skipped", matchFrom, matchTo, typeName)
            skippedCount++
            return
        }

        // At this point, we'd create a new relationship (unless DRY_RUN)
        if (!DRY_RUN) {
            def newRel = project.getElementsFactory().createAbstractionInstance()
            mem.addElement(newRel, owner)

            // Copy name if present
            if (rel instanceof NamedElement) {
                newRel.setName(rel.getName())
            }

            // Copy endpoints from original
            suppliers.each { s -> newRel.getSupplier().add(s) }
            clients.each   { c -> newRel.getClient().add(c) }

            // Swap FROM requirement to TO requirement on the correct side
            if (fromIsSupplier) {
                newRel.getSupplier().remove(matchFrom)
                newRel.getSupplier().add(matchTo)
            }
            else if (fromIsClient) {
                newRel.getClient().remove(matchFrom)
                newRel.getClient().add(matchTo)
            }

            // Copy all stereotypes from original relationship
            def applied = StereotypesHelper.getStereotypes(rel)
            applied?.each { st ->
                StereotypesHelper.addStereotype(newRel, st)
            }
        }

        // Copied – either actually created or would be (in DRY_RUN)
        logCsvRow(logWriter, "Copied", matchFrom, matchTo, typeName)
        copiedCount++
    }

    sess.closeSession()
}
catch (Throwable t) {
    ERR("Exception during duplication: ${t}")
    try { sess.cancelSession() } catch (Throwable ignore) {}
}
finally {
    INFO("Candidate relationships considered = ${candidateCount}")
    INFO("Ignored (no TO requirement found) = ${ignoredCount}")
    INFO("Skipped (relationship already existed) = ${skippedCount}")
    INFO("Copied (or would be, in DRY_RUN) = ${copiedCount}")

    if (logFile != null && logWriter != null) {
        INFO("CSV log file: ${logFile.absolutePath}")
    } else {
        WARN("No CSV log file was written.")
    }

    if (logWriter != null) {
        logWriter.flush()
        logWriter.close()
        logWriter = null
    }
}

INFO("=== ReqRelCopier COMPLETE (FROM→TO duplicator) END ===")
return
