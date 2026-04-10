# Local light shadow entities escaped into Iris framebuffer

## Problem

During the local light shadow pass, chunk geometry rendered into the custom local shadow atlas, but entity and anchored-model draws were still ending up in Iris-managed shadow or gbuffer framebuffers.

This produced several symptoms:

- entity shadows did not appear in the local light result;
- old/different shadow content appeared in the wrong atlas;
- RenderDoc showed alternating `Color` and `Depth-only` passes on entity draws;
- anchored/ECS models could use their regular material shader instead of the shadow-compatible path.

## Root cause

Two issues overlapped:

1. Iris `ExtendedShader.apply()` and `FallbackShader.apply()` bind their own destination framebuffer every time the shader is applied.
   Even if HollowEngine bound the local shadow atlas before flushing entity batches, Iris rebound rendering back to its own target during the entity draw itself.

2. HollowEngine custom model rendering selected `ModShaders.GLTF_ENTITY` whenever shader override mode was not active.
   In a shadow pass this is incorrect, because the pass must use the vanilla/Iris shadow-compatible entity shader instead of the regular colored model shader.

## Fix

- Added bootstrap Iris mixins for `ExtendedShader` and `FallbackShader` that rebind the active HollowEngine local shadow framebuffer at the end of `apply()` when the local shadow pass is active.
- Added a shared helper to keep the framebuffer restore logic centralized.
- Updated `ShaderUtil.SHADER` to force `GameRenderer.getRendertypeEntityCutoutShader()` whenever `ShadowRenderer.ACTIVE` is enabled, even if the normal Iris shader override path is not active.

## Expected result

- vanilla entities, player, and anchored/ECS models stay in the HollowEngine local shadow atlas during local shadow rendering;
- the local shadow pass no longer leaks entity draws into the solar shadow framebuffer or Iris gbuffer targets.
