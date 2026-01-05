#!/usr/bin/env groovy
this.metaClass = null

/*******************************************************************************************
 *  ReqVarCopier.groovy
 *  Requirement Variability Copier (FROM → TO)
 *
 *  Behavior:
 *    - Copies variability-related artifacts from FROM requirements to matching TO requirements
 *      (matched by name).
 *    - Does not duplicate existing TO artifacts (stereotypes, owned rules, or relationships).
 *    - Supports DRY_RUN mode and CSV logging similar to ReqRelCopier.groovy.
 *    - Uses SLMNP for FROM / TO / SCOPE selection.
 *
 *  Copied items:
 *    - InvisibleStereotype (MagicDraw Profile) on requirements.
 *    - Owned Rules stereotyped «ExistenceVariationPoint» (Variability Profile), including
 *      their Expression/specification content.
 *    - FeatureImpact dependency relationships (Variability Profile) touching FROM requirements;
 *      duplicated to TO requirements under the selected SCOPE owner.
 *    - Feature stereotype application/tag values (Variability Profile) on requirements.
 *******************************************************************************************/


// =========================================================================================
// IMPORTS
// =========================================================================================
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.sysml.util.SysMLProfile
import com.nomagic.magicdraw.copypaste.CopyPasting
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Relationship
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Constraint
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Slot
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.ValueSpecification

import groovy.lang.GroovyShell

import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.JCheckBox

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date


// =========================================================================================
// GLOBAL CONFIG
// =========================================================================================
def DRY_RUN = false   // Will be set by askDryRun()

PrintWriter logWriter = null
File logFile = null

// Counters
int ignoredCount   = 0   // FROM req has no TO match
int skippedCount   = 0   // artifact already exists on TO
int copiedCount    = 0   // artifact created / would be created
int candidateCount = 0   // artifacts considered for duplication


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
    guiLog("[ReqVarCopier] ${msg}")
}

def WARN = { String msg ->
    guiLog("[ReqVarCopier][WARN] ${msg}")
}

def ERR = { String msg ->
    guiLog("[ReqVarCopier][ERROR] ${msg}")
}


// =========================================================================================
/** CSV helpers */
// =========================================================================================
def csvEscape = { String v ->
    if (v == null) return ""
    return v.replace('"', '\'')
}

