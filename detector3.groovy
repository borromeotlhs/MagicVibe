import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.uml.BaseElement

def app = Application.getInstance()
def project = app.getProject()
def log = app.getGUILog()

if (project == null) {
    log.log("[DEBUG-HUMAN-NAME] No open project.")
    return
}

def models = project.getModels()
log.log("[DEBUG-HUMAN-NAME] project.getModels(): size=${models?.size()}")

models?.eachWithIndex { m, i ->
    String human = "<n/a>"

    try {
        if (m instanceof BaseElement) {
            human = (m as BaseElement).getHumanName()
        } else {
            human = m.toString()
        }
    } catch (Throwable t) {
        human = "ERROR: ${t.class.simpleName}: ${t.message}"
    }

    log.log("  [${i}] class=${m?.class?.name}")
    log.log("      name='${m?.name}'")
    log.log("      human='${human}'")
}

log.log("[DEBUG-HUMAN-NAME] Done.")
