package com.apertureconcepts.cameo.proxyfix.model;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

import java.util.ArrayList;
import java.util.List;

public class ProxyFixPlan {
    private Connector originalConnector;
    private SeamLevel targetSeam;
    private Port end1OriginalPort;
    private Port end2OriginalPort;
    private Element end1BoundaryOwner;
    private Element end2BoundaryOwner;
    private Port end1TargetProxy;
    private Port end2TargetProxy;
    private final List<Connector> newLowerConnectors = new ArrayList<>();

    public Connector getOriginalConnector() {
        return originalConnector;
    }

    public void setOriginalConnector(Connector originalConnector) {
        this.originalConnector = originalConnector;
    }

    public SeamLevel getTargetSeam() {
        return targetSeam;
    }

    public void setTargetSeam(SeamLevel targetSeam) {
        this.targetSeam = targetSeam;
    }

    public Port getEnd1OriginalPort() {
        return end1OriginalPort;
    }

    public void setEnd1OriginalPort(Port end1OriginalPort) {
        this.end1OriginalPort = end1OriginalPort;
    }

    public Port getEnd2OriginalPort() {
        return end2OriginalPort;
    }

    public void setEnd2OriginalPort(Port end2OriginalPort) {
        this.end2OriginalPort = end2OriginalPort;
    }

    public Element getEnd1BoundaryOwner() {
        return end1BoundaryOwner;
    }

    public void setEnd1BoundaryOwner(Element end1BoundaryOwner) {
        this.end1BoundaryOwner = end1BoundaryOwner;
    }

    public Element getEnd2BoundaryOwner() {
        return end2BoundaryOwner;
    }

    public void setEnd2BoundaryOwner(Element end2BoundaryOwner) {
        this.end2BoundaryOwner = end2BoundaryOwner;
    }

    public Port getEnd1TargetProxy() {
        return end1TargetProxy;
    }

    public void setEnd1TargetProxy(Port end1TargetProxy) {
        this.end1TargetProxy = end1TargetProxy;
    }

    public Port getEnd2TargetProxy() {
        return end2TargetProxy;
    }

    public void setEnd2TargetProxy(Port end2TargetProxy) {
        this.end2TargetProxy = end2TargetProxy;
    }

    public List<Connector> getNewLowerConnectors() {
        return newLowerConnectors;
    }
}
