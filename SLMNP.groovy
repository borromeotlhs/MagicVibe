import javax.swing.*
import javax.swing.tree.*
import javax.swing.event.*
import java.awt.*

import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.MagicDrawProfile

// ===================================================================
//  LAZY TREE NODE
// ===================================================================
class LazyNode extends DefaultMutableTreeNode {
    boolean loaded = false
    LazyNode(Object uo) {
        super(uo)
        allowsChildren = true
    }
}

// ===================================================================
//  RENDERER
// ===================================================================
class SLMNPRenderer extends DefaultTreeCellRenderer {
    @Override
    Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected,
            boolean expanded, boolean leaf, int row, boolean hasFocus) {

        Component comp = super.getTreeCellRendererComponent(
            tree, value, selected, expanded, leaf, row, hasFocus)

        def data = (value?.userObject instanceof Map)
                ? value.userObject.label
                : value.userObject

        this.setText(data.toString())
        return comp
    }
}

// ===================================================================
//  EXPANSION LISTENER
// ===================================================================
class SLMNPExpandListener implements TreeWillExpandListener {
    def getChildrenClosure
    def createNodeClosure
    def treeModel

    SLMNPExpandListener(getChildrenClosure, createNodeClosure, treeModel) {
        this.getChildrenClosure = getChildrenClosure
        this.createNodeClosure = createNodeClosure
        this.treeModel = treeModel
    }

    void treeWillExpand(TreeExpansionEvent evt) {
        def node = evt.path.lastPathComponent
        if (node.loaded) return
        node.loaded = true

        if (!(node.userObject instanceof Map)) return

        def elem = node.userObject.element
        def kids = getChildrenClosure(elem)

        node.removeAllChildren()
        kids.each { ch ->
            def newNode = createNodeClosure(ch)
            if (newNode != null) node.add(newNode)
        }

        treeModel.reload(node)
    }

    void treeWillCollapse(TreeExpansionEvent evt) { }
}

// ===================================================================
//  GLOBAL STATE: TITLE / LABEL OVERRIDES
// ===================================================================
def _slmnpTitleOverride = null
def _slmnpLabelOverride = null

// ===================================================================
//  CHILD RETRIEVAL
// ===================================================================
def getChildren(elem) {
    def list = []

    try {
        if (elem != null && elem.respondsTo("getOwnedElement")) {
            list.addAll(elem.ownedElement)
        }
    } catch (Throwable ignored) {}

    // Only show NamedElements and Comments as tree nodes
    list.findAll { (it instanceof NamedElement) || (it instanceof Comment) }.unique()
}

// ===================================================================
//  NODE FACTORY
// ===================================================================
def createNode(elem) {

    def tp = elem.class.simpleName.replace("Impl", "")
    def nm

    // Element ID (useful if you want to correlate with logs)
    def id = ""
    try {
        if (elem instanceof Element) {
            id = elem.getID() ?: ""
        }
    } catch (Throwable ignored) { }

    // ----- Comment / Attached-file handling -----
    if (elem instanceof Comment) {
        boolean attached = false
        try {
            attached = MagicDrawProfile.isAttachedFile(elem)
        } catch (Throwable ignored) { }

        def body = (elem.body ?: "").trim()
        if (!body) {
            body = attached ? "<attached file>" : "<comment>"
        }
        if (body.length() > 80) {
            body = body.substring(0, 77) + "..."
        }

        nm = body
        tp = attached ? "AttachedFile" : "Comment"
    }
    // ----- Named elements -----
    else if (elem instanceof NamedElement) {
        nm = elem.name?.trim() ? elem.name : "<unnamed>"
    }
    // ----- Fallback -----
    else {
        nm = elem.toString()
    }

    def label = id ? "${nm} [${tp}] {${id}}" : "${nm} [${tp}]"

    def node = new LazyNode([element: elem, label: label])
    node.add(new DefaultMutableTreeNode("▹ Loading..."))
    return node
}

