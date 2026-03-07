package com.apertureconcepts.cameo.proxyfix.util;

import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

public final class OwnerTraversalUtil {

    private OwnerTraversalUtil() {
    }

    public static BoundaryContext resolveBoundaryContext(Connector connector, int endIndex) {
        Port port = ConnectorUtil.getEndPort(connector, endIndex);
        if (port == null) {
            return null;
        }

        Element rawOwner = port.getOwner();
        Element nearestComponent = findNearestAncestorWithStereotype(rawOwner, "Component");
        Element nearestSubsystem = findNearestAncestorWithStereotype(rawOwner, "Subsystem");
        Element nearestSegment = findNearestAncestorWithStereotype(rawOwner, "Segment");

        return new BoundaryContext(rawOwner, nearestComponent, nearestSubsystem, nearestSegment, port);
    }

    public static Element findNearestAncestorWithStereotype(Element start, String stereotypeName) {
        Element cursor = start;
        while (cursor != null) {
            if (StereotypeUtil.hasStereotype(cursor, stereotypeName)) {
                return cursor;
            }
            cursor = cursor.getOwner();
        }
        return null;
    }
}
