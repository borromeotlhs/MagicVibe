#!/usr/bin/env groovy
this.metaClass = null

/*******************************************************************************************
 *  ReqCyclicCheck.groovy
 *  Requirements Dependency Cycle Detector (WORK_ENV Edition)
 *
 *  Also writes a CSV file listing all edges in all reported cycles:
 *       Columns: From, RelationshipType, To
 *       File:    <user.dir>/plugins/macros/logs/ReqCyclicCheck_cycles.csv
 *       (fallback: <user.dir>/logs/ReqCyclicCheck_cycles.csv)
 *******************************************************************************************/


// =========================================================================================
// IMPORTS (WORK_ENV-safe)
// =========================================================================================
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.sysml.util.SysMLProfile
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*   // Element, NamedElement, Relationship, Package, etc.
import groovy.lang.GroovyShell

import java.io.*           // File, FileWriter, BufferedWriter, etc.
import java.util.HashSet


// =========================================================================================
// CONFIG
// =========================================================================================
/** Toggle CSV export on/off. */
def CSV_ENABLED    = true
/** Name of CSV file (created under logs directory). */
def CSV_FILE_NAME  = "ReqCyclicCheck_cycles.csv"


// =========================================================================================
// LOG HELPERS (no timestamps; MagicDraw adds them)
// =========================================================================================
def LOG  = { msg -> Application.getInstance().getGUILog().log(msg) }
def WARN = { msg -> Application.getInstance().getGUILog().log("WARN: ${msg}") }
def ERR  = { msg -> Application.getInstance().getGUILog().log("ERROR: ${msg}") }


// =========================================================================================
// LOAD SLMNP (same mechanism as ReqRelCopier)
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
// REQUIREMENT DETECTION (stereotype-based, per WORK_ENV assertions)
// =========================================================================================
def isRequirement = { Element e, def reqStereo ->
    return (e != null && reqStereo != null &&
            StereotypesHelper.hasStereotypeOrDerived(e, reqStereo))
}


// =========================================================================================
// RELATIONSHIP HELPERS
// =========================================================================================

def getRelTypeName = { Relationship rel, Map relStereoMap, List enabledTypes ->
    for (String tName : enabledTypes) {
        def st = relStereoMap[tName]
        if (st != null && StereotypesHelper.hasStereotypeOrDerived(rel, st)) {
            return tName
        }
    }
    return "UnknownType"
}

/** Collect all Requirement elements under a given scope element (ownedElement recursion). */
def collectRequirementsInScope = { Element scopeElement, def reqStereo ->
    def result = []
    def walk
    walk = { Element e ->
        if (e == null) return

        if (isRequirement(e, reqStereo)) {
            result << e
        }

        e.ownedElement?.each { Element child ->
            walk(child)
        }
    }
    walk(scopeElement)
    return result
}

/**
 * Collect all Relationship elements of ANY kind that are attached to the given Requirements
 * as either client or supplier (via NamedElement.getClientDependency/getSupplierDependency).
 */
