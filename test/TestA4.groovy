import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.MagicDrawProfile

// Simple DFS to collect all owned elements starting from a root Element
private void collectOwned(Element root, Collection<Element> out) {
    if (root == null) return
    if (!out.add(root)) return  // avoid cycles / duplicates

    try {
        def owned = root.getOwnedElement()
        if (owned != null) {
            owned.each { child ->
                if (child instanceof Element) {
                    collectOwned((Element) child, out)
                }
            }
        }
    } catch (Throwable ignore) {
        // If something weird happens on getOwnedElement(), just stop descending this branch
    }
}

// A4.1: Scan project for AttachedFile comments and log details
tests << new IGTest(
    id: "A4.1",
    description: "Scan primary model for elements marked as AttachedFile (MagicDrawProfile.isAttachedFile)",
    run: {
        def app  = Application.getInstance()
        def proj = app.getProject()
        def log  = app.getGUILog()

        log.log("[A4.1][WARN] This test is most informative when run on a project that actually has comments with attached files (MagicDraw AttachedFile).")

        if (proj == null) {
            log.log("[A4.1] No active project; cannot inspect attached files.")
            return false
        }

        def primary
        try {
            primary = proj.getPrimaryModel()
        } catch (Throwable t) {
            log.log("[A4.1] ERROR calling getPrimaryModel(): ${t.class.simpleName}: ${t.message}")
            return false
        }

        if (!(primary instanceof Element)) {
            log.log("[A4.1] Primary model is null or not an Element; nothing to scan.")
            return false
        }

        // Collect all elements in the primary model via DFS
        Collection<Element> all = new LinkedHashSet<Element>()
        collectOwned((Element) primary, all)

        log.log("[A4.1] Scanning ${all.size()} element(s) from primary model for AttachedFile markers...")

        def attached = []
        all.each { Element e ->
            try {
                if (MagicDrawProfile.isAttachedFile(e)) {
                    attached << e
                }
            } catch (Throwable ignore) {
                // Skip elements that cause issues in isAttachedFile
            }
        }

        log.log("[A4.1][INFO] Detected ${attached.size()} element(s) that MagicDrawProfile.isAttachedFile(e) reports as attached files.")

        int idx = 0
        attached.each { Element e ->
            // Element Server ID (from getID())
            String serverId = "<no-element-server-id>"
            try {
                if (e.respondsTo("getID")) {
                    serverId = e.getID()
                }
            } catch (Throwable ignore) { }

            // Name if available
            String name = null
            try {
                if (e.respondsTo("getName")) {
                    name = e.getName()
                }
            } catch (Throwable ignore) { }
            if (!name) {
                name = e.toString()
            }

            // If it's a Comment-like element, try to get body text
            String body = null
            try {
                if (e.respondsTo("getBody")) {
                    body = e.getBody()
                }
            } catch (Throwable ignore) { }
            if (body == null) {
                body = "<no-body-or-not-a-comment>"
            }

            // Owner info
            String ownerName = "<no-owner-name>"
            String ownerId   = "<no-owner-id>"
            try {
                def owner = e.getOwner()
                if (owner != null) {
                    try {
                        if (owner.respondsTo("getName")) {
                            ownerName = owner.getName()
                        }
                    } catch (Throwable ignore2) { }
                    try {
                        if (owner.respondsTo("getID")) {
                            ownerId = owner.getID()
                        }
                    } catch (Throwable ignore3) { }
                }
            } catch (Throwable ignoreOwner) { }

            log.log(
                "[A4.1] [attached ${idx}] " +
                "name='${name}', " +
                "elementServerId(from getID())='${serverId}', " +
                "ownerName='${ownerName}', ownerId='${ownerId}', " +
                "body='${body}', " +
                "type=${e.class.name}"
            )
            idx++
        }

        // Mechanism executed successfully, regardless of how many attached files exist
        return true
    }
)
