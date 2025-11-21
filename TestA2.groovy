// TestA2.groovy
// Assumption 2 – ElementsFactory access pattern (reflection vs direct import)

import com.nomagic.magicdraw.core.Application

// A2.1: Confirm ElementsFactory is reachable via Project.getElementsFactory() using reflection,
// and log whether the ElementsFactory class is resolvable via Class.forName().
tests << new IGTest(
    id: "A2.1",
    description: "ElementsFactory is reachable via Project.getElementsFactory() reflection; direct import is avoided in WORK_ENV",
    run: {
        def proj = Application.getInstance().getProject()
        if (proj == null) {
            println "[A2.1] No open project; cannot test ElementsFactory access."
            return false
        }

        // Reflectively locate getElementsFactory()
        def m
        try {
            m = proj.class.getMethod("getElementsFactory" as String, (Class[]) [] as Class[])
        } catch (NoSuchMethodException e) {
            println "[A2.1] Project.getElementsFactory() method NOT found via reflection: ${e}"
            return false
        }

        // Invoke getElementsFactory()
        def ef = m.invoke(proj, (Object[]) [] as Object[])
        println "[A2.1] proj.getElementsFactory() via reflection returned: ${ef}"

        // Informational: can the runtime see the ElementsFactory class by name?
        boolean classResolvable
        try {
            Class.forName("com.nomagic.magicdraw.core.ElementsFactory")
            classResolvable = true
        } catch (Throwable t) {
            classResolvable = false
        }
        println "[A2.1] Class.forName('com.nomagic.magicdraw.core.ElementsFactory') success? ${classResolvable}"

        // The actual assertion for this assumption:
        // ElementsFactory is reachable via the reflection path.
        return ef != null
    }
)
