this.metaClass = null
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.annotation.AnnotationManager
import com.nomagic.magicdraw.validation.ValidationConstants
import com.nomagic.magicdraw.ui.notification.Notification
import com.nomagic.magicdraw.ui.notification.NotificationManager
import com.nomagic.magicdraw.uml.symbols.PresentationElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element

// ============================================================
// validationErrorsToCSV.groovy
// Exports CURRENT validation annotations (VALIDATION_ONLY) to CSV
// and posts the saved path to the Notification Window.
// Writes TargetURI/OwnerURI as Excel HYPERLINK formulas wrapping mdel:// links for click-through.
// Robust against Elements that do NOT have getQualifiedName().
// ============================================================

// ---------- CSV helpers ----------
String csvCell(def v) {
    String s = (v == null) ? "" : v.toString()
    s = s.replace("\"", "\"\"")          // escape quotes
    return "\"${s}\""                    // quote everything
}

// Some MagicDraw element types (e.g., GeneralizationImpl) are Elements but not NamedElements,
// so they may NOT implement getQualifiedName(). Use method-existence checks.
String safeQualifiedName(def obj) {
    if (obj == null) return ""
    try {
        if (obj.metaClass?.respondsTo(obj, "getQualifiedName")) {
            def qn = obj.getQualifiedName()
            return qn == null ? "" : qn.toString()
        }
    } catch (Throwable ignore) { }
    return ""
}

String safeName(def obj) {
    if (obj == null) return ""
    try {
        if (obj.metaClass?.respondsTo(obj, "getName")) {
            def n = obj.getName()
            return n == null ? "" : n.toString()
        }
    } catch (Throwable ignore) { }
    return ""
}

String safeTypeName(def obj) {
    return obj == null ? "" : obj.getClass().getSimpleName()
}

String safeElementId(Element e) {
    try {
        def id = e?.getID()
        return id == null ? "" : id.toString()
    } catch (Throwable ignore) {
        return ""
    }
}



String excelFormulaEscape(String value) {
    return value == null ? "" : value.replace("\"", "\"\"")
}

String safeElementHyperlink(Element e, String label) {
    def id = safeElementId(e)
    if (id == null || id.trim().isEmpty()) return ""
    String linkLabel = (label == null || label.trim().isEmpty()) ? id : label
    return "=HYPERLINK(\"mdel://${excelFormulaEscape(id)}\",\"${excelFormulaEscape(linkLabel)}\")"
}

String safeProjectName(def modelRoot) {
    if (modelRoot == null) return ""
    try {
        if (modelRoot.metaClass?.respondsTo(modelRoot, "getName")) {
            def n = modelRoot.getName()
            if (n != null && n.toString().trim() != "") return n.toString()
        }
    } catch (Throwable ignore) { }
    return safeTypeName(modelRoot)
}

Element safeOwnerElement(Element e) {
    if (e == null) return null
    try {
        def o = e.getOwner()
        return (o instanceof Element) ? (Element) o : null
    } catch (Throwable ignore) {
        return null
    }
}

// Build a containment chain without assuming NamedElement APIs exist everywhere.
String ownerChain(Element e) {
    def parts = []
    def cur = e
    while (cur != null) {
        def n = safeName(cur)
        parts << (n != "" ? n : safeTypeName(cur))
        cur = safeOwnerElement(cur)
    }
    return parts.reverse().join(" / ")
}

// Best-effort "display path" for a target element.
String displayPath(Element e) {
    def qn = safeQualifiedName(e)
    if (qn != "") return qn
    def n = safeName(e)
    if (n != "") return n
    return safeTypeName(e)
}

List<Element> getUsedProjectRoots(Project proj) {
    if (proj == null) return []
    def primary = proj.getPrimaryModel()
    def roots = proj.getModels()
    if (roots == null) return []

    def used = []
    roots.each { m ->
        if (m != null && !m.is(primary) && m instanceof Element) {
            used << (Element) m
        }
    }
    return used
}

Element getProjectRootForElement(Element e, Element primaryRoot, List<Element> usedRoots) {
    if (e == null) return null
    def cur = e
    while (cur != null) {
        if (primaryRoot != null && cur.is(primaryRoot)) return primaryRoot
        if (usedRoots != null) {
            for (Element u : usedRoots) {
                if (u != null && cur.is(u)) return u
            }
        }
        cur = safeOwnerElement(cur)
    }
    return null
}


