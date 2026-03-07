#!/usr/bin/env python
"""
Practical SysML knowledge graph schema and Python classes.

Purpose:
- Hold a normalized intermediate representation between diagram parsing and
  SysML v2 textual emission.
- Keep the model simple enough for LLM/agent workflows.
- Support later adapters to JSON, NetworkX, graph DBs, or SysML emitters.

This is an engineering-oriented schema, not the official OMG interchange schema.
"""

from __future__ import annotations

from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Any, Dict, List, Optional, Iterable
import json


class NodeKind(str, Enum):
    PACKAGE = "Package"
    PART_DEFINITION = "PartDefinition"
    PART_USAGE = "PartUsage"
    ATTRIBUTE_DEFINITION = "AttributeDefinition"
    ATTRIBUTE_USAGE = "AttributeUsage"
    PORT_DEFINITION = "PortDefinition"
    PORT_USAGE = "PortUsage"
    ITEM_DEFINITION = "ItemDefinition"
    ITEM_USAGE = "ItemUsage"
    CONNECTION_DEFINITION = "ConnectionDefinition"
    CONNECTION_USAGE = "ConnectionUsage"
    ACTION_DEFINITION = "ActionDefinition"
    ACTION_USAGE = "ActionUsage"
    STATE_DEFINITION = "StateDefinition"
    STATE_USAGE = "StateUsage"
    REQUIREMENT_DEFINITION = "RequirementDefinition"
    REQUIREMENT_USAGE = "RequirementUsage"
    VIEW_DEFINITION = "ViewDefinition"
    VIEW_USAGE = "ViewUsage"
    VIEWPOINT_DEFINITION = "ViewpointDefinition"
    INTERFACE_DEFINITION = "InterfaceDefinition"
    INTERFACE_USAGE = "InterfaceUsage"
    UNKNOWN = "Unknown"


class EdgeKind(str, Enum):
    OWNS = "owns"
    TYPED_BY = "typedBy"
    SPECIALIZES = "specializes"
    SUBSETS = "subsets"
    REDEFINES = "redefines"
    REFERENCES = "references"
    CONNECTS = "connects"
    BINDS = "binds"
    FLOWS = "flows"
    SATISFIES = "satisfies"
    VERIFIES = "verifies"
    REFINES = "refines"
    EXPOSES = "exposes"
    IMPORTS = "imports"
    HAS_PORT = "hasPort"
    HAS_ATTRIBUTE = "hasAttribute"
    HAS_ACTION = "hasAction"
    HAS_STATE = "hasState"
    UNKNOWN = "unknown"


@dataclass
class SourceProvenance:
    source_type: str
    source_name: str
    confidence: float = 1.0
    notes: str = ""


@dataclass
class GraphNode:
    id: str
    kind: NodeKind
    name: str = ""
    qualified_name: str = ""
    properties: Dict[str, Any] = field(default_factory=dict)
    provenance: List[SourceProvenance] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["kind"] = self.kind.value
        return d


@dataclass
class GraphEdge:
    id: str
    kind: EdgeKind
    source: str
    target: str
    properties: Dict[str, Any] = field(default_factory=dict)
    provenance: List[SourceProvenance] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["kind"] = self.kind.value
        return d


