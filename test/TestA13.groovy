// TestA13.groovy
//
// Assumption 13: com.nomagic.magicdraw.core.Project.getResourcesManager()
//                is NOT available in this WORK_ENV (method missing).

import com.nomagic.magicdraw.core.Application

tests << new IGTest(
    id: "A13.1",
    description: "Project.getResourcesManager() method is NOT available in this WORK_ENV",
    run: {
        def proj = Application.getInstance().getProject()
        if (proj == null) {
            // If there's no project, treat as FAIL (can't confirm assumption)
            return false
        }

        def projClass = proj.class

        // Look through all methods on the runtime Project implementation
        def hasResourcesManager = projClass.methods.any { m ->
            m.name == "getResourcesManager" && m.parameterTypes.length == 0
        }

        // Assumption 13 is that this method does NOT exist
        return !hasResourcesManager
    }
)
