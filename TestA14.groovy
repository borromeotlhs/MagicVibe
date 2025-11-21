// TestA14.groovy
//
// Assumption 14: ElementsFactory can be obtained reflectively from Project
// and used (without importing com.nomagic.uml2.model.base.ElementsFactory)
// to create a Class-like element via a no-arg create*Class* method.

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project

// A14.1: ElementsFactory via reflection can create a Class-like element
tests << new IGTest(
    id: "A14.1",
    description: "ElementsFactory can be obtained via Project.getElementsFactory() reflection and used to create a Class-like element via a no-arg create*Class* method",
    run: {
        // Get current project
        Project proj = Application.getInstance().getProject()
        if (proj == null) {
            println "[A14.1] No active Project from Application.getInstance().getProject()."
            return false
        }

        // Use same primary model pattern as other tests (A3, A6)
        def primary = proj.getPrimaryModel()
        if (primary == null) {
            println "[A14.1] Project.getPrimaryModel() returned null."
            return false
        }

        // Get ElementsFactory *via reflection* (no direct import of its type)
        def efMethod
        try {
            efMethod = proj.class.getMethod(
                "getElementsFactory" as String,
                (Class[]) [] as Class[]
            )
        } catch (NoSuchMethodException ex) {
            println "[A14.1] Project.getElementsFactory() method not found via reflection: ${ex}"
            return false
        }

        def ef = efMethod.invoke(proj, (Object[]) [] as Object[])
        if (ef == null) {
            println "[A14.1] getElementsFactory() returned null."
            return false
        }

        // Look for any zero-arg method whose name suggests "create Class"
        def mClassCreator = ef.class.methods.find { m ->
            m.name.toLowerCase().contains("class") &&
            m.name.toLowerCase().startsWith("create") &&
            m.parameterTypes.length == 0
        }

        if (mClassCreator == null) {
            println "[A14.1] No zero-arg create*Class* method found on ElementsFactory. Methods: " +
                    ef.class.methods*.name.sort().unique().join(", ")
            return false
        }

        // Invoke the creator to get a new Class-like element
        def newClass
        try {
            newClass = mClassCreator.invoke(ef, (Object[]) [] as Object[])
        } catch (Throwable t) {
            println "[A14.1] Error invoking ${mClassCreator.name} on ElementsFactory: ${t}"
            return false
        }

        if (newClass == null) {
            println "[A14.1] Class-like element returned by ${mClassCreator.name} is null."
            return false
        }

        // Try to set a name reflectively (avoid compile-time type for Class)
        try {
            def setName = newClass.class.getMethod(
                "setName" as String,
                String
            )
            setName.invoke(newClass, "IG_A14_TestClass")
        } catch (Throwable t) {
            println "[A14.1] Could not call setName(String) on created class-like element: ${t}"
            // Not fatal for the assumption; we mainly care that creation works
        }

        // Try to set the owner to the primary model reflectively
        try {
            // Prefer an exact setOwner(Model or Package), but fall back to any single-arg setOwner
            def setOwnerExact = newClass.class.methods.find { m ->
                m.name == "setOwner" && m.parameterTypes.length == 1
            }

            if (setOwnerExact != null) {
                setOwnerExact.invoke(newClass, primary)
            } else {
                println "[A14.1] No setOwner(..) method found on created class-like element."
            }
        } catch (Throwable t) {
            println "[A14.1] Error calling setOwner(..) on created class-like element: ${t}"
            // Still not strictly fatal to the "can create via EF" assumption
        }

        // Verify owner == primary if getOwner() exists; otherwise fall back to created-not-null
        try {
            def getOwner = newClass.class.getMethod(
                "getOwner" as String,
                (Class[]) [] as Class[]
            )
            def owner = getOwner.invoke(newClass, (Object[]) [] as Object[])
            if (owner == primary) {
                println "[A14.1] Created class-like element owner matches primary model."
                return true
            } else {
                println "[A14.1] Created class-like element owner does NOT match primary model; owner=${owner}."
                // Still demonstrate that we successfully created the element via reflection
                return (newClass != null)
            }
        } catch (NoSuchMethodException ex) {
            println "[A14.1] getOwner() not available on created class-like element; " +
                    "treating non-null creation as success."
            return (newClass != null)
        }
    }
)
