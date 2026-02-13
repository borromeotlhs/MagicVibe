import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project

import java.text.SimpleDateFormat

// =========================================================================================
// CONFIG
// =========================================================================================
def OUTPUT_FILE_NAME = "twc_commit_history.csv"
def DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"

// =========================================================================================
// LOGGING HELPERS (GUILog + console)
// =========================================================================================
def guiLog = { String text ->
    try {
        Application.getInstance().getGUILog().log(text)
    } catch (Throwable t) {
        println text
    }
}

def INFO = { String msg -> guiLog("[TWCCommitHistory] ${msg}") }
def WARN = { String msg -> guiLog("[TWCCommitHistory][WARN] ${msg}") }
def ERR  = { String msg -> guiLog("[TWCCommitHistory][ERROR] ${msg}") }

// =========================================================================================
// CSV / formatting helpers
// =========================================================================================
def csvEscape = { Object v ->
    def s = (v == null) ? "" : v.toString()
    s = s.replace("\"", "\"\"")
    return "\"${s}\""
}

def formatDate = { Object dateLike ->
    if (dateLike == null) return ""
    if (dateLike instanceof Date) {
        return new SimpleDateFormat(DATE_PATTERN).format((Date) dateLike)
    }
    if (dateLike instanceof Number) {
        return new SimpleDateFormat(DATE_PATTERN).format(new Date(((Number) dateLike).longValue()))
    }
    return dateLike.toString()
}

def ensureLogDir = {
    def base = new File(System.getProperty("user.dir"))
    def d1 = new File(base, "plugins/macros/logs")
    def d2 = new File(base, "logs")
    if (d1.exists() || d1.mkdirs()) return d1
    if (d2.exists() || d2.mkdirs()) return d2
    return base
}

// =========================================================================================
// Reflection helpers
// =========================================================================================
def hasMethod = { Object target, String methodName, int arity ->
    if (target == null || methodName == null) return false
    def methods = target.getClass().methods.findAll { it.name == methodName }
    return methods.any { it.parameterTypes.length == arity }
}

def invokeNoArg = { Object target, String methodName ->
    if (!hasMethod(target, methodName, 0)) return null
    return target."${methodName}"()
}

def invokeOneArg = { Object target, String methodName, Object arg ->
    if (!hasMethod(target, methodName, 1)) return null
    return target."${methodName}"(arg)
}

def toList = { Object maybeCollection ->
    if (maybeCollection == null) return []
    if (maybeCollection instanceof Collection) return maybeCollection as List
    if (maybeCollection.getClass().isArray()) return Arrays.asList((Object[]) maybeCollection)
    if (maybeCollection instanceof Iterable) {
        def out = []
        maybeCollection.each { out << it }
        return out
    }
    return [maybeCollection]
}

def getFirstNonNull = { Object obj, List<String> methodNames ->
    for (String m : methodNames) {
        def v = invokeNoArg(obj, m)
        if (v != null) return v
    }
    return null
}

def readCommitFields = { Object c ->
    def id = getFirstNonNull(c, ["getId", "getRevision", "getNumber", "getHash", "getVersion"]) ?: c?.toString()

    def committer = getFirstNonNull(c, ["getAuthor", "getCommittedBy", "getCommitter", "getUser", "getOwner", "getUserName"])
    if (committer != null && !(committer instanceof String)) {
        def userName = getFirstNonNull(committer, ["getName", "getUserName", "getLogin", "getDisplayName"])
        committer = userName ?: committer.toString()
    }

    def dateRaw = getFirstNonNull(c, ["getDate", "getTime", "getTimestamp", "getCommitTime", "getCreatedOn"])
    def msg = getFirstNonNull(c, ["getComment", "getMessage", "getDescription", "getCommitComment"])

    return [
        id       : id,
        committer: committer,
        dateRaw  : dateRaw,
        dateText : formatDate(dateRaw),
        comment  : msg
    ]
}

// =========================================================================================
// Commit history collection strategies
// =========================================================================================
def collectFromObject = { Object source, String sourceName ->
    def candidates = ["getCommits", "getCommitHistory", "getHistory", "getRevisions", "listCommits", "listRevisions"]
    for (String methodName : candidates) {
        try {
            def result = invokeNoArg(source, methodName)
            def list = toList(result)
            if (!list.isEmpty()) {
                INFO("Found ${list.size()} commit/revision entries via ${sourceName}.${methodName}()")
                return list
            }
        } catch (Throwable ignored) {
            // try next candidate
        }
    }
    return []
}

