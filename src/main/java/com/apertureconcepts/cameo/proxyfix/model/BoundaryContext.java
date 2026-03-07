package com.apertureconcepts.cameo.proxyfix.model;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

public class BoundaryContext {
    private final Element rawOwner;
    private final Element nearestComponent;
    private final Element nearestSubsystem;
    private final Element nearestSegment;
    private final Port port;

    public BoundaryContext(Element rawOwner, Element nearestComponent, Element nearestSubsystem, Element nearestSegment, Port port) {
        this.rawOwner = rawOwner;
        this.nearestComponent = nearestComponent;
        this.nearestSubsystem = nearestSubsystem;
        this.nearestSegment = nearestSegment;
        this.port = port;
    }

    public Element getRawOwner() {
        return rawOwner;
    }

    public Element getNearestComponent() {
        return nearestComponent;
    }

    public Element getNearestSubsystem() {
        return nearestSubsystem;
    }

    public Element getNearestSegment() {
        return nearestSegment;
    }

    public Port getPort() {
        return port;
    }
}
