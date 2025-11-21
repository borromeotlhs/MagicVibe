import com.nomagic.magicdraw.core.Application

// =====================================================
// IGTest record
// =====================================================
class IGTest {
    String id
    String description
    Closure<Boolean> run    // should return true/false or throw
}

// =====================================================
// Utility: logging
// =====================================================
def app = Application.getInstance()
def log = app.getGUILog()

// =====================================================
// LOAD SLMNP UTILITY (WORK_ENV canonical pattern)
// =====================================================
def loadSLMNP = {
    def baseDir = new File(System.getProperty("user.dir"))
    def p1 = new File(baseDir, "plugins/macros/lib/SLMNP.groovy")
    def p2 = new File(baseDir, "lib/SLMNP.groovy")

    if (p1.exists()) return new GroovyShell().parse(p1)
    if (p2.exists()) return new GroovyShell().parse(p2)

    log.log("SLMNP.groovy not found in plugins/macros/lib or lib.")
    return null
}

// =====================================================
// FIND TESTS DIRECTORY (mirrors SLMNP loading style)
// =====================================================
def findTestsDir = {
    def baseDir = new File(System.getProperty("user.dir"))
    def t1 = new File(baseDir, "plugins/macros/tests")
    def t2 = new File(baseDir, "tests")

    if (t1.exists() && t1.isDirectory()) return t1
    if (t2.exists() && t2.isDirectory()) return t2
    return null
}

// =====================================================
// Shared test list
// =====================================================
def tests = []

// =====================================================
// Prepare GroovyShell with shared Binding
// =====================================================
def binding = new Binding()
binding.setVariable("IGTest", IGTest)
binding.setVariable("tests", tests)
binding.setVariable("Application", Application)

// Optionally expose SLMNP to tests (may be null if not found)
def slmnp = loadSLMNP()
binding.setVariable("SLMNP", slmnp)

def shell = new GroovyShell(this.class.classLoader, binding)

// =====================================================
// Load test files
// =====================================================
def testsDir = findTestsDir()
if (testsDir == null) {
    log.log("InspectorGadget AssumptionTester: tests directory not found.")
    log.log("Expected at: plugins/macros/tests or tests under " + System.getProperty("user.dir"))
    return
}

log.log("InspectorGadget AssumptionTester: loading tests from " + testsDir.absolutePath)

File[] testFiles = testsDir.listFiles({ File f ->
    f.isFile() && f.name.toLowerCase().endsWith(".groovy")
} as FileFilter)

if (testFiles == null || testFiles.length == 0) {
    log.log("InspectorGadget AssumptionTester: no *.groovy test files found in tests directory.")
    return
}

// deterministic order
testFiles.sort { a, b -> a.name <=> b.name }

testFiles.each { File f ->
    try {
        log.log("InspectorGadget AssumptionTester: evaluating test file " + f.name)
        shell.evaluate(f)
    } catch (Throwable t) {
        log.log("InspectorGadget AssumptionTester: ERROR evaluating ${f.name} (${t.class.simpleName}: ${t.message})")
    }
}

if (tests.isEmpty()) {
    log.log("InspectorGadget AssumptionTester: no IGTest instances registered by test files.")
    return
}

// =====================================================
// Run tests
// =====================================================
log.log("InspectorGadget AssumptionTester: running ${tests.size()} tests")

int pass = 0
int fail = 0
int err  = 0

tests.each { IGTest t ->
    try {
        boolean ok = t.run()
        if (ok) {
            pass++
            log.log("[${t.id}] ${t.description} => PASS")
        } else {
            fail++
            log.log("[${t.id}] ${t.description} => FAIL")
        }
    } catch (Throwable e) {
        err++
        log.log("[${t.id}] ${t.description} => ERROR (${e.class.simpleName}: ${e.message})")
    }
}

log.log("InspectorGadget AssumptionTester: finished. PASS=${pass}, FAIL=${fail}, ERROR=${err}, TOTAL=${tests.size()}")
