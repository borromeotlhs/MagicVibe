package com.apertureconcepts.cameo.proxyfix.fix;

import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.ProxyFixPlan;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.apertureconcepts.cameo.proxyfix.util.ConnectorUtil;
import com.apertureconcepts.cameo.proxyfix.util.EditabilityUtil;
import com.apertureconcepts.cameo.proxyfix.util.ProxyPortUtil;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.annotation.AnnotationAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

import java.awt.event.ActionEvent;

public abstract class AbstractLiftConnectorAction extends NMAction implements AnnotationAction {

    protected final Connector connector;
    protected final BoundaryContext left;
    protected final BoundaryContext right;

    protected AbstractLiftConnectorAction(String id, String name, Connector connector, BoundaryContext left, BoundaryContext right) {
        super(id, name, null, null);
        this.connector = connector;
        this.left = left;
        this.right = right;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = Application.getInstance().getProject();

        SessionManager.getInstance().createSession(project, getName());
        try {
            EditabilityUtil.assertEditable(connector);

            ProxyFixPlan plan = buildPlan();
            Port seamLeft = ProxyPortUtil.findOrCreateBoundaryProxy(plan.getEnd1BoundaryOwner(), getTargetSeam(), left);
            Port seamRight = ProxyPortUtil.findOrCreateBoundaryProxy(plan.getEnd2BoundaryOwner(), getTargetSeam(), right);

            plan.setEnd1TargetProxy(seamLeft);
            plan.setEnd2TargetProxy(seamRight);

            ConnectorUtil.retargetConnector(connector, seamLeft, seamRight);
            ConnectorUtil.createLowerRouting(plan, left, right);
        } finally {
            SessionManager.getInstance().closeSession(project);
        }
    }

    protected Element resolveBoundaryOwner(BoundaryContext context, SeamLevel seamLevel) {
        if (seamLevel == SeamLevel.SUBSYSTEM) {
            return context.getNearestSubsystem();
        }
        if (seamLevel == SeamLevel.SEGMENT) {
            return context.getNearestSegment();
        }
        return context.getNearestComponent();
    }

    protected abstract SeamLevel getTargetSeam();

    protected ProxyFixPlan buildPlan() {
        ProxyFixPlan plan = new ProxyFixPlan();
        plan.setOriginalConnector(connector);
        plan.setTargetSeam(getTargetSeam());
        plan.setEnd1OriginalPort(left.getPort());
        plan.setEnd2OriginalPort(right.getPort());
        plan.setEnd1BoundaryOwner(resolveBoundaryOwner(left, getTargetSeam()));
        plan.setEnd2BoundaryOwner(resolveBoundaryOwner(right, getTargetSeam()));
        return plan;
    }
}
