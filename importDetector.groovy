// Minimal direct imports: only stuff we are *sure* exists in a running MD instance.
import com.nomagic.magicdraw.core.Application

// ----------------------------------------------------------------------------
// CONFIGURE: list of classes you want to probe
// ----------------------------------------------------------------------------
def classGroups = [
    core: [
        'com.nomagic.magicdraw.core.Application',
        'com.nomagic.magicdraw.core.Project',
        'com.nomagic.magicdraw.core.ProjectsManager',
    ],
    openapi: [
        'com.nomagic.magicdraw.openapi.uml.ModelElementsManager',
        'com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException',
        'com.nomagic.magicdraw.openapi.uml.SessionManager',
    ],
    helpers: [
        'com.nomagic.uml2.ext.jmi.helpers.ModelHelper',
        'com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper',
        'com.nomagic.uml2.ext.jmi.helpers.TagsHelper',
    ],
    umlKernel: [
        'com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element',
        'com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package',
        'com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class',
        'com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property',
    ],
    activities: [
        'com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity',
        'com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode',
        'com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.FlowFinalNode',
    ],
    misc: [
        'com.nomagic.uml2.MagicDrawProfile',
        'com.nomagic.magicdraw.copypaste.CopyPasting',
        'com.nomagic.magicdraw.automaton.AutomatonMacroAPI',
    ]
]

// ----------------------------------------------------------------------------
// IMPLEMENTATION
// ----------------------------------------------------------------------------
def app  = Application.getInstance()
def log  = app.getGUILog()
def cl   = this.class.classLoader

int okCount   = 0
int failCount = 0

log.log("=== MagicDraw Groovy import profiling started ===")

classGroups.each { groupName, fqcnList ->
    log.log("=== Group: ${groupName} ===")
    fqcnList.each { fqcn ->
        try {
            // Try to resolve the class without triggering static initializers
            Class.forName(fqcn, false, cl)
            log.log("import ${fqcn} works")
            okCount++
        }
        catch (Throwable t) {
            // Could be ClassNotFoundException, NoClassDefFoundError, etc.
            log.log("import ${fqcn} fails (${t.class.simpleName}: ${t.message})")
            failCount++
        }
    }
}

int total = okCount + failCount
log.log("=== MagicDraw Groovy import profiling finished ===")
log.log("Summary: ${okCount} works, ${failCount} fails, total ${total}")
