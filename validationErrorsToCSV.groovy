import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.uml.symbols.PresentationElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element

/**
 * Escape CSV field.
 */
String csv(def v) {
    String s = (v == null) ? "" : String.valueOf(v)
    '"' + s.replace('"', '""') + '"'
}

String simpleType(def x) {
    if (x == null) return ""
    String n = x.getClass().getName()
    int i = n.lastIndexOf('.')
    return (i >= 0) ? n.substring(i + 1) : n
}

String displayPath(Element e) {
    if (e == null) return ""
    def qn = e.hasProperty('qualifiedName') ? e.qualifiedName : null
    if (qn) return qn
    def n = e.hasProperty('name') ? e.name : null
    return n ?: e.toString()
}

String modelProjectName(Project p, Element e) {
    if (p == null || e == null) return ""
    def roots = p.getModels()
    if (roots == null) return ""
    for (def r : roots) {
        if (r != null) {
            def cur = e
            while (cur != null) {
                if (cur == r) {
                    return (r.hasProperty('name') ? r.name : "") ?: ""
                }
                def o = cur.getOwner()
                cur = (o instanceof Element) ? (Element) o : null
            }
        }
    }
    return ""
}

Element ownerElement(Element e) {
    if (e == null) return null
    def owner = e.getOwner()
    return (owner instanceof Element) ? (Element) owner : null
}

// Diagram symbols (ConnectorView, etc.) are PresentationElements; validation annotations may target
// the symbol object, so we unwrap to its backing UML model Element to emit stable mdel:// links.
Element unwrapToElement(def t) {
    if (t instanceof Element) return (Element) t
    if (t instanceof PresentationElement) {
        def me = t.getElement()
        if (me instanceof Element) return (Element) me
    }
    return null
}

Map targetColumns(Project project, def t) {
    Element e = unwrapToElement(t)
    Element owner = ownerElement(e)

    String targetId = e?.getID() ?: ""
    String ownerId = owner?.getID() ?: ""

    return [
        TargetDisplayPath: e ? displayPath(e) : String.valueOf(t),
        TargetName       : e?.hasProperty('name') ? (e.name ?: "") : "",
        TargetType       : e ? simpleType(e) : simpleType(t),
        TargetID         : targetId,
        TargetURI        : targetId ? "mdel://${targetId}" : "",
        TargetProject    : modelProjectName(project, e),
        OwnerName        : owner?.hasProperty('name') ? (owner.name ?: "") : "",
        OwnerType        : owner ? simpleType(owner) : "",
        OwnerID          : ownerId,
        OwnerURI         : ownerId ? "mdel://${ownerId}" : ""
    ]
}

/**
 * Example row builder. Keep annotation lookup keyed to original validation target object.
 */
List<Map> buildRows(def am, Collection targets, def subset) {
    Project project = Application.getInstance()?.getProject()
    List<Map> rows = []

    targets?.each { t ->
        def annos = am.getAnnotations(t, subset)
        Map cols = targetColumns(project, t)
        annos?.each { a ->
            rows << cols + [
                Severity : a?.severity?.toString() ?: "",
                Rule      : a?.rule?.name ?: "",
                Message   : a?.text ?: ""
            ]
        }
    }

    return rows
}
