
# SysML v2 + KerML AI Grounding Document

This document consolidates KerML v1.0, SysML v2.0, and transformation guidance into a single AI‑friendly reference.

---
# 1–6 Foundation
KerML architecture, SysML v1→v2 mapping, core modeling constructs, transformation pipeline, graph representation, and minimal modeling subset.

---
# 7–9 Storage + Schema
Graph schema and serialization concepts allowing SysML models to be represented as typed graphs and JSON interchange formats.

---
# 10–12 Generation + Agent Templates
Distilled grammar subset, JSON graph schema, and prompt templates for LLM‑driven SysML generation.

---
# 13 Canonical 20 SysMLv2 Primitives
Structural:
package, part def, part, attribute def, attribute, port def, port, item def, connection

Behavior:
action def, action, state def, state

Traceability:
requirement def, requirement, satisfy, verify, refine

Architecture:
view def, viewpoint def

---
# 14 SysML v1 Diagram → SysML v2 Translation Patterns

BDD → PartDefinitions  
IBD → PartUsages + Connections  
Activity → ActionDefinitions  
StateMachine → StateDefinitions  
Requirement diagrams → RequirementDefinitions + traces

---
# 15 SysML v2 Pattern Library

Common patterns:

Subsystem decomposition
```
part def Vehicle {
   part engine : Engine
}
```

Service interface
```
port def PowerPort
```

---
# 16 SysML v2 Semantics Cheat Sheet

Key concepts:

Definition vs Usage  
Feature ownership  
Occurrence semantics  
Specialization vs Subsetting

---
# 17 KerML Concept Glossary

Element – universal base type  
Feature – property of a type  
Namespace – container of elements  
Membership – containment relationship  
Occurrence – runtime instance of a model element

---
# 18 KerML → SysML Extension Map

KerML Type → SysML PartDefinition  
KerML Feature → SysML Port / Attribute  
KerML Relationship → SysML Connection

---
# 19 KerML Relationship Semantics

specializes – inheritance  
subsets – feature narrowing  
redefines – override  
references – external linkage

---
# 20 Architecture Modeling Patterns

Layered architecture
System → Subsystem → Component

Functional decomposition

Logical vs Physical architecture separation

---
# 21 Interface Modeling Patterns

Power interfaces  
Data interfaces  
Command/control interfaces  
Streaming data flows

---
# 22 Requirements Traceability

Requirement
↓ satisfy
System Element
↓ verify
Test

---
# 23 SysML v1 → v2 Conversion Rules

Block → PartDefinition  
PartProperty → PartUsage  
Connector → ConnectionUsage  
Activity → ActionDefinition

---
# 24 Model Validation Rules

Ports must belong to parts  
Connections require compatible ports  
Usages must reference definitions

---
# 25 Model Normalization

Flatten deep package nesting  
Remove duplicate definitions  
Standardize element naming

---
# 26 Diagram Parsing Strategy

Image
→ object detection
→ topology graph
→ semantic classification

---
# 27 SysML Knowledge Graph Design

Nodes:
Part, Port, Connection, Requirement, Action

Edges:
owns, connects, flows, satisfies

---
# 28 Multi‑Agent Modeling Workflow

Vision Agent
Model Graph Agent
Transformation Agent
Validation Agent
Text Generator

---
# 29 Tool Integration

MagicDraw/Cameo Block → PartDefinition

---
# 30 Interchange Formats

KerML JSON
SysML textual notation
Graph representation

---
# 31 Example SysMLv2 Models

Vehicle system
Satellite system
Network router system

---
# 32 Diagram → Text Dataset Format

JSON dataset describing diagrams and corresponding SysML text.

---
# 33 Synthetic Dataset Generation

Random system generator
→ render diagrams
→ produce ground truth models

---
# 34 Common Modeling Errors

Circular containment
Missing type references
Unconnected ports

---
# 35 Structural Integrity Checks

No orphan parts
No untyped ports
Valid multiplicities

---
# 36 Behavior Modeling

State machines
Action flows
Interactions

---
# 37 Simulation Integration

SysML → Modelica / Simulink
Executable architecture models

---
# 38 Digital Engineering Integration

Digital twins
Lifecycle modeling
Enterprise architecture links

---
End of Document
