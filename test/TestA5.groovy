import com.nomagic.magicdraw.core.Application

// Swing imports for tree/listener tests
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.event.TreeWillExpandListener
import javax.swing.event.TreeExpansionEvent

// =========================
// A5.1 – Closure -> Runnable
// =========================
tests << new IGTest(
    id: "A5.1",
    description: "Closures can be coerced to Runnable and executed in macro environment",
    run: {
        def log = Application.getInstance().getGUILog()
        log.log("[A5.1] Starting closure-as-Runnable test...")

        // Groovy closure coerced to Runnable
        Runnable r = { ->
            log.log("[A5.1] Closure-based Runnable executed.")
        } as Runnable

        try {
            r.run()
        } catch (Throwable t) {
            log.log("[A5.1] ERROR executing closure-based Runnable: ${t.class.simpleName}: ${t.message}")
            return false
        }

        log.log("[A5.1] Closure-as-Runnable test completed successfully.")
        return true
    }
)

// =========================
// A5.2 – Top-level Runnable
// =========================
class A5TopLevelRunnable implements Runnable {
    void run() {
        // no-op
    }
}

tests << new IGTest(
    id: "A5.2",
    description: "Top-level Runnable class can be instantiated and executed in macro environment",
    run: {
        def log = Application.getInstance().getGUILog()
        log.log("[A5.2] Starting top-level Runnable class test...")

        Runnable r
        try {
            r = new A5TopLevelRunnable()
            r.run()
        } catch (Throwable t) {
            log.log("[A5.2] ERROR instantiating or executing A5TopLevelRunnable: ${t.class.simpleName}: ${t.message}")
            return false
        }

        log.log("[A5.2] Top-level Runnable executed successfully.")
        return true
    }
)

// ===================================
// A5.3 – Anonymous inner class Runnable
// ===================================
tests << new IGTest(
    id: "A5.3",
    description: "Anonymous inner class implementing Runnable can be instantiated and executed in macro environment",
    run: {
        def log = Application.getInstance().getGUILog()
        log.log("[A5.3] Starting anonymous-inner-class Runnable test...")

        Runnable r
        try {
            r = new Runnable() {
                @Override
                void run() {
                    log.log("[A5.3] Anonymous Runnable executed.")
                }
            }
        } catch (Throwable t) {
            log.log("[A5.3] ERROR constructing anonymous Runnable: ${t.class.simpleName}: ${t.message}")
            return false
        }

        try {
            r.run()
        } catch (Throwable t) {
            log.log("[A5.3] ERROR executing anonymous Runnable: ${t.class.simpleName}: ${t.message}")
            return false
        }

        log.log("[A5.3] Anonymous-inner-class Runnable test completed successfully.")
        return true
    }
)

// =============================================
// A5 – Tree listener–specific tests / imports
// =============================================

// Top-level TreeWillExpandListener implementation
class A5TopLevelTreeWillExpandListener implements TreeWillExpandListener {
    @Override
    void treeWillExpand(TreeExpansionEvent event) {
        // no-op for test
    }

    @Override
    void treeWillCollapse(TreeExpansionEvent event) {
        // no-op for test
    }
}

// A5.4 – Can we import JTree/TreeWillExpandListener and attach a top-level listener?
tests << new IGTest(
    id: "A5.4",
    description: "JTree + top-level TreeWillExpandListener can be constructed and listener attached",
    run: {
        def log = Application.getInstance().getGUILog()
        log.log("[A5.4] Starting JTree + top-level TreeWillExpandListener test...")

        JTree tree
        try {
            // Simple tree model: root with one child
            def root  = new DefaultMutableTreeNode("root")
            def child = new DefaultMutableTreeNode("child")
            root.add(child)

            tree = new JTree(root)
        } catch (Throwable t) {
            log.log("[A5.4] ERROR constructing JTree: ${t.class.simpleName}: ${t.message}")
            return false
        }

        try {
            def listener = new A5TopLevelTreeWillExpandListener()
            tree.addTreeWillExpandListener(listener)
        } catch (Throwable t) {
            log.log("[A5.4] ERROR attaching top-level TreeWillExpandListener: ${t.class.simpleName}: ${t.message}")
            return false
        }

        log.log("[A5.4] Successfully constructed JTree and attached top-level TreeWillExpandListener.")
        return true
    }
)

// A5.5 – Can we use an anonymous inner TreeWillExpandListener with JTree?
tests << new IGTest(
    id: "A5.5",
    description: "JTree + anonymous inner TreeWillExpandListener can be constructed and listener attached",
    run: {
        def log = Application.getInstance().getGUILog()
        log.log("[A5.5] Starting JTree + anonymous TreeWillExpandListener test...")

        JTree tree
        try {
            def root  = new DefaultMutableTreeNode("root")
            def child = new DefaultMutableTreeNode("child")
            root.add(child)

            tree = new JTree(root)
        } catch (Throwable t) {
            log.log("[A5.5] ERROR constructing JTree: ${t.class.simpleName}: ${t.message}")
            return false
        }

        try {
            tree.addTreeWillExpandListener(new TreeWillExpandListener() {
                @Override
                void treeWillExpand(TreeExpansionEvent event) {
                    // For this test we just log once if it ever fires;
                    // we don't actually trigger expansion here.
                    log.log("[A5.5] treeWillExpand called on anonymous listener (if expansion occurs).")
                }

                @Override
                void treeWillCollapse(TreeExpansionEvent event) {
                    log.log("[A5.5] treeWillCollapse called on anonymous listener (if collapse occurs).")
                }
            })
        } catch (Throwable t) {
            log.log("[A5.5] ERROR attaching anonymous TreeWillExpandListener: ${t.class.simpleName}: ${t.message}")
            return false
        }

        log.log("[A5.5] Successfully constructed JTree and attached anonymous TreeWillExpandListener.")
        return true
    }
)
