package com.apertureconcepts.cameo.proxyfix.util;

import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;

public final class ValidationMessageUtil {

    private ValidationMessageUtil() {
    }

    public static String buildLiftMessage(SeamLevel targetSeam, String caseName) {
        return "Connector crosses architectural boundary and should be lifted to "
            + targetSeam.name().toLowerCase()
            + " seam ("
            + caseName
            + ").";
    }
}
