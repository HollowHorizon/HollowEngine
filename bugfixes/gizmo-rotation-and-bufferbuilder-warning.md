## Gizmo rotation instability and BufferBuilder warnings

- Symptom 1: frequent `Clearing BufferBuilder with unused batches` warnings during normal level rendering.
- Root cause 1: anchored model rendering always called `bufferSource.endBatch()` even when no anchored model actually wrote any vertices into the shared `BufferSource`.
- Fix 1: make anchored model rendering report whether anything was rendered and call `endBatch()` only when at least one anchored model submitted geometry.

- Symptom 2: transform gizmo could appear duplicated and rotation snapped incorrectly in some angular ranges, with tiny input producing near-90 degree jumps.
- Root cause 2: gizmo state was split into `gizmoTransform` translation and a separate post-multiplied rotation/scale matrix (`gizmoClientOffset`). During editing the resulting matrix composition and decomposition path was unstable.
- Fix 2: store the full world TRS directly in `gizmo.gizmoTransform` and decompose that matrix directly when applying edits back into `TransformComponent`.

- Follow-up: spot-light preview orientation should not be reconstructed from a forward direction vector, because that introduces ambiguity and angle-dependent axis flips. Use the resolved transform quaternion directly for the preview cone and normalize quat conversions between gizmo and component transforms.
- Follow-up: anchored model rendering path no longer forces `BufferSource.endBatch()` at the end of the stage, because that path is primarily VAO / instancing based and can legitimately leave the shared buffer source empty.
- Follow-up: translation gizmo should remain aligned to world axes even when the edited object is rotated. The gizmo basis is now mode-dependent: `TRANSLATE` uses world rotation, while `ROTATE` / `SCALE` keep the resolved object rotation.
- Follow-up: `BatchingRenderer` must validate primitive buffers before calling `source.getBuffer(renderType)`. Some shadow-pass primitives could reserve a batch without emitting vertices, which is a direct trigger for Iris / vanilla `Clearing BufferBuilder with unused batches` warnings.
- Follow-up: anchored models are rendered from `AFTER_ENTITIES`, which on 1.21.1 is injected around vanilla `MultiBufferSource.BufferSource.endLastBatch()`. A full `endBatch()` there is unsafe because it can flush unrelated entity/player render types. The correct fix is to track the exact batched `RenderType`s opened by anchored models and close only those with `endBatch(renderType)`.
