# AGENTS

This file contains repository-wide instructions for agents working in the MagicVibe project. The scope of these guidelines is the entire repository unless a more specific AGENTS file is added in a subdirectory.

## How to work in this repo
- Follow the whitelisted and blacklisted APIs below when writing Groovy/Gaia macros for the MagicDraw 2022xR2 environment (WORK_ENV).
- Treat every assertion in this file as authoritative knowledge about the runtime environment; designs or tests must not rely on behaviors that conflict with these assertions.
- Do not add try/catch blocks around imports.

## Whitelisted imports (known good in WORK_ENV)
- **MagicDraw core / openapi**: `com.nomagic.magicdraw.core.Application`, `com.nomagic.magicdraw.core.Project`, `com.nomagic.magicdraw.openapi.uml.ModelElementsManager`, `com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException`, `com.nomagic.magicdraw.openapi.uml.SessionManager`
- **Helpers**: `com.nomagic.uml2.ext.jmi.helpers.ModelHelper`, `com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper`, `com.nomagic.uml2.ext.jmi.helpers.TagsHelper`
- **UML kernel**: `com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element`, `Package`, `Class`, `Property`
- **Activities**: `com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity`, `com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode`, `com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.FlowFinalNode`
- **Misc / MD profile / utilities**: `com.nomagic.uml2.MagicDrawProfile`, `com.nomagic.magicdraw.copypaste.CopyPasting`, `com.nomagic.magicdraw.automaton.AutomatonMacroAPI`
- **Swing (verified by A5 tests)**: `javax.swing.JTree`, `javax.swing.tree.DefaultMutableTreeNode`, `javax.swing.event.TreeWillExpandListener`, `javax.swing.event.TreeExpansionEvent`

## Blacklisted or missing APIs (do not use in WORK_ENV)
- **Imports**: `com.nomagic.magicdraw.core.ProjectsManager` (throws `ClassNotFoundException`).
- **Methods / APIs**: `com.nomagic.magicdraw.core.Project.getResourcesManager()`, `com.nomagic.magicdraw.core.Project.getUsedProjects()`, `com.nomagic.magicdraw.core.Project.getAttachedProjectFiles()` (all confirmed missing or failing in WORK_ENV).
- Any design that depends on these APIs is not feasible in this environment.

## Assertions about WORK_ENV behavior
Treat these as reliable constraints when designing macros and tests. Labels (A0–A13) are preserved for easy reference.

- **A0 – Project.getResourcesManager is not available**: `Project` does not provide `getResourcesManager()`; avoid strategies requiring it.
- **A1 – Canonical DeriveReqt pattern via ElementsFactory**: `project.getElementsFactory()` exists and can create an Abstraction-like element via a no-arg `create*Abstraction*` method; a «DeriveReqt» stereotype is available and can be applied to the abstraction. Use this stereotype-based pattern for derive relationships.
- **A2 – Requirement detection must use stereotypes**: Determine “requirement-ness” with `StereotypesHelper.hasStereotypeOrDerived(e, "Requirement")`; avoid `instanceof` checks or plain string stereotype name matching.
- **A2 (import avoidance) – ElementsFactory via reflection**: The primary model (`project.getPrimaryModel()`) is a normal UML `Model` (subtype of `Package`/`NamedElement`). Used project roots are discovered via `project.getModels()` excluding the primary. See the `getUsedProjectRoots` helper below for the canonical pattern.
- **Canonical used-project root helper**:

```groovy
List<Element> getUsedProjectRoots(Project proj) {
    if (proj == null) return []
    def primary = proj.getPrimaryModel()
    def roots   = proj.getModels()
    if (roots == null) return []
    def used = []
    roots.each { m ->
        if (m != null && !m.is(primary)) {
            used << m
        }
    }
    return used
}
```

- **A6.2 – Anonymous inner classes are supported**: The macro environment supports anonymous inner classes and top-level listeners (e.g., `Runnable`, `TreeWillExpandListener`). Using named listeners is a preference, not a requirement.
- **A11.1 – Project.isElementInPrimaryModel is not available**: `Project` has no `isElementInPrimaryModel(Element)`; do not rely on it.
- **A11.2 – Element.getModel() is not usable/present**: JMI implementation classes lack a no-arg `getModel()` method; calls throw `MissingMethodException`. Membership must be inferred by other means (e.g., the root-discovery pattern above).
- **A13 – ResourcesManager unavailability**: Reinforces that `Project.getResourcesManager()` and related resource manager APIs are absent and must be avoided.

If you discover new constraints or verified behaviors in WORK_ENV, update this file so future contributors inherit accurate guidance.
