package com.apertureconcepts.cameo.proxyfix.util;

import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

import java.util.Collection;

public final class ProxyPortUtil {

    private ProxyPortUtil() {
    }

    public static Port findOrCreateBoundaryProxy(Element boundaryOwner, SeamLevel seam, BoundaryContext sourceContext) {
        Port existing = findBoundaryProxy(boundaryOwner, seam, sourceContext);
        if (existing != null) {
            return existing;
        }

        String proposedName = buildProxyName(sourceContext.getPort().getName(), seam);
        return createProxyPort(boundaryOwner, proposedName);
    }

    public static Port findBoundaryProxy(Element boundaryOwner, SeamLevel seam, BoundaryContext sourceContext) {
        if (!(boundaryOwner instanceof NamedElement)) {
            return null;
        }

        Collection<Element> owned = boundaryOwner.getOwnedElement();
        for (Element ownedElement : owned) {
            if (ownedElement instanceof Port && StereotypeUtil.isProxyPort(ownedElement)) {
                Port candidate = (Port) ownedElement;
                if (candidate.getName() != null && candidate.getName().equals(buildProxyName(sourceContext.getPort().getName(), seam))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static Port createProxyPort(Element owner, String name) {
        Project project = Application.getInstance().getProject();
        Port port = project.getElementsFactory().createPortInstance();
        port.setName(name);

        StereotypesHelper.addStereotypeByString(port, "ProxyPort");

        ModelElementsManager.getInstance().addElement(port, owner);
        return port;
    }

    private static String buildProxyName(String baseName, SeamLevel seam) {
        String left = (baseName == null || baseName.isEmpty()) ? "port" : baseName;
        return left + "_" + seam.name().toLowerCase() + "Proxy";
    }
}
