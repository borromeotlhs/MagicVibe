package com.apertureconcepts.cameo.proxyfix.validation;

import com.apertureconcepts.cameo.proxyfix.fix.LiftToSegmentSeamAction;
import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.apertureconcepts.cameo.proxyfix.util.ValidationMessageUtil;
import com.nomagic.actions.NMAction;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;

public class HwComponentCrossSegmentRule extends AbstractProxySeamValidationRule {

    @Override
    protected boolean matchesCase(BoundaryContext left, BoundaryContext right) {
        boolean hwComp = (isHardware(left.getRawOwner()) && isComponent(right.getRawOwner()))
            || (isComponent(left.getRawOwner()) && isHardware(right.getRawOwner()));
        return crossesSegment(left, right) && hwComp;
    }

    @Override
    protected SeamLevel getTargetSeam() {
        return SeamLevel.SEGMENT;
    }

    @Override
    protected String getRuleId() {
        return "HwComponentCrossSegmentRule";
    }

    @Override
    protected String getMessage() {
        return ValidationMessageUtil.buildLiftMessage(getTargetSeam(), getRuleId());
    }

    @Override
    protected NMAction buildFixAction(Connector connector, BoundaryContext left, BoundaryContext right) {
        return new LiftToSegmentSeamAction("fix." + getRuleId(), "Lift connector to segment seam", connector, left, right);
    }
}
