import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element

// =====================================================================================
// Helper: encapsulated discovery of used project roots (same pattern as A6)
// =====================================================================================
/**
 * Returns a List of "used project" root models for the given project.
 *
 * Pattern (WORK_ENV):
 * - primary = project.getPrimaryModel()
 * - roots   = project.getModels()
 * - used    = all roots where m != null && !m.is(primary)
 */
List<Element> getUsedProjectRoots(Project proj) {
    if (proj == null) {
        return []
    }

    def app = Application.getInstance()
    def log = app.getGUILog()

    def primary
    def roots

    try {
        primary = proj.getPrimaryModel()
    } catch (Throwable t) {
        log.log("[A11.2] ERROR calling Project.getPrimaryModel(): ${t.class.simpleName}: ${t.message}")
        return []
    }

    try {
        roots = proj.getModels()
    } catch (Throwable t) {
        log.log("[A11.2] ERROR calling Project.getModels(): ${t.class.simpleName}: ${t.message}")
        return []
    }

    if (roots == null) {
        return []
    }

    def used = []
    roots.each { m ->
        if (m != null && !m.is(primary)) {
            used << m
        }
    }
    return used
}

// =====================================================================================
// A11.1 – Check for Project.isElementInPrimaryModel(Element)
// =====================================================================================
tests << new IGTest(
    id: "A11.1",
    description: "Reflectively check for presence of Project.isElementInPrimaryModel(Element) and confirm it is NOT available in WORK_ENV",
    run: {
        def app = Application.getInstance()
        def log = app.getGUILog()

        log.log("[A11.1] Inspecting Project.class for isElementInPrimaryModel(Element) method...")

        boolean found = false
        def projClass = Project

        try {
            projClass.methods.each { m ->
                if (m.name == "isElementInPrimaryModel" &&
                    m.parameterTypes?.size() == 1 &&
                    Element.isAssignableFrom(m.parameterTypes[0])) {
                    found = true
                    log.log("[A11.1] Found candidate method: ${m}")
                }
            }
        } catch (Throwable t) {
            log.log("[A11.1] ERROR while reflecting Project.class: ${t.class.simpleName}: ${t.message}")
            // If reflection itself is broken, treat as failure.
            return false
        }

        if (found) {
            log.log("[A11.1] Project.isElementInPrimaryModel(Element) IS present on this Project class => Assumption 11 (missing method) is FALSE.")
            return false
        } else {
            log.log("[A11.1] Project.isElementInPrimaryModel(Element) NOT found on this Project class => Assumption 11 (missing method) CONFIRMED for WORK_ENV.")
            return true
        }
    }
)

