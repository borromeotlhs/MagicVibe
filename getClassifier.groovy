import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.ui.SelectionProvider

def app = Application.getInstance()
def project = app.getProject()
def log = app.getGUILog()

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

try {
    // A) looks like a classifier
    if (element.respondsTo("getGeneralization") || element.respondsTo("getOwnedAttribute")) {
        return element
    }

    // B) typed element / property / port (proxy ports are Properties)
    if (element.respondsTo("getType")) {
        def t = element.getType()
        if (t != null) return t
    }

    // C) instance specification
    if (element.respondsTo("getClassifier")) {
        def cs = element.getClassifier()
        if (cs != null && !cs.isEmpty()) return cs.get(0)
    }
} catch (e) {
    log.log("[getClassifier] Error while resolving classifier/type: " + e.toString())
}

return element