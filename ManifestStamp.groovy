import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property

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

def INFO = { String msg -> guiLog("[ManifestStamp] ${msg}") }
def WARN = { String msg -> guiLog("[ManifestStamp][WARN] ${msg}") }
def ERR  = { String msg -> guiLog("[ManifestStamp][ERROR] ${msg}") }

// =========================================================================================
// CLI parsing
// =========================================================================================
def parseArgs = { Object rawArgs ->
    def tokens = []
    if (rawArgs instanceof String[]) {
        tokens.addAll(rawArgs as List)
    } else if (rawArgs instanceof List) {
        tokens.addAll(rawArgs.collect { String.valueOf(it) })
    } else if (binding?.hasVariable("args")) {
        def a = binding.getVariable("args")
        if (a instanceof String[]) tokens.addAll(a as List)
        if (a instanceof List) tokens.addAll(a.collect { String.valueOf(it) })
    }

    def out = [
        project : null,
        commit  : null,
        branch  : null,
        used    : []
    ]

    int i = 0
    while (i < tokens.size()) {
        def t = tokens[i]
        if (t == "--project" && i + 1 < tokens.size()) { out.project = tokens[++i] }
        else if (t == "--commit" && i + 1 < tokens.size()) { out.commit = tokens[++i] }
        else if (t == "--branch" && i + 1 < tokens.size()) { out.branch = tokens[++i] }
        else if (t == "--used" && i + 1 < tokens.size()) {
            out.used = tokens[++i].split(",").collect { it.trim() }.findAll { it }
        }
        i++
    }

    return out
}

def parseUsedEntry = { String token ->
    if (token == null) return null
    def t = token.trim()
    if (!t) return null

    // Format: <name>|<commit>|<branch>
    // Branch is kept exactly as provided (no normalization).
    def parts = t.split("\\|", -1)
    if (parts.length == 1) {
        return [name: parts[0].trim(), commit: 0, branch: null]
    }

    def nm = parts[0].trim()
    def cm = 0
    try { cm = Integer.parseInt(parts[1].trim()) } catch (Throwable ignored) { cm = 0 }
    def br = (parts.length >= 3) ? parts[2] : null
    return [name: nm, commit: cm, branch: br]
}

// =========================================================================================
// Model helpers
// =========================================================================================
def findByNameRecursive
findByNameRecursive = { Element start, String wantedName, Class wantedType ->
    if (start == null) return null
    try {
        if (wantedType.isInstance(start) && start.hasProperty("name") && start.name == wantedName) {
            return start
        }
    } catch (Throwable ignored) {}

    def owned = []
    try { owned = start.getOwnedElement() ?: [] } catch (Throwable ignored) { return null }

    for (def child : owned) {
        def found = findByNameRecursive(child as Element, wantedName, wantedType)
        if (found != null) return found
    }
    return null
}

def getUsedProjectRoots = { Project proj ->
    if (proj == null) return []
    def primary = proj.getPrimaryModel()
    def roots   = proj.getModels()
    if (roots == null) return []
    def used = []
    roots.each { m ->
        if (m != null && !m.is(primary)) used << m
    }
    return used
}

def createFactoryElement = { Object ef, String contains ->
    def m = ef.class.methods.find { it.name.startsWith("create") && it.name.contains(contains) && it.parameterCount == 0 }
    if (m == null) throw new IllegalStateException("No ElementsFactory method create*${contains}* found")
    return m.invoke(ef)
}

def ensureOwnedElement = { Element owner, Element child ->
    if (owner == null || child == null) return
    if (child.getOwner() == owner) return
    ModelElementsManager.getInstance().addElement(child, owner)
}

def ensureProperty = { Object ef, Class ownerBlock, String propName, Object typeEl, int lower, int upper ->
    def existing = ownerBlock.getOwnedAttribute()?.find { it.name == propName }
    if (existing != null) return existing

    def prop = createFactoryElement(ef, "Property")
    prop.setName(propName)
    prop.setType(typeEl)
    prop.setLower(lower)
    prop.setUpper(upper)
    ensureOwnedElement(ownerBlock, prop)
    return prop
}

