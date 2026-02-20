# Validation CSV feature comparison

This document compares four things:

1. **Requested feature set** (interpreted from your notes and both code variants)
2. **Current repository implementation** (`validationErrorsToCSV.groovy`)
3. **Failed merge snapshot** (`validationErrorsToCSV.groovy1` conflict state)
4. **Second attempt helper file** (`validationToCSV.groovy2`)

## Requested feature set (normalized)

Based on your prompt and attached snippets, the target behavior appears to be:

- Export **current validation annotations** to CSV.
- Include direct navigation links (`mdel://<id>`) for **target and owner**.
- Work safely when validation targets are not plain UML `Element` objects (especially diagram symbols / `PresentationElement`).
- Keep annotation lookup keyed to the **original target object** (not only the unwrapped element).
- Include project/scope context (primary vs used project roots) and containment context (`OwnerChain`).
- Emit the output path in the MagicDraw UI (GUI log + notification) and write a timestamped CSV file.

## Comparison matrix

| Feature | Requested | Repo now (`validationErrorsToCSV.groovy`) | Failed merge (`validationErrorsToCSV.groovy1`) | Second attempt (`validationToCSV.groovy2`) |
|---|---|---|---|---|
| Complete runnable macro (collect + write file + notify UI) | Yes | **Yes** | HEAD side: **Yes**; other side: **No** | **No** (helper-only) |
| Uses `AnnotationManager` + `ValidationConstants.VALIDATION_ONLY` | Yes | **Yes** | HEAD side: **Yes**; other side: no collector | **No** |
| CSV escaping | Yes | **Yes** (`csvCell`) | Both sides include escaping helpers | **Yes** (`csv`) |
| `TargetURI` / `OwnerURI` as `mdel://` | Yes | **Yes** | HEAD side: **Yes**; other side: yes in helpers | **Yes** |
| Owner metadata (`OwnerName`, `OwnerType`, `OwnerID`) | Yes | **Yes** | HEAD side: **Yes**; other side: yes in helpers | **Yes** |
| Project scope labels (`PRIMARY:` / `USED:`) | Preferred | **Yes** (`projectScopeLabel`) | HEAD side: **Yes**; other side only root name (no scope prefix) | Partial (root name only) |
| Owner containment chain (`OwnerChain`) | Preferred | **Yes** | HEAD side: **Yes**; other side: no | **No** |
| Robust non-NamedElement handling (no hard dependency on `getQualifiedName`) | Yes | **Yes** (`safeQualifiedName`) | HEAD side: **Yes**; other side uses `hasProperty` | Partial |
| Handles `PresentationElement` validation targets | Yes | **No** (targets cast directly to `Element`) | Conflicting: other side adds `unwrapToElement`, HEAD side does not | **Yes** (`unwrapToElement`) |
| Annotation lookup keyed to original target object | Yes | **Yes** (`am.getAnnotations(t, subset)`) | HEAD side: **Yes**; other side explicitly documents this pattern | **Yes** |
| Notification window message | Yes | **Yes** (`NotificationManager`) | HEAD side: **Yes**; other side no | **No** |
| Output file in `logs/validation_findings_<timestamp>.csv` | Yes | **Yes** | HEAD side: **Yes**; other side no | **No** |
| Rule column support (`a?.rule?.name`) | Optional | **No** (uses `Kind`) | HEAD side: `Kind`; other side proposes `Rule` | **Yes** (`Rule`) |

## What the failed merge actually tells us

The merge conflict reveals two non-identical goals were being combined:

- **HEAD branch intent**: keep a full, production macro with file generation, scope classification, owner chain, and UI notification.
- **Other branch intent**: improve target normalization (especially `PresentationElement` unwrapping) and modularize row-building helpers.

So the failed merge is not just a textual conflict; it is a **design conflict** between:

- an end-to-end macro script, and
- a reusable helper-oriented extraction layer.

## Gap summary (repo now vs requested)

The current repository file is strong on full workflow features (write + notify + scope + chain), but it still misses one key robustness feature from your second attempt:

1. **PresentationElement unwrapping is missing**, so diagram-symbol validation targets may lose stable `Element` IDs and `mdel://` URIs.
2. The current file uses `Kind` rather than `Rule`; if your downstream analysis expects rule names, this is a schema mismatch.

## Recommendation

A best merged version should keep the current repo file as the backbone and add only these targeted upgrades:

- Add `PresentationElement` unwrapping before target column extraction.
- Preserve annotation lookup on original target object (`am.getAnnotations(t, subset)`) exactly as now.
- Optionally add a `Rule` column while retaining `Kind` (or replace intentionally, but document the schema change).

That gives you the reliability improvements from `validationToCSV.groovy2` without losing the operational macro behavior already present in `validationErrorsToCSV.groovy`.
