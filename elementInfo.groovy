import com.nomagic.magicdraw.core.Application

def tree = Application.getInstance().getMainFrame().getBrowser().getContainmentTree()
def node = tree.getSelectedNode()

def element = null
try {
    element = node.getUserObject()
} catch(ignore) {}

def cls = element?.getClass()?.getName()
def type = null

try {
    type = element?.getHumanType()
} catch(ignore){}

Application.getInstance().getGUILog().log("Node class: ${node?.getClass()?.getName()}")
Application.getInstance().getGUILog().log("Backing UML element class: ${cls}")
Application.getInstance().getGUILog().log("Human type: ${type}")