@dataclass
class SysMLGraph:
    id: str
    name: str = ""
    language: str = "SysMLv2"
    metadata: Dict[str, Any] = field(default_factory=dict)
    nodes: Dict[str, GraphNode] = field(default_factory=dict)
    edges: Dict[str, GraphEdge] = field(default_factory=dict)

    def add_node(self, node: GraphNode) -> None:
        if node.id in self.nodes:
            raise ValueError(f"Duplicate node id: {node.id}")
        self.nodes[node.id] = node

    def add_edge(self, edge: GraphEdge) -> None:
        if edge.id in self.edges:
            raise ValueError(f"Duplicate edge id: {edge.id}")
        if edge.source not in self.nodes:
            raise KeyError(f"Edge source node not found: {edge.source}")
        if edge.target not in self.nodes:
            raise KeyError(f"Edge target node not found: {edge.target}")
        self.edges[edge.id] = edge

    def get_node(self, node_id: str) -> GraphNode:
        return self.nodes[node_id]

    def get_edge(self, edge_id: str) -> GraphEdge:
        return self.edges[edge_id]

    def find_nodes(self, kind: Optional[NodeKind] = None, name: Optional[str] = None) -> List[GraphNode]:
        result = list(self.nodes.values())
        if kind is not None:
            result = [n for n in result if n.kind == kind]
        if name is not None:
            result = [n for n in result if n.name == name]
        return result

    def outgoing(self, node_id: str, kind: Optional[EdgeKind] = None) -> List[GraphEdge]:
        result = [e for e in self.edges.values() if e.source == node_id]
        if kind is not None:
            result = [e for e in result if e.kind == kind]
        return result

    def incoming(self, node_id: str, kind: Optional[EdgeKind] = None) -> List[GraphEdge]:
        result = [e for e in self.edges.values() if e.target == node_id]
        if kind is not None:
            result = [e for e in result if e.kind == kind]
        return result

    def owner_of(self, node_id: str) -> Optional[GraphNode]:
        owners = self.incoming(node_id, EdgeKind.OWNS)
        if not owners:
            return None
        return self.nodes[owners[0].source]

    def owned_by(self, node_id: str) -> List[GraphNode]:
        return [self.nodes[e.target] for e in self.outgoing(node_id, EdgeKind.OWNS)]

    def typed_by(self, node_id: str) -> Optional[GraphNode]:
        edges = self.outgoing(node_id, EdgeKind.TYPED_BY)
        if not edges:
            return None
        return self.nodes[edges[0].target]

    def validate(self) -> List[str]:
        errors: List[str] = []

        # 1) All usages that should be typed should have a TYPED_BY edge.
        usage_kinds = {
            NodeKind.PART_USAGE,
            NodeKind.ATTRIBUTE_USAGE,
            NodeKind.PORT_USAGE,
            NodeKind.ITEM_USAGE,
            NodeKind.ACTION_USAGE,
            NodeKind.STATE_USAGE,
            NodeKind.REQUIREMENT_USAGE,
            NodeKind.VIEW_USAGE,
            NodeKind.INTERFACE_USAGE,
            NodeKind.CONNECTION_USAGE,
        }
        for node in self.nodes.values():
            if node.kind in usage_kinds:
                typed_edges = self.outgoing(node.id, EdgeKind.TYPED_BY)
                if not typed_edges and not node.properties.get("untyped_ok", False):
                    errors.append(f"Usage node '{node.id}' ({node.kind.value}) is missing typedBy.")

        # 2) Connection usages should have at least two CONNECTS edges.
        for node in self.nodes.values():
            if node.kind == NodeKind.CONNECTION_USAGE:
                conn_edges = self.outgoing(node.id, EdgeKind.CONNECTS)
                if len(conn_edges) < 2:
                    errors.append(f"ConnectionUsage '{node.id}' has fewer than two connects edges.")

        # 3) Port usages should normally be owned by part or port usages/definitions.
        for node in self.nodes.values():
            if node.kind == NodeKind.PORT_USAGE:
                owner = self.owner_of(node.id)
                if owner is None:
                    errors.append(f"PortUsage '{node.id}' has no owner.")
                elif owner.kind not in {
                    NodeKind.PART_DEFINITION,
                    NodeKind.PART_USAGE,
                    NodeKind.PORT_DEFINITION,
                    NodeKind.PORT_USAGE,
                    NodeKind.INTERFACE_DEFINITION,
                    NodeKind.INTERFACE_USAGE,
                }:
                    errors.append(
                        f"PortUsage '{node.id}' owner '{owner.id}' has incompatible kind '{owner.kind.value}'."
                    )

        # 4) No self-owning edges.
        for edge in self.edges.values():
            if edge.kind == EdgeKind.OWNS and edge.source == edge.target:
                errors.append(f"Node '{edge.source}' cannot own itself.")

        return errors

    def to_dict(self) -> Dict[str, Any]:
        return {
            "model": {
                "id": self.id,
                "name": self.name,
                "language": self.language,
                "metadata": self.metadata,
                "nodes": [n.to_dict() for n in self.nodes.values()],
                "edges": [e.to_dict() for e in self.edges.values()],
            }
        }

    def to_json(self, indent: int = 2) -> str:
        return json.dumps(self.to_dict(), indent=indent)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "SysMLGraph":
        model = data["model"]
        graph = cls(
            id=model["id"],
            name=model.get("name", ""),
            language=model.get("language", "SysMLv2"),
            metadata=model.get("metadata", {}),
        )

        for node_data in model.get("nodes", []):
            node = GraphNode(
                id=node_data["id"],
                kind=NodeKind(node_data["kind"]),
                name=node_data.get("name", ""),
                qualified_name=node_data.get("qualified_name", ""),
                properties=node_data.get("properties", {}),
                provenance=[SourceProvenance(**p) for p in node_data.get("provenance", [])],
            )
            graph.add_node(node)

        for edge_data in model.get("edges", []):
            edge = GraphEdge(
                id=edge_data["id"],
                kind=EdgeKind(edge_data["kind"]),
                source=edge_data["source"],
                target=edge_data["target"],
                properties=edge_data.get("properties", {}),
                provenance=[SourceProvenance(**p) for p in edge_data.get("provenance", [])],
            )
            graph.add_edge(edge)

        return graph

    @classmethod
    def from_json(cls, text: str) -> "SysMLGraph":
        return cls.from_dict(json.loads(text))


