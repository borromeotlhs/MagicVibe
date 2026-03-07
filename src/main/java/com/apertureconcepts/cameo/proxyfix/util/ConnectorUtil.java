package com.apertureconcepts.cameo.proxyfix.util;

import com.apertureconcepts.cameo.proxyfix.model.BoundaryContext;
import com.apertureconcepts.cameo.proxyfix.model.ProxyFixPlan;
import com.apertureconcepts.cameo.proxyfix.model.SeamLevel;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.ConnectorEnd;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port;

import java.util.ArrayList;
import java.util.List;

public final class ConnectorUtil {

    private ConnectorUtil() {
    }

    public static boolean hasTwoPortEnds(Connector connector) {
        return getEndPort(connector, 0) != null && getEndPort(connector, 1) != null;
    }

    public static Port getEndPort(Connector connector, int idx) {
        if (connector == null || connector.getEnd().size() <= idx) {
            return null;
        }

        ConnectorEnd end = connector.getEnd().get(idx);
        if (!(end.getRole() instanceof Port)) {
            return null;
        }
        return (Port) end.getRole();
    }

    public static void retargetConnector(Connector connector, Port left, Port right) {
        connector.getEnd().get(0).setRole(left);
        connector.getEnd().get(1).setRole(right);
    }

    public static void createLowerRouting(ProxyFixPlan plan, BoundaryContext left, BoundaryContext right) {
        List<Port> leftPath = buildPathToSeam(left, plan.getTargetSeam(), plan.getEnd1TargetProxy());
        List<Port> rightPath = buildPathToSeam(right, plan.getTargetSeam(), plan.getEnd2TargetProxy());

        plan.getNewLowerConnectors().addAll(connectPortsInOrder(leftPath));
        plan.getNewLowerConnectors().addAll(connectPortsInOrder(rightPath));
    }

    private static List<Port> buildPathToSeam(BoundaryContext context, SeamLevel seamLevel, Port seamProxy) {
        List<Port> path = new ArrayList<>();
        path.add(context.getPort());

        Element component = context.getNearestComponent();
        Element subsystem = context.getNearestSubsystem();

        if (seamLevel == SeamLevel.SUBSYSTEM) {
            if (component != null && component != subsystem) {
                path.add(ProxyPortUtil.findOrCreateBoundaryProxy(component, SeamLevel.COMPONENT, context));
            }
            path.add(seamProxy);
            return path;
        }

        if (component != null) {
            path.add(ProxyPortUtil.findOrCreateBoundaryProxy(component, SeamLevel.COMPONENT, context));
        }
        if (subsystem != null) {
            path.add(ProxyPortUtil.findOrCreateBoundaryProxy(subsystem, SeamLevel.SUBSYSTEM, context));
        }
        path.add(seamProxy);

        return path;
    }

    private static List<Connector> connectPortsInOrder(List<Port> path) {
        List<Connector> created = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            Port from = path.get(i);
            Port to = path.get(i + 1);
            if (from == null || to == null || from == to) {
                continue;
            }
            created.add(createSimpleConnector(from, to));
        }
        return created;
    }

    private static Connector createSimpleConnector(Port left, Port right) {
        Project project = Application.getInstance().getProject();
        Connector connector = project.getElementsFactory().createConnectorInstance();
        ConnectorEnd end1 = project.getElementsFactory().createConnectorEndInstance();
        ConnectorEnd end2 = project.getElementsFactory().createConnectorEndInstance();

        end1.setRole(left);
        end2.setRole(right);
        connector.getEnd().add(end1);
        connector.getEnd().add(end2);

        Element commonOwner = left.getOwner();
        if (commonOwner == null) {
            commonOwner = right.getOwner();
        }

        if (commonOwner != null) {
            ModelElementsManager.getInstance().addElement(connector, commonOwner);
        }
        return connector;
    }
}
