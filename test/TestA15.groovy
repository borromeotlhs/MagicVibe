// TestA15.groovy
//
// Assumption 15: Selection retrieval & classifier-resolution in WORK_ENV is macro-safe
// using SelectionProvider + duck-typing (minimal imports).
//
// NOTE: These are acceptance tests for issues surfaced during getClassifier.groovy debugging:
// - SelectionManager import failure
// - Wrong BaseElement import path
// - Need for minimal imports + duck typing
//
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.ui.SelectionProvider

tests << new IGTest(
    id: "A15.1",
    description: "SelectionProvider class is resolvable in WORK_ENV",
    run: {
        try {
            Class.forName("com.nomagic.magicdraw.ui.SelectionProvider")
            println "[A15.1] SelectionProvider is resolvable."
            return true
        } catch (Throwable t) {
            println "[A15.1] SelectionProvider NOT resolvable: ${t}"
            return false
        }
    }
)

tests << new IGTest(
    id: "A15.2",
    description: "Selection retrieval does not require SelectionManager (it may be absent)",
    run: {
        try {
            Class.forName("com.nomagic.magicdraw.ui.SelectionManager")
            println "[A15.2] SelectionManager is resolvable in this build (OK, but not required)."
            return true
        } catch (Throwable t) {
            println "[A15.2] SelectionManager NOT resolvable (expected in WORK_ENV); SelectionProvider will be used instead."
            return true
        }
    }
)

tests << new IGTest(
    id: "A15.3",
    description: "SelectionProvider.getInstance(project).getMainElement() is callable (may return null)",
    run: {
        Project proj = Application.getInstance().getProject()
        if (proj == null) {
            println "[A15.3] No active Project."
            return false
        }

        def sp = SelectionProvider.getInstance(proj)
        if (sp == null) {
            println "[A15.3] SelectionProvider.getInstance(proj) returned null."
            return false
        }

        try {
            def main = sp.getMainElement()
            println "[A15.3] getMainElement() callable; returned: " + (main == null ? "null" : main.getClass().getName())
            return true
        } catch (Throwable t) {
            println "[A15.3] Error calling getMainElement(): ${t}"
            return false
        }
    }
)

tests << new IGTest(
    id: "A15.4",
    description: "If selection wrapper respondsTo(getElement), unwrapping is callable without throwing",
    run: {
        Project proj = Application.getInstance().getProject()
        if (proj == null) {
            println "[A15.4] No active Project."
            return false
        }

        def sp = SelectionProvider.getInstance(proj)
        def main = null
        try {
            main = sp?.getMainElement()
        } catch (Throwable t) {
            println "[A15.4] Error obtaining main element: ${t}"
            return false
        }

        if (main == null) {
            println "[A15.4] Nothing selected (N/A) - PASS."
            return true
        }

        boolean hasGetElement = false
        try { hasGetElement = main.respondsTo("getElement") } catch (Throwable ignored) {}

        if (!hasGetElement) {
            println "[A15.4] Selected object has no getElement() (N/A) - PASS."
            return true
        }

        try {
            def el = main.getElement()
            println "[A15.4] getElement() callable; returned: " + (el == null ? "null" : el.getClass().getName())
            return true
        } catch (Throwable t) {
            println "[A15.4] getElement() threw: ${t}"
            return false
        }
    }
)

tests << new IGTest(
    id: "A15.5",
    description: "If selected element respondsTo(getType), getType() is callable without throwing (proxy port / part property support)",
    run: {
        Project proj = Application.getInstance().getProject()
        if (proj == null) {
            println "[A15.5] No active Project."
            return false
        }

        def sp = SelectionProvider.getInstance(proj)
        def main = null
        try { main = sp?.getMainElement() } catch (Throwable t) {
            println "[A15.5] Error obtaining main element: ${t}"
            return false
        }

        if (main == null) {
            println "[A15.5] Nothing selected (N/A) - PASS."
            return true
        }

        // unwrap if possible
        def element = main
        try {
            if (main.respondsTo("getElement")) {
                def unwrapped = main.getElement()
                if (unwrapped != null) element = unwrapped
            }
        } catch (Throwable ignored) {}

        boolean hasGetType = false
        try { hasGetType = element.respondsTo("getType") } catch (Throwable ignored) {}

        if (!hasGetType) {
            println "[A15.5] Selected element has no getType() (N/A) - PASS. Selected=" + element.getClass().getName()
            return true
        }

        try {
            def t = element.getType()
            println "[A15.5] getType() callable; returned: " + (t == null ? "null" : t.getClass().getName())
            return true
        } catch (Throwable ex) {
            println "[A15.5] getType() threw: ${ex}"
            return false
        }
    }
)
