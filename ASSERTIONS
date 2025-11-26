WHITELIST (known-good imports in WORK_ENV)

MagicDraw core / openapi

com.nomagic.magicdraw.core.Application

com.nomagic.magicdraw.core.Project

com.nomagic.magicdraw.openapi.uml.ModelElementsManager

com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException

com.nomagic.magicdraw.openapi.uml.SessionManager

Helpers

com.nomagic.uml2.ext.jmi.helpers.ModelHelper

com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper

com.nomagic.uml2.ext.jmi.helpers.TagsHelper

UML kernel

com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element

com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package

com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class

com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property

Activities

com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity

com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode

com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.FlowFinalNode

Misc / MD profile / utilities

com.nomagic.uml2.MagicDrawProfile

com.nomagic.magicdraw.copypaste.CopyPasting

com.nomagic.magicdraw.automaton.AutomatonMacroAPI

Swing (verified by A5 tests)

javax.swing.JTree

javax.swing.tree.DefaultMutableTreeNode

javax.swing.event.TreeWillExpandListener

javax.swing.event.TreeExpansionEvent

BLACKLIST (known-bad or missing in WORK_ENV)

Imports

com.nomagic.magicdraw.core.ProjectsManager

Fails with ClassNotFoundException when imported in the macro environment.

Methods / APIs (not imports, but “do not use”)

com.nomagic.magicdraw.core.Project.getResourcesManager()

Confirmed missing (previous macro attempt threw MissingMethodException).

com.nomagic.magicdraw.core.Project.getUsedProjects()

Confirmed missing (MissingMethodException) in tests.

com.nomagic.magicdraw.core.Project.getAttachedProjectFiles()

Confirmed missing (NoSuchMethodException by reflection).

These method-level items aren’t imports per se, but they’re effectively “blacklisted APIs” for WORK_ENV: any design that depends on them is not feasible.

ASSERTIONS (WORK_ENV behavior so far)

I’ll label them A0–A5 so they’re easy to reference.

A0 – Project.getResourcesManager is not available

In WORK_ENV, com.nomagic.magicdraw.core.Project does not have getResourcesManager(). Any macro or plugin design that relies on project.getResourcesManager() is not feasible in this environment.

A1 – Canonical DeriveReqt pattern via ElementsFactory

In WORK_ENV:

Project.getElementsFactory() exists and returns a non-null ElementsFactory.

The ElementsFactory can create an Abstraction-like element via a no-arg create*Abstraction* method.

A «DeriveReqt» stereotype is available and can be applied to that Abstraction-like element.

Canonical DeriveReqt relationship pattern: an Abstraction element with the «DeriveReqt» stereotype applied.

A2 – Requirement detection must use stereotypes, not instanceof / strings

In WORK_ENV, “requirement-ness” is determined by stereotypes, not by metaclass or name matching.

A reliable utility is:

boolean isRequirement(Element e) {
    return com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
        .hasStereotypeOrDerived(e, "Requirement")
}


Do not rely on:

e instanceof com.nomagic.uml2.ext.magicdraw.requirements.mdrequirements.Requirement

Plain string name checks on stereotypes.
A2 – ElementsFactory via reflection (import avoidance)

A2.1 – Reflection access to ElementsFactory (no direct import)

In WORK_ENV, ElementsFactory must not be accessed via
import com.nomagic.magicdraw.core.ElementsFactory
because the macro environment fails to resolve that class.

Instead, ElementsFactory is reliably reachable via reflection on the project instance:

Application.getInstance().getProject()

then project.getClass().getMethod("getElementsFactory").invoke(project)

So: use reflection to get the factory; avoid importing ElementsFactory in macros.

(Groovy reflection reference (general): https://groovy-lang.org/reflection.html
)



A3 – Used projects and attached-project APIs

In WORK_ENV (MagicDraw 2022xR2):

Project.getAttachedProjectFiles() does not exist (reflection yields NoSuchMethodException).

Project.getUsedProjects() does not exist (MissingMethodException).

Used projects are visible via:

project.getPrimaryModel() → primary root model.

project.getModels() → all root models.

Used project roots are those ModelImpl instances m where m != null && !m.is(primary).

Each root has a stable Element Server ID from m.getID() that matches the “Element Server ID” shown in the MD spec dialog.

Therefore:

Do not design macros around getAttachedProjectFiles() or getUsedProjects().

Do use getPrimaryModel() + getModels() and identity comparison (!m.is(primary)) to enumerate used project roots.

A4 – Attached files are Comment elements marked by MagicDrawProfile

In WORK_ENV:

“Attached files” are represented as Comment elements
(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.impl.CommentImpl)
for which MagicDrawProfile.isAttachedFile(Element e) returns true.

These comments live under the primary model’s containment tree (primary + recursive getOwnedElement() traversal).

For each attached file comment:

e.getID() → Element Server ID.

e.getBody() → user-visible file reference (e.g., "context.docx", "HISideIntegration.xlsx").

e.getOwner() → the UML element (e.g., Architecture package, root Model) that the file is attached to, with its own getName() / getID().

Discovery pattern:

Start at project.getPrimaryModel() (as Element).

DFS via getOwnedElement() collecting all Elements.

For each, call MagicDrawProfile.isAttachedFile(e).

Those that return true are attached-file comments.

This is independent of any filesystem heuristics (project.getPrimaryProject().getLocation().getPath(), etc.). Directory logic may help locate the actual file on disk, but it is not what defines attached files in the model.

