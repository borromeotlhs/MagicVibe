#!/usr/bin/env groovy
this.metaClass = null

/*******************************************************************************************
 *  ReqRelSwitcher.groovy
 *  Requirements Relationship Duplicator (FROM → TO), WORK_ENV Edition
 *
 *  Behavior:
 *    - DOES NOT modify existing relationships.
 *    - For each relationship in SCOPE that touches a FROM requirement, and if a
 *      corresponding TO requirement exists (matched by name), create a NEW relationship
 *      of the same UML kind (Abstraction) under the same owner, but pointing to the TO
 *      requirement instead of the FROM requirement.
 *
 *    Example:
 *      FROM_req  <-- «satisfy» --  SysML_Element      (original, unchanged)
 *      TO_req    <-- «satisfy» --  SysML_Element      (new, added by this macro)
 *
 *  Key Features:
 *    - Uses SLMNP.pick(title, label) for FROM, TO, and SCOPE
 *    - DRY_RUN toggle dialog
 *    - Relationship type multi-select dialog
 *    - Recursively gathers requirements under FROM and TO
 *    - Pairs FROM/TO requirements by name
 *    - Only processes relationships under chosen SCOPE
 *    - Creates new Abstraction relationships; copies stereotypes, name, and endpoints
 *    - Respects element editability; skips locked owners
 *    - WORK_ENV-safe (no forward refs, closures first)
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
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*
import groovy.lang.GroovyShell

import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.JCheckBox

import java.io.File


// =========================================================================================
// LOG HELPERS (no timestamps; MagicDraw adds them)
// =========================================================================================
def LOG  = { msg -> Application.getInstance().getGUILog().log(msg) }
def WARN = { msg -> Application.getInstance().getGUILog().log("WARN: ${msg}") }
def ERR  = { msg -> Application.getInstance().getGUILog().log("ERROR: ${msg}") }


// =========================================================================================
/** LOAD SLMNP UTILITY */
// =========================================================================================
def loadSLMNP = {
    def baseDir = new File(System.getProperty("user.dir"))
    def p1 = new File(baseDir, "plugins/macros/lib/SLMNP.groovy")
    def p2 = new File(baseDir, "lib/SLMNP.groovy")

    if (p1.exists()) return new GroovyShell().parse(p1)
    if (p2.exists()) return new GroovyShell().parse(p2)

    ERR("SLMNP.groovy not found in plugins/macros/lib or lib.")
    return null
}


// =========================================================================================
/** MODAL: DRY RUN ON/OFF */
// =========================================================================================
def askDryRun = {
    Object[] opts = ["Dry Run (no changes)", "Perform Actual Duplicate"] as Object[]
    int choice = JOptionPane.showOptionDialog(
            null,
            "Choose run mode:",
            "ReqRelSwitcher Mode",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            opts,
            opts[0]
    )
    return (choice == 0)
}


