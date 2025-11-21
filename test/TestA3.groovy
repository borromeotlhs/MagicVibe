import com.nomagic.magicdraw.core.Application

// A3.1: Neutral probe of project models, using the same pattern as SLMNP.pick()
tests << new IGTest(
    id: "A3.1",
    description: "Inspect Project.getPrimaryModel() and Project.getModels(), log primary vs non-primary (S L M N P pattern)",
    run: {
        def app  = Application.getInstance()
        def proj = app.getProject()
        def log  = app.getGUILog()

        // Warning now belongs to this test only
        log.log("[A3.1][WARN] This test is most informative when run on a project that actually has used projects (requirements, libraries, etc.).")

        if (proj == null) {
            log.log("[A3.1] No active project; cannot inspect models.")
            return false
        }

        def primary
        def roots
        try {
            primary = proj.getPrimaryModel()
            roots   = proj.getModels()
        } catch (Throwable t) {
            log.log("[A3.1] ERROR calling getPrimaryModel()/getModels(): ${t.class.simpleName}: ${t.message}")
            return false
        }

        if (roots == null) {
            log.log("[A3.1] Project.getModels() returned null.")
            return false
        }

        log.log("[A3.1] Project.getModels() returned ${roots.size()} root object(s).")
        if (primary == null) {
            log.log("[A3.1] Project.getPrimaryModel() returned null.")
        } else {
            log.log("[A3.1] Project.getPrimaryModel(): ${primary} (type=${primary.class.name})")
        }

        // Log *all* roots and whether each is identical to primary
        int idx = 0
        roots.each { m ->
            if (m == null) {
                log.log("  [root ${idx}] <null>")
            } else {
                boolean isPrimary = (m.is(primary))

                String name = null
                try {
                    if (m.respondsTo("getName")) {
                        name = m.getName()
                    }
                } catch (Throwable ignore) { }
                if (!name) {
                    name = m.toString()
                }

                // Element Server ID (from getID())
                String serverId = null
                try {
                    if (m.respondsTo("getID")) {
                        serverId = m.getID()
                    }
                } catch (Throwable ignore2) { }
                if (serverId == null) {
                    serverId = "<no-element-server-id>"
                }

                log.log(
                    "  [root ${idx}] name='${name}', " +
                    "elementServerId(from getID())='${serverId}', " +
                    "type=${m.class.name}, isPrimary=${isPrimary}"
                )
            }
            idx++
        }

        // SLMNP-style usedRoots = all non-primary roots (by identity)
        def usedRoots = []
        roots.each { m ->
            if (m != null && !m.is(primary)) {
                usedRoots << m
            }
        }

        if (usedRoots.isEmpty()) {
            log.log("[A3.1][INFO] No non-primary roots detected by this pattern (usedRoots is empty).")
        } else {
            log.log("[A3.1][INFO] Detected ${usedRoots.size()} non-primary root(s) (candidate used project roots):")
            int j = 0
            usedRoots.each { m ->
                String humanName = null
                // Use same human-name trick as SLMNP
                try {
                    if (proj.metaClass.respondsTo(proj, "getHumanName", Object)) {
                        humanName = proj.getHumanName(m)
                    }
                } catch (Throwable ignored) { }

                if (!humanName) {
                    try {
                        if (m.respondsTo("getName")) {
                            humanName = m.getName()
                        }
                    } catch (Throwable ignore2) { }
                }
                if (!humanName) {
                    humanName = "<used model: ${m.toString()}>"
                }

                // Element Server ID for used root
                String serverId = null
                try {
                    if (m.respondsTo("getID")) {
                        serverId = m.getID()
                    }
                } catch (Throwable ignore3) { }
                if (serverId == null) {
                    serverId = "<no-element-server-id>"
                }

                log.log(
                    "    [used ${j}] humanName='${humanName}', " +
                    "elementServerId(from getID())='${serverId}', " +
                    "type=${m.class.name}"
                )
                j++
            }
        }

        // Mechanism executed successfully
        return true
    }
)

// A3.2: Neutral probe for presence of getAttachedProjectFiles()
tests << new IGTest(
    id: "A3.2",
    description: "Reflectively check for presence of Project.getAttachedProjectFiles() and log result (no semantics attached)",
    run: {
        def app  = Application.getInstance()
        def proj = app.getProject()
        def log  = app.getGUILog()

        if (proj == null) {
            log.log("[A3.2] No active project; cannot inspect getAttachedProjectFiles().")
            return false
        }

        try {
            def m = proj.class.getMethod("getAttachedProjectFiles" as String, (Class[]) [] as Class[])
            log.log("[A3.2] Project.getAttachedProjectFiles() method EXISTS on this Project class: ${m}.")
            try {
                def result = m.invoke(proj, (Object[]) [] as Object[])
                if (result == null) {
                    log.log("[A3.2] Invocation of getAttachedProjectFiles() returned null.")
                } else if (result instanceof Collection) {
                    log.log("[A3.2] Invocation of getAttachedProjectFiles() returned a Collection of size=${result.size()}.")
                } else {
                    log.log("[A3.2] Invocation of getAttachedProjectFiles() returned instance of ${result.class.name}.")
                }
            } catch (Throwable t) {
                log.log("[A3.2] Invocation of getAttachedProjectFiles() threw ${t.class.simpleName}: ${t.message}")
            }
        } catch (NoSuchMethodException nsme) {
            log.log("[A3.2] Project.getAttachedProjectFiles() method NOT found on this Project class.")
        } catch (Throwable t) {
            log.log("[A3.2] ERROR while reflecting on getAttachedProjectFiles(): ${t.class.simpleName}: ${t.message}")
            return false
        }

        // Reflection test ran successfully regardless of outcome
        return true
    }
)
