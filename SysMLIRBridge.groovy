#!/usr/bin/env groovy
this.metaClass = null

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.charset.StandardCharsets

// =========================================================================================
// CONFIG
// =========================================================================================
String EXPORT_PATH = "./sysmlv2-ir.json"
String IMPORT_PATH = "./sysmlv2-ir.json"
boolean RUN_EXPORT_FROM_ACTIVE_MODEL = true
boolean RUN_IMPORT_AND_REEXPORT = false

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
    guiLog("[SysMLIRBridge] ${msg}")
}

def WARN = { String msg ->
    guiLog("[SysMLIRBridge][WARN] ${msg}")
}

def ERR = { String msg ->
    guiLog("[SysMLIRBridge][ERROR] ${msg}")
}

List<Element> getUsedProjectRoots(Project proj) {
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

String qName(Element e) {
    try {
        return e?.qualifiedName ?: e?.name ?: ""
    } catch (Throwable ignored) {
        return ""
    }
}

String nameOf(Element e) {
    try {
        return e?.name ?: ""
    } catch (Throwable ignored) {
        return ""
    }
}

List<Element> ownedElements(Element e) {
    try {
        def values = e?.getOwnedElement()
        if (values == null) return []
        return values.findAll { it instanceof Element }
    } catch (Throwable ignored) {
        return []
    }
}

Map<String, Object> elementToV2Node(Element e, String ownerId) {
    String id = e?.getID() ?: ""
    String type = "Element"

    if (e instanceof Package) {
        type = "Package"
    } else if (e instanceof Class && StereotypesHelper.hasStereotypeOrDerived(e, "Requirement")) {
        type = "RequirementUsage"
    } else if (e instanceof Class) {
        type = "PartDefinition"
    } else if (e instanceof Property) {
        type = "PartUsage"
    }

    return [
        id           : id,
        ownerId      : ownerId,
        name         : nameOf(e),
        qualifiedName: qName(e),
        type         : type,
        stereotypes  : ((StereotypesHelper.getStereotypes(e) ?: [])
            .collect { it?.name }
            .findAll { it != null && it.trim().length() > 0 })
    ]
}

String relationshipKindFromStereotypes(Element rel) {
    if (StereotypesHelper.hasStereotypeOrDerived(rel, "DeriveReqt")) return "deriveRequirement"
    if (StereotypesHelper.hasStereotypeOrDerived(rel, "Satisfy")) return "satisfy"
    if (StereotypesHelper.hasStereotypeOrDerived(rel, "Verify")) return "verify"
    if (StereotypesHelper.hasStereotypeOrDerived(rel, "Refine")) return "refine"
    if (StereotypesHelper.hasStereotypeOrDerived(rel, "Trace")) return "trace"
    if (StereotypesHelper.hasStereotypeOrDerived(rel, "Copy")) return "copy"
    return "dependency"
}

Map<String, Object> relationshipToV2Edge(Element rel) {
    def suppliers = []
    def clients = []
    try {
        suppliers = rel?.getSupplier() ?: []
    } catch (Throwable ignored) {
        suppliers = []
    }
    try {
        clients = rel?.getClient() ?: []
    } catch (Throwable ignored) {
        clients = []
    }

    return [
        id       : rel?.getID() ?: "",
        kind     : relationshipKindFromStereotypes(rel),
        clients  : clients.collect { it?.getID() ?: "" }.findAll { it },
        suppliers: suppliers.collect { it?.getID() ?: "" }.findAll { it },
        name     : nameOf(rel),
        stereotypes: ((StereotypesHelper.getStereotypes(rel) ?: [])
            .collect { it?.name }
            .findAll { it != null && it.trim().length() > 0 })
    ]
}

void collectV2NodesRecursive(Element current, String ownerId, List<Map<String, Object>> outNodes) {
    if (current == null) return
    outNodes << elementToV2Node(current, ownerId)

    String thisId = current.getID()
    ownedElements(current).each { child ->
        collectV2NodesRecursive(child, thisId, outNodes)
    }
}

Map<String, Object> buildSysMLv2IRFromProject(Project project) {
    if (project == null) {
        throw new IllegalStateException("No active MagicDraw project is open.")
    }

    def primary = project.getPrimaryModel()
    def usedRoots = getUsedProjectRoots(project)

    List<Map<String, Object>> nodes = []
    List<Map<String, Object>> relationships = []

    collectV2NodesRecursive(primary as Element, "", nodes)
    usedRoots.each { root -> collectV2NodesRecursive(root as Element, "", nodes) }

    Set<String> seenRelIds = [] as Set<String>
    nodes.each { n ->
        Element element = project.getElementByID(n.id as String)
        if (element == null) return

        def supplierDependencies = []
        def clientDependencies = []

        try { supplierDependencies = element.getSupplierDependency() ?: [] } catch (Throwable ignored) {}
        try { clientDependencies = element.getClientDependency() ?: [] } catch (Throwable ignored) {}

        (supplierDependencies + clientDependencies).each { rel ->
            String relId = rel?.getID() ?: ""
            if (!relId || seenRelIds.contains(relId)) return
            seenRelIds << relId
            relationships << relationshipToV2Edge(rel as Element)
        }
    }

    return [
        schemaVersion: "1.0",
        irStandard   : "SysMLv2-IR",
        source       : [
            tool          : "MagicDraw 2022xR2",
            sourceStandard: "SysML v1.7b"
        ],
        model        : [
            rootModelId  : primary?.getID() ?: "",
            elementCount : nodes.size(),
            relationCount: relationships.size(),
            elements     : nodes,
            relationships: relationships
        ]
    ]
}

Map<String, Object> loadIRFromJson(String jsonPath) {
    File f = new File(jsonPath)
    if (!f.exists()) {
        throw new IllegalArgumentException("JSON file not found: ${jsonPath}")
    }

    def parsed = new JsonSlurper().parse(f)
    if (!(parsed instanceof Map)) {
        throw new IllegalArgumentException("Top-level JSON must be an object/map.")
    }

    Map<String, Object> ir = (Map<String, Object>) parsed
    if (ir.irStandard != "SysMLv2-IR") {
        WARN("JSON irStandard is '${ir.irStandard}', expected 'SysMLv2-IR'. Continuing.")
    }

    return ir
}

void saveIRToJson(Map<String, Object> ir, String jsonPath) {
    File out = new File(jsonPath)
    if (out.parentFile != null && !out.parentFile.exists()) {
        out.parentFile.mkdirs()
    }

    String content = JsonOutput.prettyPrint(JsonOutput.toJson(ir))
    out.setText(content + "\n", StandardCharsets.UTF_8.name())
}

Map<String, Object> normalizeAsSysMLv2IR(Map<String, Object> ir) {
    def model = (ir.model instanceof Map) ? (Map<String, Object>) ir.model : [:]
    def elements = (model.elements instanceof List) ? (List<Map<String, Object>>) model.elements : []
    def relationships = (model.relationships instanceof List) ? (List<Map<String, Object>>) model.relationships : []

    return [
        schemaVersion: ir.schemaVersion ?: "1.0",
        irStandard   : "SysMLv2-IR",
        source       : ir.source ?: [tool: "unknown", sourceStandard: "SysML v2"],
        model        : [
            rootModelId  : model.rootModelId ?: "",
            elementCount : elements.size(),
            relationCount: relationships.size(),
            elements     : elements,
            relationships: relationships
        ]
    ]
}

void runMacro() {
    INFO("Starting SysML IR bridge macro.")

    if (RUN_EXPORT_FROM_ACTIVE_MODEL) {
        Project p = Application.getInstance().getProject()
        Map<String, Object> irFromV1 = buildSysMLv2IRFromProject(p)
        saveIRToJson(irFromV1, EXPORT_PATH)
        INFO("Exported active SysML v1.7b model to SysMLv2 IR JSON: ${new File(EXPORT_PATH).absolutePath}")
        INFO("Elements=${irFromV1.model.elementCount}, Relationships=${irFromV1.model.relationCount}")
    }

    if (RUN_IMPORT_AND_REEXPORT) {
        Map<String, Object> loaded = loadIRFromJson(IMPORT_PATH)
        Map<String, Object> normalized = normalizeAsSysMLv2IR(loaded)
        saveIRToJson(normalized, EXPORT_PATH)
        INFO("Loaded JSON IR from ${new File(IMPORT_PATH).absolutePath} and re-wrote normalized SysMLv2 IR to ${new File(EXPORT_PATH).absolutePath}")
    }

    INFO("Done.")
}

runMacro()
