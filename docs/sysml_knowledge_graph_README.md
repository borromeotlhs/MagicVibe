# SysML Knowledge Graph Schema + Python Classes

This artifact gives you a practical intermediate representation for a pipeline like:

```text
diagram
→ graph extractor
→ SysMLGraph object
→ SysML v2 emitter
```

Files:
- `sysml_knowledge_graph.py` — schema, classes, validation, JSON serialization, minimal SysML emitter

## What is included

The Python module contains:

- `NodeKind` enum
- `EdgeKind` enum
- `SourceProvenance`
- `GraphNode`
- `GraphEdge`
- `SysMLGraph`
- `build_vehicle_example()`
- `emit_minimal_sysml()`

## Core design

### Node kinds
- Package
- PartDefinition / PartUsage
- AttributeDefinition / AttributeUsage
- PortDefinition / PortUsage
- ItemDefinition / ItemUsage
- ConnectionDefinition / ConnectionUsage
- ActionDefinition / ActionUsage
- StateDefinition / StateUsage
- RequirementDefinition / RequirementUsage
- ViewDefinition / ViewUsage
- ViewpointDefinition

### Edge kinds
- owns
- typedBy
- specializes
- subsets
- redefines
- references
- connects
- binds
- flows
- satisfies
- verifies
- refines
- exposes
- imports

## Why this is useful

This lets you normalize data from:
- SysML v1 diagrams
- OCR output
- CSV imports
- MagicDraw exports
- LLM extraction

into one common representation before generating SysML v2.

## Suggested next step

The next artifact that would fit this cleanly is:

1. a `diagram_to_graph_schema.json`
2. a `graph_to_sysmlv2.py` emitter with more constructs
3. a `graph_validator.py` with stronger semantic checks
