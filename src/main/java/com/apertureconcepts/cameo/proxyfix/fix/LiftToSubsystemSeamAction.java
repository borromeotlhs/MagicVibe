package com.apertureconcepts.cameo.proxyfix.fix;

import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;

public class LiftToSubsystemSeamAction extends AbstractLiftConnectorAction {

    public LiftToSubsystemSeamAction(String id, String name, Connector connector, BoundaryContext left, BoundaryContext right) {
        super(id, name, connector, left, right);
    }

    @Override
    protected SeamLevel getTargetSeam() {
        return SeamLevel.SUBSYSTEM;
    }
}