def collectRelationshipsTouchingRequirements = { Collection<Element> reqs ->
    def relSet = new HashSet<Relationship>()

    reqs.each { Element r ->
        if (!(r instanceof NamedElement)) return

        def ne = (NamedElement) r

        try {
            def cds = ne.getClientDependency()
            if (cds != null) {
                cds.each { dep ->
                    if (dep instanceof Relationship) {
                        relSet.add((Relationship) dep)
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            def sds = ne.getSupplierDependency()
            if (sds != null) {
                sds.each { dep ->
                    if (dep instanceof Relationship) {
                        relSet.add((Relationship) dep)
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    return relSet.toList()
}


// =========================================================================================
// GRAPH BUILDING
// =========================================================================================
/**
 * Build a (conceptually UNDIRECTED) graph from relationships of the enabled types.
 *
 * For each dependency with (clients x suppliers), we add two directed edges:
 *   client → supplier  AND  supplier → client
 * so cyclic chains are detected regardless of direction conventions.
 */
def buildGraph = { List allRels,
                   Map relStereos,
                   List enabledTypes,
                   def reqStereo ->

    def edges = []

    allRels.each { Relationship rel ->
        def tName = getRelTypeName(rel, relStereos, enabledTypes)
        if ("UnknownType".equals(tName)) {
            return // skip relationships not among the enabled types
        }

        List suppliers = []
        List clients   = []

        try {
            suppliers = rel.getSupplier() ? rel.getSupplier().toList() : []
        } catch (Throwable ignored) {}

        try {
            clients = rel.getClient() ? rel.getClient().toList() : []
        } catch (Throwable ignored) {}

        if (suppliers.isEmpty() || clients.isEmpty()) {
            return
        }

        // UNDIRECTED interpretation: add edges in BOTH directions for each client/supplier pair.
        clients.each { def c ->
            suppliers.each { def s ->
                if (c instanceof Element && s instanceof Element) {
                    Element ce = (Element) c
                    Element se = (Element) s

                    edges << [
                        src      : ce,
                        dst      : se,
                        rel      : rel,
                        typeName : tName
                    ]
                    edges << [
                        src      : se,
                        dst      : ce,
                        rel      : rel,
                        typeName : tName
                    ]
                }
            }
        }
    }

    def adj = [:].withDefault { [] }

    edges.each { edge ->
        adj[edge.src] << edge
        def tmp = adj[edge.dst]
    }

    return [adj: adj, edges: edges]
}


// =========================================================================================
// ELEMENT LABELING FOR LOGGING / CSV
// =========================================================================================
def elementLabel = { Element e, def reqStereo ->
    String nm = ""
    if (e instanceof NamedElement) {
        nm = e.qualifiedName ?: e.name ?: "<unnamed>"
    } else {
        nm = "<non-named element>"
    }

    String kind = isRequirement(e, reqStereo) ? "Requirement" : "Element"

    String id = ""
    try {
        id = e.getID() ?: ""
    } catch (Throwable ignored) {}

    if (id) {
        return "${nm} [${kind}] (id=${id})"
    } else {
        return "${nm} [${kind}]"
    }
}

/** Simpler label for CSV: just qualifiedName or name (no kind/id). */
def elementCsvName = { Element e ->
    if (e instanceof NamedElement) {
        return ((NamedElement) e).qualifiedName ?: ((NamedElement) e).name ?: "<unnamed>"
    }
    return "<non-named element>"
}


// =========================================================================================
// CYCLE DETECTION (DFS with stack; deduplicated by signature)
// =========================================================================================
def findCycles = { Map adj, def reqStereo ->

    def visited = new HashSet<Element>()
    def stack   = new HashSet<Element>()
    def cycles  = []
    def sigs    = new HashSet<String>()

    def elemId = { Element e ->
        try {
            return e.getID() ?: ("@" + e.hashCode())
        } catch (Throwable ignored) {
            return "@" + e.hashCode()
        }
    }

    def dfs
    dfs = { Element node, List<Element> nodePath, List<Map> edgePath ->
        visited.add(node)
        stack.add(node)

        def outgoing = adj[node]
        outgoing.each { Map edge ->
            Element next = (Element) edge.dst

            if (!stack.contains(next)) {
                if (!visited.contains(next)) {
                    nodePath.add(next)
                    edgePath.add(edge)
                    dfs(next, nodePath, edgePath)
                    edgePath.remove(edgePath.size() - 1)
                    nodePath.remove(nodePath.size() - 1)
                }
            } else {
                int idx = -1
                for (int i = 0; i < nodePath.size(); i++) {
                    if (nodePath[i].is(next)) {
                        idx = i
                        break
                    }
                }
                if (idx >= 0) {
                    def cycNodes = []
                    for (int i = idx; i < nodePath.size(); i++) {
                        cycNodes << nodePath[i]
                    }
                    cycNodes << next

                    def cycEdges = []
                    for (int i = idx; i < edgePath.size(); i++) {
                        cycEdges << edgePath[i]
                    }
                    cycEdges << edge

                    def nodeIds = cycNodes.collect { elemId(it) }
                    def relIds  = cycEdges.collect { edgeMap ->
                        Relationship r = (Relationship) edgeMap.rel
                        return elemId(r)
                    }

                    String sig = nodeIds.join("->") + "|" + relIds.join("->")
                    if (!sigs.contains(sig)) {
                        sigs.add(sig)
                        cycles << [nodes: cycNodes, edges: cycEdges]
                    }
                }
            }
        }

        stack.remove(node)
    }

    adj.keySet().each { Element n ->
        if (!visited.contains(n)) {
            dfs(n, [n] as List<Element>, [] as List<Map>)
        }
    }

    return cycles
}


// =========================================================================================
// CSV UTILITIES (writes to plugins/macros/logs or <user.dir>/logs)
// =========================================================================================
def csvEscape = { String s ->
    if (s == null) return ""
    return s.replace("\"", "\"\"")
}

/**
 * Resolve a logs file similar to how SLMNP.groovy is located:
 *
 * baseDir = new File(System.getProperty("user.dir"))
 * primary logs dir:   plugins/macros/logs
 * fallback logs dir:  logs
 */
def resolveLogsFile = { String fileName ->
    def baseDir = new File(System.getProperty("user.dir"))

    // Prefer logs directory under plugins/macros (sibling to lib)
    def logs1 = new File(baseDir, "plugins/macros/logs")
    // Fallback logs directory directly under baseDir
    def logs2 = new File(baseDir, "logs")

    File targetDir = null

    if (logs1.exists()) {
        targetDir = logs1
    } else if (logs2.exists()) {
        targetDir = logs2
    } else {
        // Try to create plugins/macros/logs first
        if (logs1.mkdirs()) {
            targetDir = logs1
        } else if (logs2.mkdirs()) {
            targetDir = logs2
        } else {
            throw new IOException("Unable to create logs directory at '${logs1.absolutePath}' or '${logs2.absolutePath}'")
        }
    }

    return new File(targetDir, fileName)
}

def writeCsv = { List<List<String>> rows, String fileName ->
    try {
        def outFile = resolveLogsFile(fileName)

        def fw = new FileWriter(outFile, false) // overwrite
        def bw = new BufferedWriter(fw)

        // header
        bw.write("\"From\",\"RelationshipType\",\"To\"")
        bw.newLine()

        rows.each { r ->
            String line = "\"${csvEscape(r[0])}\",\"${csvEscape(r[1])}\",\"${csvEscape(r[2])}\""
            bw.write(line)
            bw.newLine()
        }

        bw.close()
        LOG("CSV written to: ${outFile.getAbsolutePath()}")
    } catch (Exception ex) {
        ERR("Failed to write CSV file '${fileName}': ${ex}")
    }
}


// =========================================================================================
// MAIN EXECUTION
// =========================================================================================
LOG("=== ReqCyclicCheck (Req dependency cycle detector, WORK_ENV) ===")

def app = Application.getInstance()
def project = app.getProject()
if (!project) {
    ERR("No active project loaded.")
    return
}

def SLMNP = loadSLMNP()
if (!SLMNP) return

def sysml     = SysMLProfile.getInstance(project)
def reqStereo = sysml.getRequirement()

def relStereos = [
    "Satisfy"    : sysml.getSatisfy(),
    "Refine"     : sysml.getRefine(),
    "DeriveReqt" : sysml.getDeriveReqt(),
    "Trace"      : sysml.getTrace()
]
def enabledRelTypes = ["Satisfy", "Refine", "DeriveReqt", "Trace"]


// =========================================================================================
// PICK SCOPE USING SLMNP
// =========================================================================================
LOG("Opening REQUIREMENTS SCOPE picker…")
def scopeSel = SLMNP.pick(
        "Select Requirements Scope",
        "Pick the package (or element) whose contained Requirements will act as\n" +
        "the starting points for the cyclic dependency search."
)
if (!scopeSel) {
    WARN("Canceled at SCOPE picker.")
    return
}
Element scopeElement = (Element) scopeSel.element

String scopeName = ""
if (scopeElement instanceof NamedElement) {
    scopeName = scopeElement.qualifiedName ?: scopeElement.name ?: "<unnamed>"
} else {
    scopeName = "<non-named element>"
}
LOG("Scope selected: ${scopeName}")


// =========================================================================================
// COLLECT REQUIREMENTS & THEIR RELATIONSHIPS
// =========================================================================================
def scopedReqs = collectRequirementsInScope(scopeElement, reqStereo)
LOG("Requirements in scope = ${scopedReqs.size()}")

if (scopedReqs.isEmpty()) {
    LOG("No Requirements found under selected scope; nothing to analyze.")
    LOG("=== ReqCyclicCheck complete (no requirements). ===")
    return
}

def allRels = collectRelationshipsTouchingRequirements(scopedReqs)
LOG("Relationships touching in-scope Requirements (all kinds) = ${allRels.size()}")

if (allRels.isEmpty()) {
    LOG("No dependencies of type «satisfy», «refine», «deriveReqt», or «trace» touching in-scope Requirements were found.")
    LOG("=== ReqCyclicCheck complete (no candidate edges). ===")
    return
}


// =========================================================================================
// BUILD GRAPH & FIND CYCLES
// =========================================================================================
def graphData = buildGraph(allRels, relStereos, enabledRelTypes, reqStereo)
def adj       = graphData.adj

if (adj.isEmpty()) {
    LOG("No «satisfy», «refine», «deriveReqt», or «trace» relationships of the enabled types could be mapped into the graph.")
    LOG("=== ReqCyclicCheck complete (no candidate edges). ===")
    return
}

def cycles = findCycles(adj, reqStereo)

if (cycles.isEmpty()) {
    LOG("No cyclic dependency chains were detected involving the in-scope Requirements.")
    LOG("=== ReqCyclicCheck complete (no cycles). ===")
    return
}


// =========================================================================================
// LOG & CSV OUTPUT
// =========================================================================================
def csvRows = [] as List<List<String>>

int loggedCount = 0
int idx = 1
LOG("Detected cyclic chain(s) involving Requirements:")
cycles.each { cyc ->
    def nodes = (List<Element>) cyc.nodes
    def edges = (List<Map>)     cyc.edges

    boolean hasReq = nodes.any { n -> isRequirement(n, reqStereo) }
    if (!hasReq) {
        return
    }

    LOG("Cycle #${idx}:")
    for (int i = 0; i < edges.size(); i++) {
        Element from      = nodes[i]
        Element to        = nodes[i + 1]
        def edgeMap       = edges[i]
        Relationship rel  = (Relationship) edgeMap.rel
        String typeName   = (String) edgeMap.typeName

        String relName = ""
        if (rel instanceof NamedElement) {
            relName = rel.name ?: ""
        }

        String line = "  ${elementLabel(from, reqStereo)} --«${typeName}»--> ${elementLabel(to, reqStereo)}"
        if (relName) {
            line += "  (rel='${relName}')"
        }
        LOG(line)

        // CSV row: FromName, RelationshipType, ToName
        csvRows << [
            elementCsvName(from),
            typeName,
            elementCsvName(to)
        ]
    }
    idx++
    loggedCount++
}

if (loggedCount == 0) {
    LOG("Cyclic chains exist in the graph, but none of the detected cycles involve Requirements.")
} else if (CSV_ENABLED && !csvRows.isEmpty()) {
    writeCsv(csvRows, CSV_FILE_NAME)
}

LOG("=== ReqCyclicCheck complete. ===")