A5 – Closures, anonymous inner classes, and Swing tree listeners work in macros

From the A5 tests:

Closures as interfaces

Closures can be coerced to Java interfaces such as Runnable and executed in the macro environment:

Runnable r = { -> ... } as Runnable
r.run()


Top-level classes

Top-level Groovy classes inside macro files that implement Java interfaces (e.g., Runnable) can be instantiated and used.

Anonymous inner classes

Java-style anonymous inner classes are supported:

Runnable r = new Runnable() {
    void run() { ... }
}
r.run()


This directly contradicts the earlier assumption that anonymous inner classes are “not allowed” in the macro sandbox.

Swing tree listeners

The following Swing imports are available and usable in macros:

javax.swing.JTree

javax.swing.tree.DefaultMutableTreeNode

javax.swing.event.TreeWillExpandListener

javax.swing.event.TreeExpansionEvent

Both patterns work:

Top-level listener class

class A5TopLevelTreeWillExpandListener implements TreeWillExpandListener {
    void treeWillExpand(TreeExpansionEvent e) {}
    void treeWillCollapse(TreeExpansionEvent e) {}
}
tree.addTreeWillExpandListener(new A5TopLevelTreeWillExpandListener())


Anonymous inner listener

tree.addTreeWillExpandListener(new TreeWillExpandListener() {
    void treeWillExpand(TreeExpansionEvent e) { ... }
    void treeWillCollapse(TreeExpansionEvent e) { ... }
})


Net conclusion for A5:
In WORK_ENV, the macro Groovy environment supports:

Closures → interfaces,

Top-level listener classes,

Anonymous inner classes (including Swing listeners).
The previous Unexpected input: '(' around tree.addTreeWillExpandListener(new TreeWillExpandListener() was not due to a fundamental macro limitation, but some other local issue (syntax/placement).

A6.1 – Primary model & used project roots pattern

Assertion:

In WORK_ENV (your MagicDraw 2022xR2 macro environment):

The primary model returned by project.getPrimaryModel() is a normal UML Model (a subtype of Package and thus NamedElement, per UML and MagicDraw’s implementation). This means it behaves like any other root model from a type perspective; it is not excluded by instanceof NamedElement.

Generic background: UML Model specializes Package, which specializes Namespace and NamedElement.

The correct way to identify used project roots is:

Get the primary root:
primary = project.getPrimaryModel()

Get all root models:
roots = project.getModels()

Treat as “used project roots” every m in roots where:
m != null && !m.is(primary)

The helper we just tested in A6.1 is now the canonical pattern:

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


Your A3.1 and A6.1 logs show identical sets of 8 non-primary roots with the same element server IDs, confirming that this pattern is stable and reproducible in WORK_ENV.

So the incorrect part of the original assumption was the explanation “primary model is not a NamedElement”; the correct assertion is that the primary model must be explicitly added as the primary node, and used projects are the additional ModelImpl roots discovered via getModels() minus the primary.

A6.2 – Anonymous inner classes vs. top-level listeners

Assertion:

In WORK_ENV:

The MagicDraw Groovy macro environment does support anonymous inner classes, including:

Runnable r = new Runnable() {
    @Override
    void run() { ... }
}


and

tree.addTreeWillExpandListener(new TreeWillExpandListener() {
    @Override
    void treeWillExpand(TreeExpansionEvent e) { ... }
    @Override
    void treeWillCollapse(TreeExpansionEvent e) { ... }
})


This matches standard Groovy behavior for Java-style anonymous inner classes.

The tests A5.3, A5.4, and A5.5 all passed, showing:

Anonymous Runnable works.

Top-level TreeWillExpandListener works.

Anonymous TreeWillExpandListener works.

Therefore, the original part of Assumption 6 that claimed:

“Your MagicDraw macro engine does NOT allow anonymous inner classes…”

is false in WORK_ENV.

Using top-level listener classes for SLMNP (e.g., SLMNPExpandListener) is a design preference (readability, reuse, testability), not a hard requirement imposed by the macro engine.

A11.1 — Project.isElementInPrimaryModel is not available

Assertion A11.1
In WORK_ENV (MagicDraw 2022xR2 macro environment), com.nomagic.magicdraw.core.Project does not provide a public method isElementInPrimaryModel(Element).

Verified via reflection in TestA11.groovy, which inspects all Project methods and finds no such signature.

Any prior approach relying on project.isElementInPrimaryModel(e) must be considered invalid in WORK_ENV.

A11.2 — Element.getModel() is not usable / not present

Assertion A11.2
In WORK_ENV, the JMI implementation classes for model elements (e.g. ModelImpl, PackageImpl, ProfileImpl, StringTaggedValueImpl, ProfileApplicationImpl) do not implement a no-arg getModel() method.

Calls to e.getModel() on these types consistently throw MissingMethodException.

Therefore, the pattern proj.getPrimaryModel() == elem.getModel() is not available as a membership test and must not be treated as the “correct” or “stable” API in WORK_ENV.

Membership of an element in the primary vs used models will instead need to be inferred through other means (e.g., the SLMNP-style root discovery via project.getPrimaryModel() and project.getModels(), and potentially owner/root traversal), not via getModel().


A13 – ResourcesManager unavailability

A13.1 – No Project.getResourcesManager()

In WORK_ENV, com.nomagic.magicdraw.core.Project does not expose a getResourcesManager() method (confirmed reflectively).

Any strategies that depend on:

project.getResourcesManager(), or

any API exposed through that resource manager
are invalid in this environment and must be avoided.