def ensureDataProperty = { Object ef, Class ownerBlock, String propName, String mdTypeName ->
    def existing = ownerBlock.getOwnedAttribute()?.find { it.name == propName }
    if (existing != null) return existing

    def primitive = findByNameRecursive(project.getPrimaryModel(), mdTypeName, Class)
    if (primitive == null) {
        WARN("Could not find primitive type '${mdTypeName}' in model; data slot values may still be created.")
    }

    def prop = createFactoryElement(ef, "Property")
    prop.setName(propName)
    if (primitive != null) prop.setType(primitive)
    prop.setLower(1)
    prop.setUpper(1)
    ensureOwnedElement(ownerBlock, prop)
    return prop
}

def ensureSlotWithValue = { Object ef, def instanceSpec, Property p, Object valueSpec ->
    def slot = instanceSpec.getSlot()?.find { s -> s.getDefiningFeature() == p }
    if (slot == null) {
        slot = createFactoryElement(ef, "Slot")
        slot.setDefiningFeature(p)
        ensureOwnedElement(instanceSpec, slot)
    }
    slot.getValue()?.clear()
    slot.getValue()?.add(valueSpec)
    return slot
}

def literalInteger = { Object ef, int v ->
    def lit = createFactoryElement(ef, "LiteralInteger")
    lit.setValue(v)
    return lit
}

def literalString = { Object ef, String v ->
    def lit = createFactoryElement(ef, "LiteralString")
    lit.setValue(v)
    return lit
}

def instanceValue = { Object ef, def inst ->
    def iv = createFactoryElement(ef, "InstanceValue")
    iv.setInstance(inst)
    return iv
}

def ensureInstanceTable = { Package manifestPkg ->
    def existing = findByNameRecursive(project.getPrimaryModel(), "Manifest Instance Table", Element)
    if (existing != null) return existing

    // Best effort: try Generic Table API via reflection; fallback to a marker class.
    try {
        def gtmClazz = Class.forName("com.nomagic.magicdraw.generictable.GenericTableManager")
        def gtm = gtmClazz.getMethod("getInstance").invoke(null)
        def created = gtmClazz.getMethod("createGenericTable", Project, String).invoke(gtm, project, "Manifest Instance Table")
        INFO("Created generic table via GenericTableManager.")
        return created
    } catch (Throwable t) {
        WARN("Could not create generic table API object (${t.class.simpleName}: ${t.message}); creating fallback class marker.")
        def ef = project.getElementsFactory()
        def fallback = createFactoryElement(ef, "Class")
        fallback.setName("Manifest Instance Table")
        ensureOwnedElement(manifestPkg, fallback)
        return fallback
    }
}

// =========================================================================================
// Main stamp operation
// =========================================================================================
def app = Application.getInstance()
def project = app.getProject()
if (project == null) {
    ERR("No active project. Open a project before running ManifestStamp.")
    return
}

def parsed = parseArgs(this.args)
def containingProjectTitle = parsed.project ?: (project.getPrimaryModel()?.getName() ?: "ContainingProject")
def commitRaw = parsed.commit ?: "0"
def branchName = parsed.branch
def commitInt = 0
try { commitInt = Integer.parseInt(commitRaw) } catch (Throwable ignored) { commitInt = 0 }
def stampedCommit = commitInt + 1

def usedOverrides = parsed.used ?: []
def usedRoots = getUsedProjectRoots(project)
def likelyTwcHosted = []
if (usedOverrides && usedOverrides.size() > 0) {
    usedOverrides.each { raw ->
        def parsedUsed = parseUsedEntry(String.valueOf(raw))
        if (parsedUsed != null && parsedUsed.name) likelyTwcHosted << parsedUsed
    }
} else {
    usedRoots.each { r -> likelyTwcHosted << [name: r.getName(), branch: null, commit: 0] }
}

INFO("Stamping manifest for containing project '${containingProjectTitle}' at commit ${stampedCommit}.")

def sess = SessionManager.getInstance()
def sessionName = "Manifest Stamp"
boolean openedHere = false
if (!sess.isSessionCreated(project)) {
    sess.createSession(project, sessionName)
    openedHere = true
}

