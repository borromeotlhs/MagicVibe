import com.nomagic.magicdraw.core.Application

def app = Application.getInstance()
def project = app.getProject()
def log = app.getGUILog()

if (project == null) {
    log.log("[DEBUG] No open project.")
    return
}

log.log("=== DEBUG: project.getModels() ===")
def models = project.getModels()
log.log("models class = ${models?.class?.name}, size = ${models?.size()}")
models?.eachWithIndex { m, i ->
    log.log("  [${i}] ${m?.class?.name}  name='${m?.name}'  toString='${m}'")
}

log.log("=== DEBUG: project.getUsedProjects() ===")
def used = project.getUsedProjects()
log.log("used class = ${used?.class?.name}, size = ${used?.size()}")
used?.eachWithIndex { u, i ->
    log.log("  [${i}] ${u?.class?.name}  toString='${u}'")
    // Try a few common getters
    ["getModel","getPrimaryModel","getProject","getElement","getModelRoot"].each { g ->
        try {
            if (u.metaClass.respondsTo(u, g as String)) {
                def v = u."$g"()
                log.log("    ${g}() -> ${v?.class?.name} name='${v?.name}' toString='${v}'")
            }
        } catch (Throwable t) {
            log.log("    ${g}() -> ERROR: ${t.class.simpleName}: ${t.message}")
        }
    }
}

log.log("=== DEBUG END ===")
