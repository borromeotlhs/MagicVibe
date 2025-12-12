import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.sysml.util.SysMLProfile
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element

/********************************************************************
 *  Requirement Re-numberer
 *  Sets the Requirement «id» tag to the jamaId from «ObjectProperties»
 *  Recursively processes a user-selected scope via SLMNP picker
 ********************************************************************/

def LOG(msg) { Application.getInstance().getGUILog().showMessage(msg) }
def ERR(msg) { Application.getInstance().getGUILog().showError(msg) }

def project = Application.getInstance().getProject()
if (!project) {
    ERR("No open project. Aborting.")
    return
}

// ====================================================================
// LOAD SLMNP (WORK_ENV pattern)
// ====================================================================
def loadSLMNP = {
    def baseDir = new File(System.getProperty("user.dir"))
    def p1 = new File(baseDir, "plugins/macros/lib/SLMNP.groovy")
    def p2 = new File(baseDir, "lib/SLMNP.groovy")

    if (p1.exists()) {
        LOG("Loading SLMNP from: ${p1.absolutePath}")
        return new GroovyShell().parse(p1)
    }
    if (p2.exists()) {
        LOG("Loading SLMNP from: ${p2.absolutePath}")
        return new GroovyShell().parse(p2)
    }

    ERR("SLMNP.groovy not found in plugins/macros/lib or lib.")
    return null
}

def SLMNP = loadSLMNP()
if (!SLMNP) return

// ====================================================================
// PICK SCOPE
// ====================================================================
LOG("Pick the root element to scan for requirements…")
def scopeSel = SLMNP.pick(
    "Select Requirement Scope",
    "Pick the element whose containment will be scanned for requirements"
)

if (!scopeSel) {
    LOG("Canceled.")
    return
}

Element scope = scopeSel.element
LOG("Scope selected: ${scope?.name ?: scope}")

// ====================================================================
// RESOLVE STEREOTYPES + TAGS
// ====================================================================
def sysml = SysMLProfile.getInstance(project)
def reqStereo = sysml.getRequirement()

def objPropStereo = StereotypesHelper.getStereotype(project, "ObjectProperties")
if (!objPropStereo) {
    ERR("ObjectProperties stereotype not found. Aborting.")
    return
}

def findTag = { stereo, String... candidates ->
    if (!stereo) return null
    def lower = candidates.collect { it?.toLowerCase() }
    stereo.getOwnedAttribute().find { attr ->
        attr?.name && attr.name.toLowerCase() in lower
    }
}

def reqIdTag = findTag(reqStereo, "id", "Id", "ID")
if (!reqIdTag) {
    ERR("Requirement ID tag not found on Requirement stereotype.")
    return
}

def jamaIdTag = findTag(objPropStereo, "jamaId", "jamaID")
if (!jamaIdTag) {
    ERR("jamaId tag not found on ObjectProperties stereotype.")
    return
}

// ====================================================================
// WALK + RENUMBER
// ====================================================================
def updated = 0

def walk
walk = { Element e ->
    if (e == null) return

    if (reqStereo && StereotypesHelper.hasStereotypeOrDerived(e, reqStereo)) {
        def jamaVal = e.getValue(objPropStereo, jamaIdTag)
        def jamaStr = jamaVal?.toString()?.trim()

        if (jamaStr) {
            StereotypesHelper.setStereotypePropertyValue(e, reqStereo, reqIdTag, jamaStr)
            LOG("Set Requirement ID for '${e.name ?: "<unnamed>"}' to '${jamaStr}'")
            updated++
        } else {
            LOG("Skipped '${e.name ?: "<unnamed>"}' (no jamaId value)")
        }
    }

    e.ownedElement?.each { child ->
        if (child instanceof Element) {
            walk(child as Element)
        }
    }
}

SessionManager.getInstance().createSession("Requirement Re-number")
try {
    walk(scope)
    SessionManager.getInstance().closeSession()
    LOG("Renumber complete. Updated ${updated} requirement(s).")
} catch (Exception ex) {
    SessionManager.getInstance().cancelSession()
    ERR("ERROR: ${ex}")
    ex.printStackTrace()
}