Element unwrapToElement(def target) {
    if (target instanceof Element) return (Element) target
    if (target instanceof PresentationElement) {
        try {
            def backing = target.getElement()
            if (backing instanceof Element) return (Element) backing
        } catch (Throwable ignore) { }
    }
    return null
}


String safeRuleName(def annotation) {
    if (annotation == null) return ""
    try {
        if (annotation.metaClass?.respondsTo(annotation, "getRule")) {
            def r = annotation.getRule()
            if (r != null) {
                if (r.metaClass?.respondsTo(r, "getName")) {
                    def rn = r.getName()
                    if (rn != null) return rn.toString()
                }
                return r.toString()
            }
        }
    } catch (Throwable ignore) { }
    return ""
}

String projectScopeLabel(Element e, Element primaryRoot, List<Element> usedRoots) {
    def root = getProjectRootForElement(e, primaryRoot, usedRoots)
    if (root == null) return ""
    def scope = (primaryRoot != null && root.is(primaryRoot)) ? "PRIMARY" : "USED"
    return "${scope}:${safeProjectName(root)}"
}

// ---------- main ----------
Project project = Application.getInstance().getProject()
def guiLog = Application.getInstance().getGUILog()

AnnotationManager am = AnnotationManager.getInstance(project)
def subset = ValidationConstants.VALIDATION_ONLY
Element primaryRoot = project?.getPrimaryModel()
List<Element> usedRoots = getUsedProjectRoots(project)

// Save under: <working dir>/logs/validation_findings_YYYYMMDD_HHMMSS.csv
def baseDir = new File(System.getProperty("user.dir"))
def logsDir = new File(baseDir, "logs")
if (!logsDir.exists()) logsDir.mkdirs()

def ts = java.time.LocalDateTime.now().format(
    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
)

def outFile = new File(logsDir, "validation_findings_${ts}.csv")

int rowCount = 0

outFile.withWriter("UTF-8") { w ->
    // header
    w.writeLine([
        "TargetDisplayPath",
        "TargetName",
        "TargetQualifiedName",
        "TargetType",
        "TargetID",
        "TargetURI",
        "TargetProject",
        "OwnerName",
        "OwnerQualifiedName",
        "OwnerType",
        "OwnerID",
        "OwnerURI",
        "OwnerProject",
        "OwnerChain",
        "Severity",
        "Rule",
        "Kind",
        "Message"
    ].collect { csvCell(it) }.join(","))

    // rows (CURRENT annotations)
    def targets = am.getAnnotatedElements(subset)
    targets.each { t ->
        // Keep annotation lookup keyed by original target object, but normalize target element for export.
        Element e = unwrapToElement(t)
        def annos = am.getAnnotations(t, subset)

        Element owner = (e != null) ? safeOwnerElement(e) : null

        annos.each { a ->
            def row = [
                e ? displayPath(e) : (t?.toString() ?: ""),
                e ? safeName(e) : "",
                e ? safeQualifiedName(e) : "",
                e ? safeTypeName(e) : (t == null ? "" : t.getClass().getSimpleName()),
                e ? safeElementId(e) : "",
                e ? safeElementHyperlink(e, safeName(e)) : "",
                e ? projectScopeLabel(e, primaryRoot, usedRoots) : "",
                owner ? safeName(owner) : "",
                owner ? safeQualifiedName(owner) : "",
                owner ? safeTypeName(owner) : "",
                owner ? safeElementId(owner) : "",
                owner ? safeElementHyperlink(owner, safeName(owner)) : "",
                owner ? projectScopeLabel(owner, primaryRoot, usedRoots) : "",
                e ? ownerChain(e) : "",
                a?.getSeverity()?.toString() ?: "",
                safeRuleName(a),
                a?.getKind()?.toString() ?: "",
                a?.getText()?.toString() ?: ""
            ]
            w.writeLine(row.collect { csvCell(it) }.join(","))
            rowCount++
        }
    }
}

// Build the absolute path for display
def absPath = outFile.getCanonicalPath()

// 1) Message Window
guiLog.log("Validation findings CSV saved (" + rowCount + " rows): " + absPath)

// 2) Notification Window entry (and open the window)
def noteId = "validationErrorsToCSV_" + ts
def note = new Notification(
    noteId,
    "Validation findings exported",
    "CSV saved (" + rowCount + " rows): " + absPath
)
NotificationManager.getInstance().openNotificationWindow(note, true)

// Also print to macro console
println("Validation findings CSV saved (" + rowCount + " rows): " + absPath)