def build_vehicle_example() -> SysMLGraph:
    graph = SysMLGraph(id="VehicleModel", name="Vehicle Model Example")

    graph.add_node(GraphNode("pkg1", NodeKind.PACKAGE, name="VehicleModel"))
    graph.add_node(GraphNode("pd_vehicle", NodeKind.PART_DEFINITION, name="Vehicle"))
    graph.add_node(GraphNode("pd_engine", NodeKind.PART_DEFINITION, name="Engine"))
    graph.add_node(GraphNode("pd_wheel", NodeKind.PART_DEFINITION, name="Wheel"))
    graph.add_node(GraphNode("pu_engine", NodeKind.PART_USAGE, name="engine"))
    graph.add_node(GraphNode("pu_wheels", NodeKind.PART_USAGE, name="wheels", properties={"multiplicity": "[4]"}))

    graph.add_edge(GraphEdge("e1", EdgeKind.OWNS, "pkg1", "pd_vehicle"))
    graph.add_edge(GraphEdge("e2", EdgeKind.OWNS, "pkg1", "pd_engine"))
    graph.add_edge(GraphEdge("e3", EdgeKind.OWNS, "pkg1", "pd_wheel"))
    graph.add_edge(GraphEdge("e4", EdgeKind.OWNS, "pd_vehicle", "pu_engine"))
    graph.add_edge(GraphEdge("e5", EdgeKind.OWNS, "pd_vehicle", "pu_wheels"))
    graph.add_edge(GraphEdge("e6", EdgeKind.TYPED_BY, "pu_engine", "pd_engine"))
    graph.add_edge(GraphEdge("e7", EdgeKind.TYPED_BY, "pu_wheels", "pd_wheel"))

    return graph


def emit_minimal_sysml(graph: SysMLGraph) -> str:
    """
    Emit a deliberately small SysML v2 textual subset:
    - package
    - part def
    - nested part usages with typedBy
    This is intentionally conservative and easy to extend.
    """
    lines: List[str] = []

    packages = graph.find_nodes(NodeKind.PACKAGE)
    root_packages = packages or [GraphNode(id="_implicit", kind=NodeKind.PACKAGE, name=graph.name or "Model")]

    for pkg in root_packages:
        pkg_name = pkg.name or "Model"
        lines.append(f"package {pkg_name} {{")

        owned_defs = [
            graph.get_node(e.target)
            for e in graph.outgoing(pkg.id, EdgeKind.OWNS)
            if graph.get_node(e.target).kind == NodeKind.PART_DEFINITION
        ]

        for part_def in owned_defs:
            owned_parts = [
                graph.get_node(e.target)
                for e in graph.outgoing(part_def.id, EdgeKind.OWNS)
                if graph.get_node(e.target).kind == NodeKind.PART_USAGE
            ]

            if not owned_parts:
                lines.append(f"  part def {part_def.name};")
                continue

            lines.append(f"  part def {part_def.name} {{")
            for part_usage in owned_parts:
                type_node = graph.typed_by(part_usage.id)
                mult = part_usage.properties.get("multiplicity", "")
                mult_text = mult if mult else ""
                type_name = type_node.name if type_node else "UnknownType"
                lines.append(f"    part {part_usage.name}{mult_text} : {type_name};")
            lines.append("  }")

        lines.append("}")

    return "\n".join(lines)


if __name__ == "__main__":
    g = build_vehicle_example()
    print("Validation errors:", g.validate())
    print()
    print("JSON:")
    print(g.to_json())
    print()
    print("SysML:")
    print(emit_minimal_sysml(g))
