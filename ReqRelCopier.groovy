#!/usr/bin/env groovy
this.metaClass = null

/*******************************************************************************************
 *  ReqRelCopier.groovy
 *  Requirements Relationship Duplicator (FROM → TO), WORK_ENV Edition
 *
 *  Updated Behavior:
 *    - DOES NOT modify or delete existing relationships.
 *    - FROM, TO, SCOPE:
 *        FROM  = root container for source requirements
 *        TO    = root container for target requirements (matched by name)
 *        SCOPE = owner package for any NEW relationships created
 *
 *    - For each FROM requirement:
 *        * Discover its relationships ANYWHERE in the model by using
 *          supplierDependency / clientDependency (adjacency-based access),
 *          similar in spirit to your getSatisfiedByElements() helper.
 *        * Filter those relationships by selected SysML stereotype(s):
 *              Satisfy, Verify, Refine, DeriveReqt, Copy, Trace
 *        * For each such relationship:
 *             - Identify the TO requirement matched by name.
 *             - Build a candidate new relationship with TO on the same side
 *               (supplier or client) that FROM occupied, preserving all
 *               other endpoints.
 *             - Check for an existing equivalent relationship on the TO
 *               requirement by examining TO’s dependency lists
 *               (supplierDependency/clientDependency), regardless of the
 *               owner of that existing relationship.
 *             - If no equivalent exists, create a NEW relationship under
 *               SCOPE (owner = SCOPE), copying name and stereotypes.
 *
 *  CSV Log format:
 *      Status,FromID,FromName,ToID,ToName,RelationshipType
 *
 *    Status values:
 *      Ignored – no TO requirement found for FROM requirement (logged once per unmatched FROM)
 *      Skipped – relationship already exists on TO requirement (any owner)
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
int candidateCount = 0   // relationships that matched FROM/TO + type (considered for duplication)


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

    // Determine project base name from project location or file if possible, else use project name
    String projectBaseName = "Project"
    try {
        def primaryProject = project?.getPrimaryProject()

        // Use the actual project location when available (covers local + TWC downloads)
        def locPath = primaryProject?.getLocation()?.getPath()
        if (locPath) {
            projectBaseName = new File(locPath).getName()
            int dotIdx = projectBaseName.lastIndexOf('.')
            if (dotIdx > 0) {
                projectBaseName = projectBaseName.substring(0, dotIdx)
            }
        } else {
            def f = project?.getFile()  // might not exist; wrapped in try/catch
            if (f instanceof File) {
                projectBaseName = f.getName()
                int dotIdx = projectBaseName.lastIndexOf('.')
                if (dotIdx > 0) {
                    projectBaseName = projectBaseName.substring(0, dotIdx)
                }
            } else {
                projectBaseName = primaryProject?.getName() ?: project?.getName() ?: "Project"
            }
        }
    } catch (Throwable ignored) {
        projectBaseName = project?.getName() ?: "Project"
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
/** UTIL: Determine the "primary" type name for logging, given enabled types. */
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
/**
 * NEW UTIL: Collect all relationships of a given type for a requirement by adjacency.
 *
 *  This mirrors your getSatisfiedByElements() pattern:
 *    - Use supplierDependency / clientDependency of the requirement.
 *    - Filter by stereotype (e.g., «satisfy», «verify», etc.).
 *    - Return the Relationship objects (not just the client/supplier elements).
 *
 *  We check both supplier and client dependency lists so that the code is robust
 *  to different SysML usage patterns.
 */
// =========================================================================================
def collectTypedRelsForRequirement = { NamedElement req,
                                       String typeName,
                                       relStereoMap ->

    def results = [] as List<Relationship>
    def seen    = new HashSet<Element>()

    // Gather all dependencies where req participates, via adjacency lists
    def deps = []
    try {
        req.getSupplierDependency()?.each { deps << it }
    } catch (Throwable ignored) {}
    try {
        req.getClientDependency()?.each { deps << it }
    } catch (Throwable ignored) {}

    deps.each { def dep ->
        if (!(dep instanceof Relationship)) return
        if (seen.contains(dep)) return
        seen.add(dep)

        // Use stereotype check pattern
        if (!hasWantedStereo(dep, [typeName], relStereoMap)) return

        results << (dep as Relationship)
    }

    return results
}


// =========================================================================================
/**
 * NEW UTIL: Check if an equivalent relationship already exists on the TO requirement,
 * regardless of the owner of that existing relationship.
 *
 *  - We build the target endpoint sets (suppliers/clients) using:
 *      otherSuppliers / otherClients + TO requirement on the same side
 *      that FROM occupied (supplier/client).
 *  - We then walk TO’s supplierDependency + clientDependency, and for each
 *    relationship with the same stereotype/type, we compare the endpoint sets.
 *
 *  This avoids any whole-model search while still respecting "regardless of
 *  that relationship's owner".
 */