try {
    def ef = project.getElementsFactory()
    Package root = project.getPrimaryModel()

    // Find recursively before creating.
    Package manifestPkg = findByNameRecursive(root, "Manifest", Package) as Package
    if (manifestPkg == null) {
        manifestPkg = createFactoryElement(ef, "Package") as Package
        manifestPkg.setName("Manifest")
        ensureOwnedElement(root, manifestPkg)
        INFO("Created Manifest package.")
    } else {
        INFO("Reusing existing Manifest package.")
    }

    ensureInstanceTable(manifestPkg)

    Class containingBlock = findByNameRecursive(root, containingProjectTitle, Class) as Class
    if (containingBlock == null) {
        containingBlock = createFactoryElement(ef, "Class") as Class
        containingBlock.setName(containingProjectTitle)
        ensureOwnedElement(manifestPkg, containingBlock)
        INFO("Created containing project block '${containingProjectTitle}'.")
    } else {
        INFO("Reusing containing project block '${containingProjectTitle}'.")
    }

    def containingCommitProp = ensureDataProperty(ef, containingBlock, "commitNumber", "Integer")
    def containingBranchProp = ensureDataProperty(ef, containingBlock, "branch", "String")
    def containingTitleProp = ensureDataProperty(ef, containingBlock, "title", "String")

    def usedBlockMap = [:]
    likelyTwcHosted.each { u ->
        def usedName = String.valueOf(u.name)
        Class usedBlock = findByNameRecursive(root, usedName, Class) as Class
        if (usedBlock == null) {
            usedBlock = createFactoryElement(ef, "Class") as Class
            usedBlock.setName(usedName)
            ensureOwnedElement(manifestPkg, usedBlock)
        }

        ensureDataProperty(ef, usedBlock, "commitNumber", "Integer")
        ensureDataProperty(ef, usedBlock, "branch", "String")

        def partName = "used_" + usedName.replaceAll("[^A-Za-z0-9_]", "_")
        ensureProperty(ef, containingBlock, partName, usedBlock, 0, 1)
        usedBlockMap[usedName] = usedBlock
    }

    String dt = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
    String instanceTitle = "Manifest_${dt}_${stampedCommit}"

    def containingInstance = createFactoryElement(ef, "InstanceSpecification")
    containingInstance.setName(instanceTitle)
    containingInstance.getClassifier()?.add(containingBlock)
    ensureOwnedElement(manifestPkg, containingInstance)

    ensureSlotWithValue(ef, containingInstance, containingCommitProp, literalInteger(ef, stampedCommit))
    ensureSlotWithValue(ef, containingInstance, containingBranchProp, literalString(ef, branchName != null ? branchName : ""))
    ensureSlotWithValue(ef, containingInstance, containingTitleProp, literalString(ef, instanceTitle))

    likelyTwcHosted.each { u ->
        def usedName = String.valueOf(u.name)
        Class usedBlock = usedBlockMap[usedName] as Class

        def usedCommitProp = usedBlock.getOwnedAttribute()?.find { it.name == "commitNumber" }
        def usedBranchProp = usedBlock.getOwnedAttribute()?.find { it.name == "branch" }

        def usedInstance = createFactoryElement(ef, "InstanceSpecification")
        usedInstance.setName("${usedName}_snapshot")
        usedInstance.getClassifier()?.add(usedBlock)
        ensureOwnedElement(manifestPkg, usedInstance)

        int usedCommit = 0
        try { usedCommit = Integer.parseInt(String.valueOf(u.commit)) } catch (Throwable ignored) { usedCommit = 0 }
        def usedBranch = (u.branch != null) ? String.valueOf(u.branch) : ""

        ensureSlotWithValue(ef, usedInstance, usedCommitProp, literalInteger(ef, usedCommit))
        ensureSlotWithValue(ef, usedInstance, usedBranchProp, literalString(ef, usedBranch))

        def partName = "used_" + usedName.replaceAll("[^A-Za-z0-9_]", "_")
        def part = containingBlock.getOwnedAttribute()?.find { it.name == partName }
        if (part != null) {
            ensureSlotWithValue(ef, containingInstance, part, instanceValue(ef, usedInstance))
        }
    }

    INFO("Manifest stamp complete: ${instanceTitle}")
    if (openedHere) sess.closeSession(project)
} catch (Throwable t) {
    ERR("Stamp failed: ${t.class.simpleName}: ${t.message}")
    if (openedHere && sess.isSessionCreated(project)) sess.cancelSession(project)
    throw t
}
