println "=== Macro Loader Diagnostics ==="

def engine = com.nomagic.magicdraw.automaton.MacroEngine.getInstance()
def list = engine.getAllMacros()

list.each { m ->
    println "Macro: ${m.getName()}  ->  Script: ${m.getScriptInstance()}"
}

println "=== End Diagnostics ==="