// =====================================================================================
// A11.2 – Reveal and verify primary vs used-model membership via Element.getModel()
// =====================================================================================
tests << new IGTest(
    id: "A11.2",
    description: "Verify primary vs used project membership pattern via proj.getPrimaryModel() == elem.getModel()",
    run: {
        def app  = Application.getInstance()
        def proj = app.getProject()
        def log  = app.getGUILog()

        if (proj == null) {
            log.log("[A11.2] No active project; cannot inspect model membership.")
            return false
        }

        log.log("[A11.2][WARN] This test is most informative when run on a project that has used projects (requirements, libraries, etc.).")

        // Get primary and used roots
        def primary
        try {
            primary = proj.getPrimaryModel()
        } catch (Throwable t) {
            log.log("[A11.2] ERROR calling Project.getPrimaryModel(): ${t.class.simpleName}: ${t.message}")
            return false
        }

        if (!(primary instanceof Element)) {
            log.log("[A11.2] Primary model is not an Element; cannot test Element.getModel() pattern.")
            return false
        }

        def usedRoots = getUsedProjectRoots(proj)
        log.log("[A11.2] getUsedProjectRoots(project) returned ${usedRoots.size()} candidate used project root(s).")

        // Helper: safe getModel() with logging
        def safeGetModel = { Element e, String label ->
            try {
                def modelObj = e.getModel()
                if (modelObj == null) {
                    log.log("[A11.2] [${label}] getModel() returned null.")
                    return null
                }
                String mid = "<no-id>"
                String mname = "<no-name>"
                if (modelObj instanceof Element) {
                    try {
                        if (modelObj.metaClass.respondsTo(modelObj, "getID")) {
                            mid = modelObj.getID()
                        }
                    } catch (Throwable ignore) {}
                    try {
                        if (modelObj.metaClass.respondsTo(modelObj, "getName")) {
                            mname = modelObj.getName()
                        } else {
                            mname = modelObj.toString()
                        }
                    } catch (Throwable ignore) {}
                    log.log("[A11.2] [${label}] getModel() => name='${mname}', elementServerId(from getID())='${mid}', type=${modelObj.class.name}")
                } else {
                    log.log("[A11.2] [${label}] getModel() returned non-Element object: ${modelObj} (type=${modelObj.class.name})")
                }
                return modelObj
            } catch (Throwable t) {
                log.log("[A11.2] [${label}] ERROR calling getModel(): ${t.class.simpleName}: ${t.message}")
                return null
            }
        }

        // --- Check primary root and (optionally) one owned element under primary ---
        String primaryId = "<no-id>"
        try {
            if (primary.metaClass.respondsTo(primary, "getID")) {
                primaryId = primary.getID()
            }
        } catch (Throwable ignore) {}

        log.log("[A11.2] Primary model root: name='${primary.name}', elementServerId(from getID())='${primaryId}', type=${primary.class.name}")

        def primaryModelFromRoot = safeGetModel(primary, "primary-root")
        boolean ok = true

        if (primaryModelFromRoot != null && primaryModelFromRoot.is(primary)) {
            log.log("[A11.2] [primary-root] getModel() == primaryModel => OK")
        } else if (primaryModelFromRoot != null) {
            log.log("[A11.2] [primary-root] getModel() != primaryModel => Pattern for root does NOT match assumption.")
            ok = false
        }

        // Try one owned element under primary, if available
        def primaryChild = null
        try {
            def owned = primary.getOwnedElement()
            if (owned != null && !owned.isEmpty()) {
                primaryChild = owned.iterator().next()
            }
        } catch (Throwable ignore) {}

        if (primaryChild != null) {
            String cid = "<no-id>"
            try {
                if (primaryChild.metaClass.respondsTo(primaryChild, "getID")) {
                    cid = primaryChild.getID()
                }
            } catch (Throwable ignore) {}
            String cname = "<no-name>"
            try {
                cname = primaryChild.metaClass.respondsTo(primaryChild, "getName") ?
                        primaryChild.getName() : primaryChild.toString()
            } catch (Throwable ignore) {}

            log.log("[A11.2] Primary child sample: name='${cname}', elementServerId(from getID())='${cid}', type=${primaryChild.class.name}")
            def primaryModelFromChild = safeGetModel(primaryChild, "primary-child")
            if (primaryModelFromChild != null && primaryModelFromChild.is(primary)) {
                log.log("[A11.2] [primary-child] getModel() == primaryModel => OK")
            } else if (primaryModelFromChild != null) {
                log.log("[A11.2] [primary-child] getModel() != primaryModel => Pattern for primary child does NOT match assumption.")
                ok = false
            }
        } else {
            log.log("[A11.2] No owned elements under primary root found for sampling; skipping primary-child check.")
        }

        // --- Check used roots (and optionally a child under each) ---
        int idx = 0
        usedRoots.each { Element ur ->
            String urId = "<no-id>"
            try {
                if (ur.metaClass.respondsTo(ur, "getID")) {
                    urId = ur.getID()
                }
            } catch (Throwable ignore) {}
            String urName = "<no-name>"
            try {
                urName = ur.metaClass.respondsTo(ur, "getName") ?
                         ur.getName() : ur.toString()
            } catch (Throwable ignore) {}

            log.log("[A11.2] Used root [${idx}]: name='${urName}', elementServerId(from getID())='${urId}', type=${ur.class.name}")

            def modelFromUsedRoot = safeGetModel(ur, "used-root-${idx}")
            if (modelFromUsedRoot != null && modelFromUsedRoot.is(primary)) {
                log.log("[A11.2] [used-root-${idx}] getModel() == primaryModel => Pattern mismatch (used root should not be in primary).")
                ok = false
            }

            // Optional: sample one child under each used root if available
            def usedChild = null
            try {
                def owned = ur.getOwnedElement()
                if (owned != null && !owned.isEmpty()) {
                    usedChild = owned.iterator().next()
                }
            } catch (Throwable ignore) {}

            if (usedChild != null) {
                String ucId = "<no-id>"
                try {
                    if (usedChild.metaClass.respondsTo(usedChild, "getID")) {
                        ucId = usedChild.getID()
                    }
                } catch (Throwable ignore) {}
                String ucName = "<no-name>"
                try {
                    ucName = usedChild.metaClass.respondsTo(usedChild, "getName") ?
                             usedChild.getName() : usedChild.toString()
                } catch (Throwable ignore) {}

                log.log("[A11.2] Used root [${idx}] child sample: name='${ucName}', elementServerId(from getID())='${ucId}', type=${usedChild.class.name}")
                def modelFromUsedChild = safeGetModel(usedChild, "used-child-${idx}")
                if (modelFromUsedChild != null && modelFromUsedChild.is(primary)) {
                    log.log("[A11.2] [used-child-${idx}] getModel() == primaryModel => Pattern mismatch (used child should not be in primary).")
                    ok = false
                }
            } else {
                log.log("[A11.2] Used root [${idx}] has no owned elements for sampling; skipping child check.")
            }

            idx++
        }

        if (ok) {
            log.log("[A11.2] Element.getModel() behavior is consistent with pattern: proj.getPrimaryModel() == elem.getModel() for primary elements, and != primary for used roots / children examined.")
            return true
        } else {
            log.log("[A11.2] Detected mismatches in getModel() pattern; Assumption 11 (simple equality test) does NOT fully hold for all sampled elements.")
            return false
        }
    }
)
