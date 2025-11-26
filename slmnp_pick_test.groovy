import com.nomagic.magicdraw.core.Application

// ------------------------------------------------------------
// Simple logger
// ------------------------------------------------------------
def LOG(Object msg) {
    try {
        Application.getInstance().getGUILog().log(msg?.toString())
    } catch (Throwable t) {
        println msg
    }
}

// ------------------------------------------------------------
// Your requested dynamic SLMNP loader
// ------------------------------------------------------------
def loadSLMNP() {
    def mdHome = new File(System.getProperty("user.dir"))
    def slmnpFile = new File(mdHome, "plugins/macros/lib/SLMNP.groovy")

    LOG("Looking for SLMNP at:")
    LOG("    " + slmnpFile.absolutePath)

    if (!slmnpFile.exists()) {
        LOG("SLMNP NOT FOUND.")
        return null
    }

    LOG("SLMNP FOUND — loading...")

    try {
        return new GroovyShell().parse(slmnpFile)
    } catch (Throwable t) {
        LOG("SLMNP load failure: ${t}")
        return null
    }
}

// ------------------------------------------------------------
// MAIN
// ------------------------------------------------------------

def slmnp = loadSLMNP()
if (slmnp == null) {
    LOG("Aborting test: SLMNP could not be loaded.")
    return
}

LOG("Launching SLMNP picker to verify comment / attached-file display...")

def result = slmnp.pick(
    "SLMNP Attached File Test",
    "Select a model element. Comments and attached-file entries " +
    "should appear as children under their owning element."
)

if (result == null) {
    LOG("Picker cancelled or no element selected.")
} else {
    LOG("Selected element:")
    LOG("    QName : ${result.qname}")
    LOG("    Type  : ${result.type}")
    LOG("    Class : ${result.element?.class?.name}")
}

return