def logCsvRow = { PrintWriter writer,
                  String status,
                  Element fromReq,
                  Element toReq,
                  String operation,
                  String details ->

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
        csvEscape(operation ?: ""),
        csvEscape(details ?: "")
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

    String projectBaseName = "Project"
    try {
        def primaryProject = project?.getPrimaryProject()
        def locPath = primaryProject?.getLocation()?.getPath()
        if (locPath) {
            projectBaseName = new File(locPath).getName()
            int dotIdx = projectBaseName.lastIndexOf('.')
            if (dotIdx > 0) {
                projectBaseName = projectBaseName.substring(0, dotIdx)
            }
        } else {
            def f = project?.getFile()
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
    } catch (Throwable ignored) {}

    String timestamp = null
    try {
        timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
    } catch (Throwable ignored) {}

    def fileName = timestamp ? "${projectBaseName}_${suffix}_${timestamp}.csv" : "${projectBaseName}_${suffix}.csv"
    return new File(logsDir, fileName)
}


// =========================================================================================
/** MODAL: DRY RUN */
// =========================================================================================
def askDryRun = {
    def options = ["Dry Run", "Live"] as Object[]
    int choice = JOptionPane.showOptionDialog(
            null,
            "Run in dry-run mode (no model changes) or live mode?",
            "Execution Mode",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
    )
    return (choice == 0)
}


// =========================================================================================
/** MODAL: FeatureImpact copy toggle */
// =========================================================================================
def askCopyFeatureImpact = {
    def cb = new JCheckBox("Copy FeatureImpact dependencies", true)
    def panel = new JPanel()
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))
    panel.add(cb)

    JOptionPane.showMessageDialog(
            null,
            panel,
            "Relationship Copy Options",
            JOptionPane.PLAIN_MESSAGE
    )

    return cb.isSelected()
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
/** UTIL: Resolve stereotype by name across all applied profiles */
// =========================================================================================
def resolveStereoAcrossAppliedProfiles = { Project proj, String stereoName ->
    if (!proj || !stereoName) return null

    def stereo = null
    try {
        def profiles = StereotypesHelper.getAllProfiles(proj) ?: []
        stereo = profiles.collect { p ->
            try {
                return StereotypesHelper.getStereotype(proj, stereoName, p)
            } catch (Throwable ignored) {
                return null
            }
        }.find { it != null }
    } catch (Throwable ignored) {}

    if (stereo == null) {
        try {
            stereo = StereotypesHelper.getStereotype(proj, stereoName)
        } catch (Throwable ignored) {}
    }

    return stereo
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
/** UTIL: Collect FeatureImpact dependencies adjacent to a requirement */
// =========================================================================================
def collectFeatureImpactRels = { Element req, featureImpactStereo ->
    def rels = []
    try { req.getSupplierDependency()?.each { rels << it } } catch (Throwable ignored) {}
    try { req.getClientDependency()?.each { rels << it } } catch (Throwable ignored) {}
    return rels.findAll { rel ->
        rel instanceof Relationship && featureImpactStereo &&
                StereotypesHelper.hasStereotypeOrDerived(rel, featureImpactStereo)
    }
}


// =========================================================================================
/** UTIL: Collect ElementPropertyVariationPoint-tagged elements for a requirement */
// =========================================================================================
def collectEpvpElements = { Element req, epvpStereo ->
    def epvps = [] as LinkedHashSet<Element>
    if (!req || !epvpStereo) return epvps.toList()

    def consider = { Element candidate ->
        if (candidate && StereotypesHelper.hasStereotypeOrDerived(candidate, epvpStereo)) {
            epvps << candidate
        }
    }

    try {
        req.getOwnedElement()?.each { consider(it) }
    } catch (Throwable ignored) {}
    try {
        req.getOwnedAttribute()?.each { Property p ->
            consider(p)
            try {
                consider(p.getDefaultValue())
            } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}
    try {
        req.getSlot()?.each { Slot s ->
            consider(s)
            try {
                s.getValue()?.each { v ->
                    if (v instanceof Element) {
                        consider(v)
                    }
                }
            } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}

    return epvps.toList()
}


// =========================================================================================
/** UTIL: Remap references on copied EPVP elements */
// =========================================================================================
def remapEpvpElementReferences
remapEpvpElementReferences = { Element epvpEl, Closure<Element> remapTarget ->
    if (!(epvpEl instanceof Element) || remapTarget == null) return

    try {
        if (epvpEl.respondsTo("getType") && epvpEl.respondsTo("setType")) {
            def t = epvpEl.getType()
            def nt = remapTarget(t)
            if (nt != null && nt != t) {
                epvpEl.setType(nt)
            }
        }
    } catch (Throwable ignored) {}

    try {
        if (epvpEl instanceof Property) {
            def dv = epvpEl.getDefaultValue()
            if (dv instanceof Element) {
                remapEpvpElementReferences(dv, remapTarget)
            }
        }
    } catch (Throwable ignored) {}

    try {
        if (epvpEl instanceof Slot) {
            epvpEl.getValue()?.each { val ->
                if (val instanceof Element) {
                    remapEpvpElementReferences(val, remapTarget)
                }
            }
        }
    } catch (Throwable ignored) {}

    try {
        if (epvpEl instanceof ValueSpecification) {
            epvpEl.getOwnedElement()?.each { oe ->
                remapEpvpElementReferences(oe, remapTarget)
            }
        }
    } catch (Throwable ignored) {}
}


// =========================================================================================
/** UTIL: Check duplicate FeatureImpact on TO (by endpoints, any owner) */
// =========================================================================================
def hasDuplicateFeatureImpact = { Element toReq,
                                   Collection<Element> targetSupSet,
                                   Collection<Element> targetCliSet,
                                   featureImpactStereo ->
    if (!(toReq instanceof Element)) return false

    def deps = []
    try { toReq.getSupplierDependency()?.each { deps << it } } catch (Throwable ignored) {}
    try { toReq.getClientDependency()?.each { deps << it } } catch (Throwable ignored) {}

    def supTarget = new LinkedHashSet<Element>(targetSupSet ?: [])
    def cliTarget = new LinkedHashSet<Element>(targetCliSet ?: [])

    return deps.any { dep ->
        if (!(dep instanceof Relationship)) return false
        if (!featureImpactStereo || !StereotypesHelper.hasStereotypeOrDerived(dep, featureImpactStereo)) return false

        def supSet = new LinkedHashSet<Element>(dep.getSupplier()?.toList() ?: [])
        def cliSet = new LinkedHashSet<Element>(dep.getClient()?.toList() ?: [])
        return supSet == supTarget && cliSet == cliTarget
    }
}


// =========================================================================================
// MAIN EXECUTION
// =========================================================================================
INFO("=== ReqVarCopier (FROM→TO variability copier) START ===")

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
    logFile   = resolveLogsFile(project, "ReqVarCopier")
    logWriter = new PrintWriter(new FileWriter(logFile))
    logWriter.println('"Status","FromID","FromName","ToID","ToName","Operation","Details"')
    logWriter.flush()
    INFO("Logging to CSV file: ${logFile.absolutePath}")
} catch (Throwable t) {
    logWriter = null
    WARN("Unable to open log file for writing: ${t}. Continuing without CSV logging.")
}

def closeLog = {
    if (logWriter != null) {
        logWriter.flush()
        logWriter.close()
        logWriter = null
    }
}

// Profiles / stereotypes
def sysml = SysMLProfile.getInstance(project)
def reqStereo = sysml.getRequirement()

def variabilityProfile = StereotypesHelper.getProfile(project, "Variability Profile")
def existenceStereo = StereotypesHelper.getStereotype(project, "ExistenceVariationPoint", variabilityProfile)
def featureImpactStereo = StereotypesHelper.getStereotype(project, "FeatureImpact", variabilityProfile)
def epvpStereo = resolveStereoAcrossAppliedProfiles(project, "ElementPropertyVariationPoint")
def featureStereo = StereotypesHelper.getStereotype(project, "Feature", variabilityProfile)
def invisibleStereo = StereotypesHelper.getStereotype(project, "InvisibleStereotype", "MagicDraw Profile")

// PICK FROM / TO / SCOPE
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
def scopeSel = SLMNP.pick("Select Relationship Owner (SCOPE)", "Pick the container that will own any NEW FeatureImpact relationships")
if (!scopeSel) {
    WARN("Canceled at SCOPE picker.")
    closeLog()
    return
}
def relScope = scopeSel.element
INFO("SCOPE (owner for NEW relationships): ${relScope?.name ?: relScope}")

// DRY RUN + options
DRY_RUN = askDryRun()
INFO("DRY_RUN = ${DRY_RUN}")

def copyFeatureImpact = askCopyFeatureImpact()
INFO("Copy FeatureImpact relationships = ${copyFeatureImpact}")

// GATHER REQUIREMENTS AND PAIR FROM→TO BY NAME
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
        logCsvRow(logWriter, "Ignored", fromReq, null, "NoMatch", "No TO requirement with same name")
        ignoredCount++
    }
}