def collectWithProjectArg = { Object source, String sourceName, Object projectArg ->
    def candidates = ["getCommits", "getCommitHistory", "getHistory", "getRevisions", "listCommits", "listRevisions"]
    for (String methodName : candidates) {
        try {
            def result = invokeOneArg(source, methodName, projectArg)
            def list = toList(result)
            if (!list.isEmpty()) {
                INFO("Found ${list.size()} commit/revision entries via ${sourceName}.${methodName}(project)")
                return list
            }
        } catch (Throwable ignored) {
            // try next candidate
        }
    }
    return []
}

def loadServiceSingleton = { String className ->
    try {
        def clz = Class.forName(className)
        for (String accessor : ["getInstance", "instance", "getDefault", "getService"]) {
            try {
                def m = clz.methods.find { it.name == accessor && it.parameterTypes.length == 0 }
                if (m != null) {
                    def svc = m.invoke(null)
                    if (svc != null) return svc
                }
            } catch (Throwable ignored) {
                // try next accessor
            }
        }
    } catch (Throwable ignored) {
        // class absent in this build
    }
    return null
}

// =========================================================================================
// Main
// =========================================================================================
Project project = Application.getInstance().getProject()
if (project == null) {
    ERR("No active project is open.")
    return
}

INFO("Collecting commit history for project: ${project.getName()}")

def commits = []

// Strategy 1: direct from project object
commits = collectFromObject(project, "Project")

// Strategy 2: from project descriptor and repository objects
if (commits.isEmpty()) {
    def descriptor = getFirstNonNull(project, ["getProjectDescriptor", "getDescriptor"])
    if (descriptor != null) {
        commits = collectFromObject(descriptor, "ProjectDescriptor")
        if (commits.isEmpty()) {
            commits = collectWithProjectArg(descriptor, "ProjectDescriptor", project)
        }
    }
}

if (commits.isEmpty()) {
    def repository = getFirstNonNull(project, ["getRepository", "getProjectRepository"])
    if (repository != null) {
        commits = collectFromObject(repository, "Repository")
        if (commits.isEmpty()) {
            commits = collectWithProjectArg(repository, "Repository", project)
        }
    }
}

// Strategy 3: known service class-name probes
if (commits.isEmpty()) {
    def serviceClassNames = [
        "com.nomagic.magicdraw.teamwork2.TeamworkUtils",
        "com.nomagic.magicdraw.teamwork2.TeamworkService",
        "com.nomagic.magicdraw.teamwork2.locks.ILockProjectService",
        "com.nomagic.ci.persistence.local.ProjectRepositoryManager"
    ]

    for (String scn : serviceClassNames) {
        def svc = loadServiceSingleton(scn)
        if (svc == null) continue

        commits = collectFromObject(svc, scn)
        if (commits.isEmpty()) commits = collectWithProjectArg(svc, scn, project)

        if (!commits.isEmpty()) break
    }
}

if (commits.isEmpty()) {
    WARN("Could not fetch commit history with available APIs in this environment.")
    WARN("Tip: run this script while connected to TWC and with a project opened from Teamwork Cloud.")
    return
}

// Normalize + sort

def rows = commits.collect { readCommitFields(it) }

rows.sort { a, b ->
    def ta = (a.dateRaw instanceof Date) ? ((Date) a.dateRaw).time : Long.MIN_VALUE
    def tb = (b.dateRaw instanceof Date) ? ((Date) b.dateRaw).time : Long.MIN_VALUE
    return ta <=> tb
}

// Write CSV

def outDir = ensureLogDir()
def outFile = new File(outDir, OUTPUT_FILE_NAME)
outFile.withWriter("UTF-8") { w ->
    w.writeLine("CommitId,Committer,DateTime,Comment")
    rows.each { r ->
        w.writeLine([
            csvEscape(r.id),
            csvEscape(r.committer),
            csvEscape(r.dateText),
            csvEscape(r.comment)
        ].join(","))
    }
}

INFO("Wrote ${rows.size()} commit rows to CSV: ${outFile.absolutePath}")
