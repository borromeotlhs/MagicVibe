package com.apertureconcepts.cameo.proxyfix.validation;

import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.apertureconcepts.cameo.proxyfix.util.ConnectorUtil;
import com.apertureconcepts.cameo.proxyfix.util.OwnerTraversalUtil;
import com.apertureconcepts.cameo.proxyfix.util.StereotypeUtil;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.annotation.Annotation;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.validation.ElementValidationRuleImpl;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Constraint;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractProxySeamValidationRule implements ElementValidationRuleImpl {

    @Override
    public Set<Annotation> run(Project project, Constraint constraint, Collection<? extends Element> elements) {
        Set<Annotation> result = new HashSet<>();

        for (Element e : elements) {
            if (!(e instanceof Connector)) {
                continue;
            }

            Connector connector = (Connector) e;
            if (!ConnectorUtil.hasTwoPortEnds(connector)) {
                continue;
            }

            BoundaryContext left = OwnerTraversalUtil.resolveBoundaryContext(connector, 0);
            BoundaryContext right = OwnerTraversalUtil.resolveBoundaryContext(connector, 1);

            if (left == null || right == null) {
                continue;
            }
            if (!matchesCase(left, right)) {
                continue;
            }
            if (!requiresLift(connector, left, right, getTargetSeam())) {
                continue;
            }

            NMAction fix = buildFixAction(connector, left, right);
            Annotation annotation = new Annotation(connector, getMessage(), Collections.singletonList(fix));
            result.add(annotation);
        }

        return result;
    }

    protected boolean isHardware(Element e) {
        return StereotypeUtil.hasStereotype(e, "Hardware");
    }

    protected boolean isComponent(Element e) {
        return StereotypeUtil.hasStereotype(e, "Component");
    }

    protected boolean crossesSubsystem(BoundaryContext left, BoundaryContext right) {
        return left.getNearestSubsystem() != null
            && right.getNearestSubsystem() != null
            && left.getNearestSubsystem() != right.getNearestSubsystem();
    }

    protected boolean crossesSegment(BoundaryContext left, BoundaryContext right) {
        return left.getNearestSegment() != null
            && right.getNearestSegment() != null
            && left.getNearestSegment() != right.getNearestSegment();
    }

    protected boolean requiresLift(Connector connector, BoundaryContext left, BoundaryContext right, SeamLevel target) {
        if (target == SeamLevel.SUBSYSTEM) {
            return !StereotypeUtil.hasStereotype(left.getRawOwner(), "Subsystem")
                || !StereotypeUtil.hasStereotype(right.getRawOwner(), "Subsystem");
        }
        if (target == SeamLevel.SEGMENT) {
            return !StereotypeUtil.hasStereotype(left.getRawOwner(), "Segment")
                || !StereotypeUtil.hasStereotype(right.getRawOwner(), "Segment");
        }
        return false;
    }

    protected abstract boolean matchesCase(BoundaryContext left, BoundaryContext right);

    protected abstract SeamLevel getTargetSeam();

    protected abstract String getRuleId();

    protected abstract String getMessage();

    protected abstract NMAction buildFixAction(Connector connector, BoundaryContext left, BoundaryContext right);
}