INFO("Paired FROM→TO requirements (by name) = ${pairs.size()}")

if (pairs.isEmpty()) {
    WARN("No matching TO requirements (by name) for any FROM requirement. Exiting.")
    closeLog()
    return
}

// DUPLICATE VARIABILITY ARTIFACTS

def mem  = ModelElementsManager.getInstance()
def sess = SessionManager.getInstance()

sess.createSession("ReqVarCopier – duplicate FROM→TO variability")

try {
    pairs.each { Element fromReq, Element toReq ->
        if (!(fromReq instanceof NamedElement) || !(toReq instanceof NamedElement)) {
            return
        }

        def ruleMap = [:] as LinkedHashMap<Element, Element> // FROM ExistenceVariationPoint → TO counterpart
        def epvpMap = [:] as LinkedHashMap<Element, Element> // FROM EPVP elements → TO counterparts

        // 1) InvisibleStereotype
        if (invisibleStereo && StereotypesHelper.hasStereotypeOrDerived(fromReq, invisibleStereo)) {
            candidateCount++
            if (StereotypesHelper.hasStereotypeOrDerived(toReq, invisibleStereo)) {
                logCsvRow(logWriter, "Skipped", fromReq, toReq, "InvisibleStereotype", "Already applied")
                skippedCount++
            } else {
                if (!DRY_RUN) {
                    StereotypesHelper.addStereotype(toReq, invisibleStereo)
                }
                logCsvRow(logWriter, "Copied", fromReq, toReq, "InvisibleStereotype", "Applied to TO")
                copiedCount++
            }
        }

        // 2) Feature stereotype properties
        if (featureStereo && StereotypesHelper.hasStereotypeOrDerived(fromReq, featureStereo)) {
            candidateCount++
            if (!DRY_RUN) {
                if (!StereotypesHelper.hasStereotypeOrDerived(toReq, featureStereo)) {
                    StereotypesHelper.addStereotype(toReq, featureStereo)
                }
                try {
                    StereotypesHelper.copyStereotypeProperties(fromReq, toReq, featureStereo, true)
                } catch (Throwable ignored) {}
            }
            logCsvRow(logWriter, "Copied", fromReq, toReq, "FeatureProperties", "Feature stereotype + properties copied")
            copiedCount++
        }

        // 3) Owned Rules with ExistenceVariationPoint
        def fromRules = []
        if (existenceStereo) {
            try {
                fromReq.getOwnedRule()?.each { rule ->
                    if (StereotypesHelper.hasStereotypeOrDerived(rule, existenceStereo)) {
                        fromRules << rule
                    }
                }
            } catch (Throwable ignored) {}
        }

        fromRules.each { Constraint rule ->
            candidateCount++
            def ruleName = (rule instanceof NamedElement) ? (rule.name ?: "") : ""
            def toRule = null
            def preExistingRules = []
            try {
                toReq.getOwnedRule()?.each { Constraint r ->
                    if (StereotypesHelper.hasStereotypeOrDerived(r, existenceStereo)) {
                        preExistingRules << r
                        def rn = (r instanceof NamedElement) ? (r.name ?: "") : ""
                        if (rn == ruleName && toRule == null) {
                            toRule = r
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (toRule != null) {
                logCsvRow(logWriter, "Skipped", fromReq, toReq, "OwnedRule", "Rule already exists: ${ruleName}")
                skippedCount++
                ruleMap[rule] = toRule
                return
            }

            if (!DRY_RUN) {
                try {
                    CopyPasting.copyPasteElement(rule, toReq, false)
                } catch (Throwable t) {
                    ERR("Failed to copy owned rule '${ruleName}': ${t}")
                }
            }

            Constraint newRule = null
            try {
                toReq.getOwnedRule()?.each { Constraint r ->
                    if (!StereotypesHelper.hasStereotypeOrDerived(r, existenceStereo)) return
                    def rn = (r instanceof NamedElement) ? (r.name ?: "") : ""
                    if (rn == ruleName && !preExistingRules.contains(r) && newRule == null) {
                        newRule = r
                    }
                }
            } catch (Throwable ignored) {}

            if (newRule != null) {
                ruleMap[rule] = newRule

                try {
                    def constrained = []
                    try { rule.getConstrainedElement()?.each { constrained << it } } catch (Throwable ignored) {}

                    def remapTarget = { Element ep ->
                        if (ep == null) return ep
                        if (ep == fromReq) return toReq
                        if (ruleMap.containsKey(ep)) return ruleMap[ep]
                        if (epvpMap.containsKey(ep) && epvpMap[ep] != null) return epvpMap[ep]
                        return ep
                    }

                    def remapped = constrained.collect { remapTarget(it) }.findAll { it != null }
                    try { newRule.getConstrainedElement()?.clear() } catch (Throwable ignored) {}
                    remapped.each { Element target ->
                        try { newRule.getConstrainedElement().add(target) } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) {
                    ERR("Failed to copy constrained elements for rule '${ruleName}': ${t}")
                }
            }

            logCsvRow(logWriter, "Copied", fromReq, toReq, "OwnedRule", "ExistenceVariationPoint: ${ruleName}")
            copiedCount++
        }

        // 3b) ElementPropertyVariationPoint artifacts
        if (epvpStereo) {
            def epvpKey = { Element el ->
                def cls = el?.getClass()?.getSimpleName() ?: "Element"
                def nm = (el instanceof NamedElement) ? (el.name ?: "") : ""
                return "${cls}::${nm}"
            }

            def fromEpvps = collectEpvpElements(fromReq, epvpStereo)
            def toEpvpsExisting = collectEpvpElements(toReq, epvpStereo)

            fromEpvps.each { Element epvpEl ->
                candidateCount++
                def key = epvpKey(epvpEl)

                def existing = toEpvpsExisting.find { epvpKey(it) == key }
                if (existing) {
                    logCsvRow(logWriter, "Skipped", fromReq, toReq, "EPVP", "Existing EPVP element on TO: ${key}")
                    skippedCount++
                    epvpMap[epvpEl] = existing
                    return
                }

                if (DRY_RUN) {
                    logCsvRow(logWriter, "Copied", fromReq, toReq, "EPVP", "DRY_RUN – would copy ${key}")
                    copiedCount++
                    return
                }

                Element newEpvp = null
                def before = new LinkedHashSet<Element>()
                try { toReq.getOwnedElement()?.each { before << it } } catch (Throwable ignored) {}
                try { toReq.getOwnedAttribute()?.each { before << it } } catch (Throwable ignored) {}
                try { toReq.getSlot()?.each { before << it } } catch (Throwable ignored) {}

                try {
                    CopyPasting.copyPasteElement(epvpEl, toReq, false)
                } catch (Throwable t) {
                    ERR("Failed to copy EPVP element '${key}': ${t}")
                }

                try {
                    def after = new LinkedHashSet<Element>()
                    toReq.getOwnedElement()?.each { after << it }
                    toReq.getOwnedAttribute()?.each { after << it }
                    toReq.getSlot()?.each { after << it }
                    def added = after.findAll { el ->
                        !before.contains(el) && StereotypesHelper.hasStereotypeOrDerived(el, epvpStereo)
                    }
                    if (!added.isEmpty()) {
                        newEpvp = added.iterator().next()
                    }
                } catch (Throwable ignored) {}

                if (newEpvp == null) {
                    try {
                        def allEpvps = collectEpvpElements(toReq, epvpStereo)
                        def fresh = allEpvps.findAll { !toEpvpsExisting.contains(it) }
                        if (!fresh.isEmpty()) {
                            newEpvp = fresh.iterator().next()
                        } else {
                            def keyMatch = allEpvps.find { epvpKey(it) == key }
                            if (keyMatch) {
                                newEpvp = keyMatch
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (newEpvp == null) {
                        logCsvRow(logWriter, "Skipped", fromReq, toReq, "EPVP", "Unable to copy EPVP element: ${key}")
                        skippedCount++
                        return
                    }
                }

                try {
                    if (!StereotypesHelper.hasStereotypeOrDerived(newEpvp, epvpStereo)) {
                        StereotypesHelper.addStereotype(newEpvp, epvpStereo)
                    }
                } catch (Throwable ignored) {}

                try {
                    StereotypesHelper.copyStereotypeProperties(epvpEl, newEpvp, epvpStereo, true)
                } catch (Throwable ignored) {}

                def remapTarget = { Element ep ->
                    if (ep == null) return ep
                    if (ep == fromReq) return toReq
                    if (ruleMap.containsKey(ep)) return ruleMap[ep]
                    if (epvpMap.containsKey(ep) && epvpMap[ep] != null) return epvpMap[ep]
                    return ep
                }
                remapEpvpElementReferences(newEpvp, remapTarget)

                try {
                    def constrained = new LinkedHashSet<Element>()
                    boolean hadCollectionError = false
                    def consider = { val ->
                        if (val instanceof Element) {
                            constrained << val
                        }
                    }
                    try {
                        epvpEl.getConstrainedElement()?.each { consider(it) }
                    } catch (Throwable t) {
                        hadCollectionError = true
                    }
                    try {
                        def slot = StereotypesHelper.getSlot(epvpEl, epvpStereo, "constrainedElement")
                        slot?.getValue()?.each { consider(it) }
                    } catch (Throwable t) {
                        hadCollectionError = true
                    }
                    try {
                        def stereoVals = StereotypesHelper.getStereotypePropertyValue(epvpEl, epvpStereo, "constrainedElement")
                        stereoVals?.each { consider(it) }
                    } catch (Throwable t) {
                        hadCollectionError = true
                    }
                    try {
                        def modelVals = ModelHelper.getStereotypePropertyValue(epvpEl, epvpStereo, "constrainedElement")
                        modelVals?.each { consider(it) }
                    } catch (Throwable t) {
                        hadCollectionError = true
                    }

                    if (constrained.isEmpty() && hadCollectionError) {
                        WARN("No constrained elements collected for EPVP element '${key}' (errors reading source constrained elements)")
                    }

                    def remapped = constrained.collect { remapTarget(it) }.findAll { it != null }
                    try { newEpvp.getConstrainedElement()?.clear() } catch (Throwable ignored) {}
                    remapped.each { Element target ->
                        try { newEpvp.getConstrainedElement().add(target) } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) {
                    ERR("Failed to copy constrained elements for EPVP element '${key}': ${t}")
                }

                epvpMap[epvpEl] = newEpvp
                toEpvpsExisting << newEpvp

                logCsvRow(logWriter, "Copied", fromReq, toReq, "EPVP", "ElementPropertyVariationPoint: ${key}")
                copiedCount++
            }
        }

        // 4) FeatureImpact relationships
        if (copyFeatureImpact && featureImpactStereo) {
            def rels = collectFeatureImpactRels(fromReq, featureImpactStereo)
            def logFeatureImpactResult = { String status, String detail ->
                logCsvRow(logWriter, status, fromReq, toReq, "FeatureImpact", detail)
                if ("Copied".equals(status)) copiedCount++
                if ("Skipped".equals(status)) skippedCount++
            }

            boolean scopeEditable = (relScope != null)
            try {
                if (scopeEditable) {
                    scopeEditable = project.isElementEditable(relScope)
                }
            } catch (Throwable ignored) {}

            if (!scopeEditable) {
                rels.each {
                    candidateCount++
                    logFeatureImpactResult("Skipped", "Scope not editable; skipping FeatureImpact copy")
                }
                return
            }

            rels.each { Relationship rel ->
                candidateCount++
                boolean proceed = true
                def remapEndpoint = { Element ep ->
                    if (ep == fromReq) return toReq
                    if (ruleMap.containsKey(ep)) return ruleMap[ep]
                    if (epvpMap.containsKey(ep) && epvpMap[ep] != null) return epvpMap[ep]
                    return ep
                }

                def suppliers = rel.getSupplier()?.toList() ?: []
                def clients   = rel.getClient()?.toList() ?: []

                boolean touchesFrom = suppliers.contains(fromReq) || clients.contains(fromReq)
                if (!touchesFrom) {
                    logFeatureImpactResult("Skipped", "Relationship does not touch source requirement; skipping")
                    proceed = false
                }

                if (proceed) {
                    // Reuse the existing feature / system endpoints; only FROM requirement
                    // and ExistenceVariationPoint rules are remapped. Everything else stays
                    // as-is to avoid copying or cloning features that already exist.
                    def targetSuppliers = suppliers.collect { remapEndpoint(it) }
                    def targetClients   = clients.collect { remapEndpoint(it) }

                    if (hasDuplicateFeatureImpact(toReq, targetSuppliers, targetClients, featureImpactStereo)) {
                        logFeatureImpactResult("Skipped", "Existing relationship found")
                        proceed = false
                    } else if (!DRY_RUN) {
                        Relationship newRel = null
                        try {
                            newRel = project.getElementsFactory().createDependencyInstance()
                            mem.addElement(newRel, relScope)

                            if (newRel.getOwner() == null) {
                                try { ModelElementsManager.getInstance().removeElement(newRel) } catch (Throwable ignored) {}
                                logFeatureImpactResult("Skipped", "New dependency has no owner; skipping to avoid corruption")
                                proceed = false
                            } else {
                                new LinkedHashSet<Element>(targetSuppliers).each { s -> newRel.getSupplier().add(s) }
                                new LinkedHashSet<Element>(targetClients).each   { c -> newRel.getClient().add(c) }

                                if (rel instanceof NamedElement) {
                                    newRel.setName(rel.getName())
                                }

                                def applied = StereotypesHelper.getStereotypes(rel)
                                applied?.each { st ->
                                    StereotypesHelper.addStereotype(newRel, st)
                                }
                            }
                        } catch (Throwable t) {
                            if (newRel != null) {
                                try { ModelElementsManager.getInstance().removeElement(newRel) } catch (Throwable ignored) {}
                            }
                            ERR("Failed to duplicate FeatureImpact for ${fromReq?.name} → ${toReq?.name}: ${t}")
                            logFeatureImpactResult("Skipped", "Error duplicating relationship: ${t}")
                            proceed = false
                        }
                    }

                    if (proceed) {
                        def detail = DRY_RUN ? "Relationship would be duplicated (DRY_RUN)" : "Relationship duplicated"
                        logFeatureImpactResult("Copied", detail)
                    }
                }
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
    INFO("Candidate artifacts considered = ${candidateCount}")
    INFO("Ignored (no TO requirement found) = ${ignoredCount}")
    INFO("Skipped (already existed on TO) = ${skippedCount}")
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

INFO("=== ReqVarCopier COMPLETE (FROM→TO variability copier) END ===")
return
