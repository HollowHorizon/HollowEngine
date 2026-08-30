Since 2.2.0.1:
- `.ui.kts` screens can replace existing ones: `override(TitleScreen::class)` inside `screen { }` hands
  the game's screen to the script instead. It is client-side and needs no world, so a pack can own the
  main menu; `LocalReplacedScreen` carries the screen that was replaced;
- modal popups: a popup opened with `modal = true` takes the keyboard for itself, so a dialog's own
  field is no longer typed into alongside whatever was focused behind it (the ctrl+N overlay and the
  new file/folder dialog are modal now);
- popups animate out as well as in — they sink and fade the way they rose, and stop taking input the
  moment they start leaving;
- hover tooltips (`rememberTooltip`), used on the find bar, the search overlay and the diagnostics
  panel;
- curves are flattened to half the old error budget, so svg icons no longer show facets;
- find and replace inside the editor (ctrl+F / ctrl+R): match case, whole words and regex with `$1`
  group references, every match highlighted at once, enter/F3 to walk them. It belongs to the file
  being edited, not to whichever text field happens to have focus;
- project-wide search overlay on ctrl+N, searching file names or the text inside files off the render
  thread (project view's New File/New Folder moved to alt+insert / alt+shift+insert);
- quick fixes on alt+enter, driven by the diagnostic under the caret;
- unused (and duplicate) imports are reported, with "remove import" and "remove all unused imports";
- `@SubscribeEvent` is checked against what the event bus can actually register: exactly one `Event`
  parameter, a `Unit` return, no suspend, no type parameters, no extension receiver, and a warning when
  it hides inside a class the bus never scans;
- `// TODO:`/`FIXME`/`HACK`/`XXX`/`BUG` stand out in comments, in scripts and stylesheets alike;
- calls into a `@DslMarker`-annotated DSL are coloured by the marker's fully qualified name, so one DSL
  keeps one colour and nested DSLs stay apart;
- colour pickers in HSS: every colour literal gets a swatch that opens a picker and writes the result
  back into the stylesheet;
- fixed inlay hints losing their icons, tags and click actions when split across lines;
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