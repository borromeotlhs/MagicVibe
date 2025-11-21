import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element

// ---------------------------------------------------------------------
// Helper: encapsulated discovery of used project roots
// ---------------------------------------------------------------------
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
        return []  // no project, no used projects
    }

    def primary
    def roots

    try {
        primary = proj.getPrimaryModel()
    } catch (Throwable t) {
        // If we can't get primary, we can't reliably distinguish used roots
        Application.getInstance().getGUILog().log(
            "[A6.1] ERROR calling Project.getPrimaryModel(): ${t.class.simpleName}: ${t.message}"
        )
        return []
    }

    try {
        roots = proj.getModels()
    } catch (Throwable t) {
        Application.getInstance().getGUILog().log(
            "[A6.1] ERROR calling Project.getModels(): ${t.class.simpleName}: ${t.message}"
        )
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

// ---------------------------------------------------------------------
// Test A6.1: Reveal & verify the used-project pattern via getUsedProjectRoots
// ---------------------------------------------------------------------
tests << new IGTest(
    id: "A6.1",
    description: "Encapsulated discovery of used project roots via Project.getModels() / getPrimaryModel() pattern",
    run: {
        def app  = Application.getInstance()
        def proj = app.getProject()
        def log  = app.getGUILog()

        if (proj == null) {
            log.log("[A6.1] No active project; cannot inspect used projects.")
            return false
        }

        log.log("[A6.1][WARN] This test is most informative when run on a project that actually has used projects (requirements, libraries, etc.).")

        // Call the encapsulated helper
        def used = getUsedProjectRoots(proj)
        log.log("[A6.1] getUsedProjectRoots(project) returned ${used.size()} candidate used project root(s).")

        // Log primary model for context
        def primary = null
        try {
            primary = proj.getPrimaryModel()
        } catch (Throwable t) {
            log.log("[A6.1] ERROR re-calling getPrimaryModel(): ${t.class.simpleName}: ${t.message}")
        }

        if (primary != null && primary instanceof Element) {
            String primaryId = "<no-id>"
            try {
                if (primary.respondsTo("getID")) {
                    primaryId = primary.getID()
                }
            } catch (Throwable ignore) { }

            log.log("[A6.1] Primary model: name='${primary.name}', elementServerId(from getID())='${primaryId}', type=${primary.class.name}")
        }

        // Log each used root with humanName, ID, and type
        int idx = 0
        used.each { Element m ->
            String id = "<no-id>"
            try {
                if (m.respondsTo("getID")) {
                    id = m.getID()
                }
            } catch (Throwable ignore) { }

            String humanName = null
            try {
                // Use same pattern as SLMNP: ask project for human name if available
                if (proj.metaClass.respondsTo(proj, "getHumanName", Object)) {
                    humanName = proj.getHumanName(m)
                }
            } catch (Throwable ignore) { }

            if (!humanName) {
                try {
                    humanName = m.respondsTo("getName") ? m.getName() : null
                } catch (Throwable ignore) { }
            }
            if (!humanName) {
                humanName = "<used model>"
            }

            log.log(
                "[A6.1] [used ${idx}] humanName='${humanName}', " +
                "elementServerId(from getID())='${id}', type=${m.class.name}"
            )
            idx++
        }

        // Helper executed successfully; the presence/absence of used projects is informational.
        return true
    }
)
