Since 2.2.0.1:
- HSS is declared in one schema: properties, aliases, value grammar and docs live together, so completion,
  inlay hints, validation and the compiler can never disagree;
- HSS list properties are named in the plural (`animations`, `transitions`, `shadows`), with the CSS
  spellings kept as aliases;
- HSS in the IDE: signatures in completion, per-slot value suggestions, argument hints on shorthands
  (`margin: 8px 8px 60px 8px`), keyframe-aware highlighting and diagnostics on the exact token;
- clickable inlay hints: `namespace:path` literals in scripts and stylesheets open the file they name
  (game resources open read-only);
- inlay hints are ordinary styled nodes: their content is typed (labels, icons), their looks come from
  `.code-editor-inlay*` rules and the room they take is measured, not guessed;
- `.parent:hover .child` now behaves like the state rule it is — it stacks with the child's own state
  rules and overlays node modifiers instead of cascading inside the base layer;
- new HSS properties: per-edge borders, per-axis transforms (`translate-y`, `scale-x`, …) and `cursor`;
- `font-size` accepts a share of the surrounding text (`85%`, `0.85em`), resolved against the inherited
  size — inlay hints now follow the editor font instead of pinning their own;
- fixed dragging a dock tab not moving it: the tab's transform was built and thrown away;
- dock tabs displaced by a reorder now slide to their new place instead of jumping: the offset they
  slide from survives the frame that draws it, rather than being consumed while composing;
- fixed ctrl+wheel zoom in the image editor: the IDE swallowed the wheel before it reached the UI, and
  the wheel skipped nodes that listen for it but have nothing to scroll. It now walks from the deepest
  node under the pointer out to whatever scrolls, and the code font resizes from its own editor;
- persistent typed `data` container on any entity or player (survives death and dimension changes);
- text-field, sliders & checkbox widgets;
- optional iris integration;
- new ui replacement bindings;
- insets hints in IDE;
- typing-delays in UI;
- rewrite UIs in **Compose Runtime**;
- new docking system for UI (hollowengine.toml -> `debugMode=true` & V-keybind in world - open demo);
- update to **Kotlin 2.4.0**.