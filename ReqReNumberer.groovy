import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.sysml.util.SysMLProfile
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement

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
def abstractReqStereo = StereotypesHelper.getStereotype(project, "AbstractRequirement")
def requirementStereos = [abstractReqStereo, reqStereo].findAll { it }

def objPropStereo = StereotypesHelper.getStereotype(project, "ObjectProperties")
if (!objPropStereo) {
    ERR("ObjectProperties stereotype not found. Aborting.")
    return
}

def findTag = { stereo, String... candidates ->
    if (!stereo) return null
    def names = candidates.findAll { it }.collect { it.toLowerCase() }
    // Include derived properties because SysML Requirement «id» may not be a direct owned attribute
    def props = (StereotypesHelper.getPropertiesWithDerived(stereo) ?: []) + (stereo.getOwnedAttribute() ?: [])
    props.find { attr ->
        def nm = attr?.name?.toLowerCase()
        nm && nm in names
    }
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

def elemLabel = { Element e ->
    if (e instanceof NamedElement) {
        def nm = e.name
        return nm && nm.trim() ? nm : "<unnamed>"
    }
    return e?.toString() ?: "<null>"
}

def resolveRequirementStereoAndIdTag = { Element e ->
    def stereosToCheck = []

    // First look for explicitly applied requirement stereotypes
    requirementStereos.each { reqSt ->
        if (reqSt && StereotypesHelper.hasStereotypeOrDerived(e, reqSt)) {
            stereosToCheck << reqSt
        }
    }

    // Also consider any other applied stereotypes for an «id» tag
    stereosToCheck.addAll(StereotypesHelper.getStereotypesWithDerived(e) ?: [])

    for (def st : stereosToCheck.unique()) {
        def tag = findTag(st, "id", "Id", "ID")
        if (tag) return [st, tag?.name]
    }
    return null
}

def isRequirement = { Element e ->
    try {
        return requirementStereos.any { reqSt -> reqSt && StereotypesHelper.hasStereotypeOrDerived(e, reqSt) }
    } catch (Exception ex) {
        LOG("Skipping '${elemLabel(e)}' (requirement stereotype check error: ${ex.message})")
        return false
    }
}

def walk
walk = { Element e ->
    if (e == null) return

    try {
        if (isRequirement(e)) {
            def reqPair = resolveRequirementStereoAndIdTag(e)
            def jamaStr = null

            if (StereotypesHelper.hasStereotypeOrDerived(e, objPropStereo)) {
                try {
                    def jamaVal = StereotypesHelper.getStereotypePropertyValue(e, objPropStereo, jamaIdTag?.name)
                    jamaStr = jamaVal ? jamaVal.toString().trim() : null
                } catch (Exception ex) {
                    LOG("Skipped '${elemLabel(e)}' (error reading jamaId: ${ex.message})")
                }
            }

            if (!reqPair) {
                LOG("Skipped '${elemLabel(e)}' (no requirement ID tag found)")
            } else if (jamaStr) {
                def (reqStereoUsed, reqIdTagName) = reqPair
                try {
                    StereotypesHelper.setStereotypePropertyValue(e, reqStereoUsed, reqIdTagName, jamaStr)
                    LOG("Set Requirement ID for '${elemLabel(e)}' to '${jamaStr}'")
                    updated++
                } catch (Exception ex) {
                    LOG("Skipped '${elemLabel(e)}' (error setting ID: ${ex.message})")
                }
            } else {
                LOG("Skipped '${elemLabel(e)}' (no jamaId value or ObjectProperties stereotype not applied)")
            }
        }
    } catch (Exception ex) {
        LOG("Skipping '${elemLabel(e)}' (processing error: ${ex.message})")
    }

    try {
        e.ownedElement?.each { child ->
            if (child instanceof Element) {
                walk(child as Element)
            }
        }
    } catch (Exception ex) {
        LOG("Skipping children of '${elemLabel(e)}' (ownedElement error: ${ex.message})")
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
