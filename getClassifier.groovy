import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.ui.SelectionProvider

def app = Application.getInstance()
def project = app.getProject()
def log = app.getGUILog()

def openSpecificationWindow = { target ->
    if (target == null) return false

    try {
        def mgrClass = Class.forName("com.nomagic.magicdraw.ui.dialogs.specifications.SpecificationDialogManager")
        def getManager = mgrClass.getMethod("getManager")
        def mgr = getManager.invoke(null)

        if (mgr == null) return false

        // Try the most common method signatures first.
        def candidates = [
            [project, target],
            [target],
            [project, target, Boolean.TRUE],
            [target, Boolean.TRUE],
        ]

        for (args in candidates) {
            def m = mgr.metaClass.respondsTo(mgr, "editSpecification", args as Object[])
            if (m && !m.isEmpty()) {
                mgr.invokeMethod("editSpecification", args as Object[])
                return true
            }
        }

        // Fallback to invokeMethod in case respondsTo misses overloaded signatures.
        try {
            mgr.invokeMethod("editSpecification", [project, target] as Object[])
            return true
        } catch (ignored) {}

        try {
            mgr.invokeMethod("editSpecification", [target] as Object[])
            return true
        } catch (ignored) {}
    } catch (Throwable t) {
        log.log("[getClassifier] Could not open specification window: ${t}")
    }

    return false
}

if (project == null) {
    log.log("[getClassifier] No open project.")
    return null
}

def sp = SelectionProvider.getInstance(project)
def main = sp?.getMainElement()

if (main == null) {
    log.log("[getClassifier] Nothing selected.")
    return null
}

// If it's a diagram symbol (or similar wrapper), unwrap to the underlying model element when possible
def element = main
try {
    if (main.respondsTo("getElement")) {
        def unwrapped = main.getElement()
        if (unwrapped != null) element = unwrapped
    }
} catch (ignored) {}

// Try to return the "classifier" of whatever is selected:
//
// A) If you selected a classifier (Block/Class/etc), return it.
// B) If you selected a part property or proxy port (Property / TypedElement), return its type (often a Classifier).
// C) If you selected an instance spec, return its first classifier.
// D) Otherwise return the element itself.

def target = element

try {
    // A) looks like a classifier
    if (element.respondsTo("getGeneralization") || element.respondsTo("getOwnedAttribute")) {
        target = element
    } else {
        // B) typed element / property / port (proxy ports are Properties)
        if (element.respondsTo("getType")) {
            def t = element.getType()
            if (t != null) {
                target = t
            } else {
                // C) instance specification
                if (element.respondsTo("getClassifier")) {
                    def cs = element.getClassifier()
                    if (cs != null && !cs.isEmpty()) target = cs.get(0)
                }
            }
        } else {
            // C) instance specification
            if (element.respondsTo("getClassifier")) {
                def cs = element.getClassifier()
                if (cs != null && !cs.isEmpty()) target = cs.get(0)
            }
        }
    }
} catch (e) {
    log.log("[getClassifier] Error while resolving classifier/type: " + e.toString())
}

if (!openSpecificationWindow(target)) {
    log.log("[getClassifier] Could not open specification window; resolved element: ${target}")
}

return target
