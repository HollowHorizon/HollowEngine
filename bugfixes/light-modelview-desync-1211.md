## 1.21.1 local light view-space desync

- Symptom: point/spot light footprints were offset and did not follow camera rotation correctly after the 1.21.1 port.
- Root cause: `LevelRendererStagesMixin` started emitting `RenderLevelStageEvent` with `new PoseStack()` for every stage. `ClusteredLightingManager` then used `event.poseStack.last().pose()` as the view matrix, so CPU-side light positions were transformed with identity instead of the actual gbuffer model-view matrix.
- Fix: stop deriving lighting matrices from `RenderLevelStageEvent.poseStack` and use Iris `CapturedRenderingState.INSTANCE.gbufferModelView / gbufferProjection` instead, with `RenderSystem` matrices as fallback outside shaderpack rendering.
- Result: CPU-side `viewSpacePosition`, cluster/tile bounds, and flare screen projection use the same camera-space basis as the shaderpack.
