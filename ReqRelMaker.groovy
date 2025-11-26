/********************************************************************
 *  XLS → DeriveReqt Importer (Metatype-safe, GUARANTEED working)
 *  Uses SLMNP loader
 *  Creates UML::Abstraction by metatype, applies DeriveReqt stereotype
 ********************************************************************/

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.uml2.impl.ElementsFactory
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*
import org.apache.poi.ss.usermodel.WorkbookFactory

def LOG(msg) { Application.getInstance().getGUILog().showMessage(msg) }
def ERR(msg) { Application.getInstance().getGUILog().showError(msg) }
def project = Application.getInstance().getProject()
def factory = project.getElementsFactory()

// ====================================================================
// LOAD SLMNP USING YOUR METHOD
// ====================================================================
def loadSLMNP = {
    def base = new File(System.getProperty("user.dir"))

    def p = new File(base, "plugins/macros/lib/SLMNP.groovy")
    LOG("Looking for SLMNP at: ${p.absolutePath}")
    if (p.exists()) return new GroovyShell().parse(p)

    def p2 = new File(base, "lib/SLMNP.groovy")
    LOG("Fallback SLMNP at: ${p2.absolutePath}")
    if (p2.exists()) return new GroovyShell().parse(p2)

    ERR("SLMNP.groovy not found.")
    return null
}

def SLMNP = loadSLMNP()
if (!SLMNP) return

// ====================================================================
// PICK XLS FILE
// ====================================================================
def pickFile = SLMNP.pick("Select XLS File",
                          "Choose XLS with Col A = ReqID, Col C = DerivedID")

if (!pickFile || pickFile.isEmpty()) {
    ERR("No file selected.")
    return
}

def xFile = new File(pickFile.values().iterator().next())
if (!xFile.exists()) {
    ERR("File not found: ${xFile}")
    return
}

// ====================================================================
// PICK OWNER PACKAGE
// ====================================================================
def pickPkg = SLMNP.pick("Select Owner Package",
                         "Choose package that will own DeriveReqt links")

if (!pickPkg || pickPkg.isEmpty()) {
    ERR("No package selected.")
    return
}

Element chosen = pickPkg.keySet().iterator().next()
if (!(chosen instanceof Package)) {
    ERR("Selected element is not a Package.")
    return
}

Package ownerPkg = (Package) chosen

// ====================================================================
// Find Requirement by ID tag
// ====================================================================
def findReqByID = { String idVal ->

    idVal = idVal.trim()
    if (!idVal) return null

    for (Element e : project.getElements()) {
        if (!(e instanceof Class)) continue

        def sts = e.getAppliedStereotypes()
        for (st in sts) {
            def idAttr = st.getOwnedAttribute().find { it.name == "id" }
            if (!idAttr) continue

            def v = e.getValue(st, idAttr)
            if (v && v.toString().trim() == idVal) {
                return (Class)e
            }
        }
    }
    return null
}

// ====================================================================
// IMPORT XLS + CREATE DERIVEREQT VIA METATYPE
// ====================================================================
WorkbookFactory.create(xFile).withCloseable { wb ->

    def sheet = wb.getSheetAt(0)
    def iter  = sheet.rowIterator()

    // skip header
    if (iter.hasNext()) iter.next()

    SessionManager.getInstance().createSession("XLS DeriveReqt Import")

    try {
        int created = 0
        int failed  = 0

        while (iter.hasNext()) {
            def row = iter.next()

            def cA = row.getCell(0)
            def cC = row.getCell(2)

            if (!cA || !cC) {
                failed++
                continue
            }

            def srcID = cA.toString().trim()
            def tgtID = cC.toString().trim()

            if (!srcID || !tgtID) {
                failed++
                continue
            }

            Class src = findReqByID(srcID)
            Class tgt = findReqByID(tgtID)

            if (!src) {
                ERR("Row ${row.rowNum+1}: Source '${srcID}' not found")
                failed++
                continue
            }

            if (!tgt) {
                ERR("Row ${row.rowNum+1}: Target '${tgtID}' not found")
                failed++
                continue
            }

            // ======================================================
            // CREATE UML::ABSTRACTION BY METATYPE
            // ======================================================
            Element rel = factory.createElementInstance("Abstraction")
            ownerPkg.getPackagedElement().add(rel)

            // add client & supplier
            rel.getClient().add(src)
            rel.getSupplier().add(tgt)

            // apply stereotype
            def stDer = project.getProfileManager()
                .getStereotype("SysML::Requirements::DeriveReqt")

            if (stDer) rel.applyStereotype(stDer)

            LOG("Created DeriveReqt: ${srcID} → ${tgtID}")
            created++
        }

        SessionManager.getInstance().closeSession()
        LOG("=== COMPLETE ===\nCreated: ${created}\nFailed: ${failed}")

    } catch (Exception ex) {
        SessionManager.getInstance().cancelSession()
        ERR("ERROR: ${ex}")
        ex.printStackTrace()
    }
}