// =========================================================================================
/** MODAL: RELATIONSHIP TYPE MULTISELECT */
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
/** UTIL: Recursively gather requirements under a root */
// =========================================================================================
def gatherReqs = { element, reqStereo ->
    def out = [:]

    def walk
    walk = { e ->
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
/** UTIL: Check if a relationship has one of the enabled SysML requirement stereotypes */
// =========================================================================================
def hasWantedStereo = { rel, sterMap ->
    sterMap.values().any { st ->
        st != null && StereotypesHelper.hasStereotypeOrDerived(rel, st)
    }
}


// =========================================================================================
/** UTIL: Determine the "primary" type name for logging, given enabled types */
// =========================================================================================
def getRelTypeName = { rel, relStereoMap, enabledTypes ->
    for (String tName : enabledTypes) {
        def st = relStereoMap[tName]
        if (st != null && StereotypesHelper.hasStereotypeOrDerived(rel, st)) {
            return tName
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
// MAIN EXECUTION
// =========================================================================================
LOG("=== ReqRelSwitcher (FROM→TO duplicator, WORK_ENV) ===")

def app = Application.getInstance()
def project = app.getProject()
if (!project) {
    ERR("No active project loaded.")
    return
}

// Load SLMNP
def SLMNP = loadSLMNP()
if (!SLMNP) return

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
// PICK FROM / TO / SCOPE
// =========================================================================================
LOG("Opening FROM picker…")
def fromSel = SLMNP.pick("Select FROM Root", "Pick the top requirement container for FROM")
if (!fromSel) {
    WARN("Canceled at FROM picker.")
    return
}
def fromRoot = fromSel.element

LOG("Opening TO picker…")
def toSel = SLMNP.pick("Select TO Root", "Pick the top requirement container for TO")
if (!toSel) {
    WARN("Canceled at TO picker.")
    return
}
def toRoot = toSel.element

LOG("Opening RELATIONSHIP SCOPE picker…")
def scopeSel = SLMNP.pick("Select Relationship Scope", "Pick the container holding the relationships to duplicate")
if (!scopeSel) {
    WARN("Canceled at SCOPE picker.")
    return
}
def relScope = scopeSel.element


// =========================================================================================
// DRY RUN + RELATIONSHIP TYPE MODALS
// =========================================================================================
def DRY_RUN = askDryRun()
LOG("DRY_RUN = ${DRY_RUN}")

def enabledRelTypes = askRelTypes()
if (!enabledRelTypes || enabledRelTypes.isEmpty()) {
    WARN("No relationship types selected; exiting.")
    return
}
LOG("Enabled relationship types: " + enabledRelTypes.join(", "))

def enabledRelStereos = relStereos.findAll { k, v -> enabledRelTypes.contains(k) && v != null }


// =========================================================================================
// GATHER REQUIREMENTS AND PAIR FROM→TO BY NAME
// =========================================================================================
LOG("Gathering FROM requirements…")
def fromReqs = gatherReqs(fromRoot, reqStereo)

LOG("Gathering TO requirements…")
def toReqs = gatherReqs(toRoot, reqStereo)

LOG("FROM requirement count = ${fromReqs.size()}")
LOG("TO   requirement count = ${toReqs.size()}")

// Build pairs map: FROM Requirement → TO Requirement
def pairs = [:]   // key: Element (FROM), value: Element (TO)
fromReqs.each { name, fromReq ->
    def toReq = toReqs[name]
    if (toReq != null) {
        pairs[fromReq] = toReq
    }
}

LOG("Paired FROM→TO requirements (by name) = ${pairs.size()}")


// =========================================================================================
// COLLECT RELATIONSHIPS UNDER SCOPE
// =========================================================================================
LOG("Collecting relationships under SCOPE…")
def allRels = collectRelationships(relScope)
LOG("Relationships in scope = ${allRels.size()}")


// =========================================================================================
// DUPLICATE RELATIONSHIPS (NO REWIRING)
// =========================================================================================
def mem  = ModelElementsManager.getInstance()
def sess = SessionManager.getInstance()

int wouldDuplicate = 0
int actualDuplicate = 0

// First pass (for dry-run logging + candidate count)
allRels.each { rel ->

    if (!hasWantedStereo(rel, enabledRelStereos)) return

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

    def otherEnds = []
    if (fromIsSupplier) {
        otherEnds.addAll(clients)
    }
    else if (fromIsClient) {
        otherEnds.addAll(suppliers)
    }

    def typeName = getRelTypeName(rel, relStereos, enabledRelTypes)
    def otherStr = otherEnds.collect { it?.name ?: "(unnamed)" }.join(", ")

    LOG("Would duplicate ${typeName}: FROM=${matchFrom.name} → TO=${matchTo.name}, other end(s)=[${otherStr}]")
    wouldDuplicate++
}

LOG("Candidate relationships found = ${wouldDuplicate}")

if (wouldDuplicate == 0) {
    LOG("No relationships matched the selected types + FROM/TO requirement pairing. No changes made.")
    return
}

if (DRY_RUN) {
    LOG("DRY RUN complete. Would duplicate = ${wouldDuplicate}. No changes made.")
    return
}

// REAL RUN: create parallel relationships
sess.createSession("ReqRelSwitcher – duplicate FROM→TO")

try {
    allRels.each { rel ->

        if (!hasWantedStereo(rel, enabledRelStereos)) return

        def owner = rel.getOwner()
        if (owner == null || !owner.isEditable()) {
            WARN("Skipping relationship with non-editable owner: ${rel}")
            return
        }

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

        // Create new Abstraction under the same owner
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

        def typeName = getRelTypeName(rel, relStereos, enabledRelTypes)
        LOG("Duplicated ${typeName}: FROM=${matchFrom.name} → TO=${matchTo.name}")
        actualDuplicate++
    }

    LOG("Actual duplicated relationships = ${actualDuplicate}")
    sess.closeSession()
}
catch (Throwable t) {
    ERR("Exception during duplication: ${t}")
    try { sess.cancelSession() } catch (Throwable ignore) {}
}

LOG("=== ReqRelSwitcher COMPLETE (FROM→TO duplicator) ===")
return
