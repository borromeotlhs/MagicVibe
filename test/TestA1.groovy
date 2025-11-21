import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper

// A1.1: Project.getElementsFactory() exists and returns non-null
tests << new IGTest(
    id: "A1.1",
    description: "Project.getElementsFactory() exists and returns non-null",
    run: {
        def proj = Application.getInstance().getProject()
        if (proj == null) return false

        def m
        try {
            m = proj.class.getMethod("getElementsFactory" as String, (Class[]) [] as Class[])
        } catch (NoSuchMethodException ignore) {
            return false
        }

        def ef = m.invoke(proj, (Object[]) [] as Object[])
        return ef != null
    }
)

// A1.2: ElementsFactory can create an Abstraction-like element
tests << new IGTest(
    id: "A1.2",
    description: "ElementsFactory can create an Abstraction-like element via a no-arg create*Abstraction* method",
    run: {
        def proj = Application.getInstance().getProject()
        if (proj == null) return false

        def efMethod = proj.class.getMethod("getElementsFactory" as String, (Class[]) [] as Class[])
        def ef = efMethod.invoke(proj, (Object[]) [] as Object[])
        if (ef == null) return false

        // Look for any zero-arg method whose name contains 'abstraction'
        def mAbs = ef.class.methods.find { m ->
            m.name.toLowerCase().contains("abstraction") && m.parameterTypes.length == 0
        }
        if (mAbs == null) return false

        def abs = mAbs.invoke(ef, (Object[]) [] as Object[])
        return abs != null
    }
)

// A1.3: Can find a DeriveReqt stereotype and apply it to an Abstraction-like element
tests << new IGTest(
    id: "A1.3",
    description: "Can find a DeriveReqt stereotype and apply it to an Abstraction-like element",
    run: {
        def proj = Application.getInstance().getProject()
        if (proj == null) return false

        // Try to locate DeriveReqt stereotype using common names
        def ster = null

        // Deprecated name-based APIs are fine inside a test harness
        // https://jdocs.nomagic.com/2021x_Refresh2/deprecated-list.html
        if (!ster) ster = StereotypesHelper.getStereotype(proj, "deriveReqt")
        if (!ster) ster = StereotypesHelper.getStereotype(proj, "DeriveReqt")

        // Try with profile name "SysML" if simple name lookup fails
        if (!ster) {
            try {
                ster = StereotypesHelper.getStereotype(proj, "deriveReqt", "SysML")
            } catch (Throwable ignore) { }
        }
        if (!ster) {
            try {
                ster = StereotypesHelper.getStereotype(proj, "DeriveReqt", "SysML")
            } catch (Throwable ignore) { }
        }

        // If we still don't have it, treat as FAIL, not ERROR
        if (ster == null) return false

        // Reuse ElementsFactory + Abstraction creation logic
        def efMethod = proj.class.getMethod("getElementsFactory" as String, (Class[]) [] as Class[])
        def ef = efMethod.invoke(proj, (Object[]) [] as Object[])
        if (ef == null) return false

        def mAbs = ef.class.methods.find { m ->
            m.name.toLowerCase().contains("abstraction") && m.parameterTypes.length == 0
        }
        if (mAbs == null) return false

        def abs = mAbs.invoke(ef, (Object[]) [] as Object[])
        if (abs == null) return false

        // Apply DeriveReqt
        StereotypesHelper.addStereotype(abs, ster)

        // Confirm it took
        return StereotypesHelper.hasStereotypeOrDerived(abs, ster)
    }
)
