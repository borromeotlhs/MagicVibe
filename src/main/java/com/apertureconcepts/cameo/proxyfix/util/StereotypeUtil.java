package com.apertureconcepts.cameo.proxyfix.util;

import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

public final class StereotypeUtil {

    private StereotypeUtil() {
    }

    public static boolean hasStereotype(Element element, String stereotypeName) {
        if (element == null || stereotypeName == null || stereotypeName.isEmpty()) {
            return false;
        }
        return StereotypesHelper.hasStereotypeOrDerived(element, stereotypeName);
    }

    public static boolean isProxyPort(Element element) {
        return element instanceof Port && hasStereotype(element, "ProxyPort");
    }
}
