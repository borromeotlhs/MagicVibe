package com.apertureconcepts.cameo.proxyfix.util;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

public final class EditabilityUtil {

    private EditabilityUtil() {
    }

    public static void assertEditable(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("Target element is null.");
        }
        if (!element.isEditable()) {
            throw new IllegalStateException("Element is read-only: " + element.getHumanName());
        }
    }
}
