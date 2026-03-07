package com.apertureconcepts.cameo.proxyfix.validation;

import com.apertureconcepts.cameo.proxyfix.fix.LiftToSubsystemSeamAction;
import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.apertureconcepts.cameo.proxyfix.util.ValidationMessageUtil;
import com.nomagic.actions.NMAction;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;

public class ComponentComponentCrossSubsystemRule extends AbstractProxySeamValidationRule {

    @Override
    protected boolean matchesCase(BoundaryContext left, BoundaryContext right) {
        return crossesSubsystem(left, right)
            && !crossesSegment(left, right)
            && isComponent(left.getRawOwner())
            && isComponent(right.getRawOwner());
    }

    @Override
    protected SeamLevel getTargetSeam() {
        return SeamLevel.SUBSYSTEM;
    }

    @Override
    protected String getRuleId() {
        return "ComponentComponentCrossSubsystemRule";
    }

    @Override
    protected String getMessage() {
        return ValidationMessageUtil.buildLiftMessage(getTargetSeam(), getRuleId());
    }

    @Override
    protected NMAction buildFixAction(Connector connector, BoundaryContext left, BoundaryContext right) {
        return new LiftToSubsystemSeamAction("fix." + getRuleId(), "Lift connector to subsystem seam", connector, left, right);
    }
}