// =========================================================================================
def hasExistingDuplicateOnTo = { NamedElement toReq,
                                 String typeName,
                                 List<Element> otherSuppliers,
                                 List<Element> otherClients,
                                 boolean toIsSupplier,
                                 boolean toIsClient,
                                 relStereoMap ->

    // Build the target end sets
    def targetSupSet = new LinkedHashSet<Element>(otherSuppliers ?: [])
    def targetCliSet = new LinkedHashSet<Element>(otherClients ?: [])

    if (toIsSupplier) targetSupSet.add(toReq)
    if (toIsClient)   targetCliSet.add(toReq)

    // Collect all relationships that already touch TO (adjacency)
    def deps = []
    try {
        toReq.getSupplierDependency()?.each { deps << it }
    } catch (Throwable ignored) {}
    try {
        toReq.getClientDependency()?.each { deps << it }
    } catch (Throwable ignored) {}

    // Check for type + endpoint match
    return (deps.any { def dep ->
        if (!(dep instanceof Relationship)) return false
        if (!hasWantedStereo(dep, [typeName], relStereoMap)) return false

        def oSupSet = new LinkedHashSet<Element>(dep.getSupplier()?.toList() ?: [])
        def oCliSet = new LinkedHashSet<Element>(dep.getClient()?.toList() ?: [])

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
def scopeSel = SLMNP.pick("Select Relationship Owner (SCOPE)", "Pick the container that will own any NEW relationships")
if (!scopeSel) {
    WARN("Canceled at SCOPE picker.")
    closeLog()
    return
}
def relScope = scopeSel.element
INFO("SCOPE (owner for NEW relationships): ${relScope?.name ?: relScope}")


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

// Build pairs map: FROM Requirement → TO Requirement (by name)
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
// DUPLICATE RELATIONSHIPS USING REQUIREMENT ADJACENCY (NO WHOLE-MODEL SEARCH)
// =========================================================================================
def mem  = ModelElementsManager.getInstance()
def sess = SessionManager.getInstance()

sess.createSession("ReqRelCopier – duplicate FROM→TO")

try {
    pairs.each { Element fromReq, Element toReq ->
        if (!(fromReq instanceof NamedElement) || !(toReq instanceof NamedElement)) {
            return
        }
        def fromNamed = fromReq as NamedElement
        def toNamed   = toReq   as NamedElement

        // For each selected relationship stereotype type, collect adjacency-based rels
        enabledRelTypes.each { String typeName ->
            def typedRels = collectTypedRelsForRequirement(fromNamed, typeName, relStereos)
            typedRels.each { Relationship rel ->
                candidateCount++

                def suppliers = rel.getSupplier()?.toList() ?: []
                def clients   = rel.getClient()?.toList() ?: []

                boolean fromIsSupplier = suppliers.contains(fromNamed)
                boolean fromIsClient   = clients.contains(fromNamed)

                // Sanity check: FROM must actually participate
                if (!fromIsSupplier && !fromIsClient) {
                    return
                }

                // Other endpoints are everything except FROM
                def otherSuppliers = suppliers.findAll { it != fromNamed }
                def otherClients   = clients.findAll { it != fromNamed }

                // Duplicate-suppression check:
                // Look at TO's dependencies, regardless of their owner
                if (hasExistingDuplicateOnTo(
                        toNamed,
                        typeName,
                        otherSuppliers,
                        otherClients,
                        fromIsSupplier,
                        fromIsClient,
                        relStereos
                )) {
                    // Skipped – relationship already exists on TO requirement
                    logCsvRow(logWriter, "Skipped", fromNamed, toNamed, typeName)
                    skippedCount++
                    return
                }

                // At this point, we'd create a new relationship (unless DRY_RUN)
                if (!DRY_RUN) {
                    // Create an Abstraction instance for all SysML rel types in this tool
                    def newRel = project.getElementsFactory().createAbstractionInstance()

                    // Owner is ALWAYS SCOPE now, independent of the original relationship's owner
                    mem.addElement(newRel, relScope)

                    // Copy name if present
                    if (rel instanceof NamedElement) {
                        newRel.setName(rel.getName())
                    }

                    // Copy all endpoints except FROM
                    otherSuppliers.each { s -> newRel.getSupplier().add(s) }
                    otherClients.each   { c -> newRel.getClient().add(c) }

                    // Put TO on the same side as FROM was
                    if (fromIsSupplier) {
                        newRel.getSupplier().add(toNamed)
                    }
                    if (fromIsClient) {
                        newRel.getClient().add(toNamed)
                    }

                    // Copy all stereotypes from original relationship
                    def applied = StereotypesHelper.getStereotypes(rel)
                    applied?.each { st ->
                        StereotypesHelper.addStereotype(newRel, st)
                    }
                }

                // Copied – either actually created or would be (in DRY_RUN)
                logCsvRow(logWriter, "Copied", fromNamed, toNamed, typeName)
                copiedCount++
            }
        }
    }

    sess.closeSession()
}
catch (Throwable t) {
    ERR("Exception during duplication: ${t}")
    try { sess.cancelSession() } catch (Throwable ignore) {}
}
finally {
    INFO("Candidate relationships considered (FROM adjacency) = ${candidateCount}")
    INFO("Ignored (no TO requirement found) = ${ignoredCount}")
    INFO("Skipped (relationship already existed on TO) = ${skippedCount}")
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

INFO("=== ReqRelCopier COMPLETE (FROM→TO duplicator, adjacency-based) END ===")
return