// ===================================================================
//  COLLECT ATTACHED-FILE COMMENTS (ONE PASS)
// ===================================================================
def collectAttachedFiles(Element root) {
    def results = []   // Groovy ArrayList

    def visit
    visit = { Element e ->
        if (e instanceof Comment) {
            boolean attached = false
            try {
                attached = MagicDrawProfile.isAttachedFile(e)
            } catch (Throwable ignored) { }
            if (attached) {
                results << (Comment)e
            }
        }
        try {
            e.ownedElement?.each { child ->
                if (child instanceof Element) {
                    visit(child as Element)
                }
            }
        } catch (Throwable ignored) { }
    }

    if (root != null) {
        visit(root)
    }

    results
}

// ===================================================================
//  MAIN PICKER
// ===================================================================
def pick() {

    def app = Application.getInstance()
    def project = app.getProject()

    if (project == null) {
        app.getGUILog().log("[SLMNP] No open project.")
        return null
    }

    def primary = project.getPrimaryModel()
    def roots   = project.getModels()

    def top = new LazyNode("Project Contents")
    top.loaded = true

    // Primary model
    if (primary != null) {
        top.add(createNode(primary))
    }

    // USED MODELS
    // Use identity (!m.is(primary)) to distinguish distinct ModelImpls.
    def usedRoots = []
    roots?.each { m ->
        if (m != null && !m.is(primary)) {
            usedRoots << m
        }
    }

    usedRoots.each { m ->
        // Try to get the same human-readable name MagicDraw shows in containment
        def humanName = null
        try {
            if (project.metaClass.respondsTo(project, "getHumanName", Object)) {
                humanName = project.getHumanName(m)
            }
        } catch (Throwable ignored) {}

        if (!humanName) {
            humanName = m.name ?: "<used model>"
        }

        def n = createNode(m)
        if (n != null && n.userObject instanceof Map) {
            n.userObject.label = "<<USED>> ${humanName}"
        }
        top.add(n)
    }

    // ATTACHED FILES NODE (from primary model)
    if (primary instanceof Element) {
        def attached = collectAttachedFiles(primary as Element)
        if (!attached.isEmpty()) {
            def afRoot = new LazyNode("Attached Files")
            attached.each { c ->
                afRoot.add(createNode(c))
            }
            top.add(afRoot)
        }
    }

    def tree = new JTree(top)
    tree.showsRootHandles = true
    tree.setRootVisible(true)
    tree.setCellRenderer(new SLMNPRenderer())

    def expandListener = new SLMNPExpandListener(
        this.&getChildren,
        this.&createNode,
        (DefaultTreeModel) tree.model
    )
    tree.addTreeWillExpandListener(expandListener)

    // Title / label
    def dlgTitle = (_slmnpTitleOverride != null)
        ? _slmnpTitleOverride
        : "Select Model Element"

    def dlg = new JDialog(app.getMainFrame(), dlgTitle, true)
    dlg.setSize(600, 650)
    dlg.setLayout(new BorderLayout())

    def labelText = (_slmnpLabelOverride != null)
        ? _slmnpLabelOverride
        : "Select any element to verify picker functionality:"

    def header = new JLabel("<html>${labelText}</html>")
    header.setBorder(BorderFactory.createEmptyBorder(10,10,10,10))
    dlg.add(header, BorderLayout.NORTH)

    dlg.add(new JScrollPane(tree), BorderLayout.CENTER)

    def selected = null

    def okBtn = new JButton("OK")
    okBtn.addActionListener({
        def n = tree.lastSelectedPathComponent
        if (n instanceof LazyNode && n.userObject instanceof Map) {
            def elem = n.userObject.element
            def t = elem.class.simpleName.replace("Impl","")
            selected = [
                element: elem,
                type   : t,
                qname  : (elem instanceof NamedElement)
                    ? elem.qualifiedName
                    : ""
            ]
            dlg.dispose()
        }
    })

    def cancelBtn = new JButton("Cancel")
    cancelBtn.addActionListener({ dlg.dispose() })

    def pnl = new JPanel()
    pnl.add(okBtn)
    pnl.add(cancelBtn)

    dlg.add(pnl, BorderLayout.SOUTH)
    dlg.setLocationRelativeTo(app.getMainFrame())
    dlg.setVisible(true)

    return selected
}

// ===================================================================
//  OVERLOAD: pick(titleText, labelText)
// ===================================================================
def pick(String titleText, String labelText) {
    _slmnpTitleOverride = titleText
    _slmnpLabelOverride = labelText

    def result = pick()

    _slmnpTitleOverride = null
    _slmnpLabelOverride = null

    return result
}

return this
