// What the effect shaders share. A shader of an effect author imports it the same way:
// #moj_import <hollowengine_vfx.glsl>

// Distance from the eye along the view axis for a depth-buffer value, under the projection proj.
float hollowengine_view_depth(mat4 proj, float depth) {
    return proj[3][2] / (depth * 2.0 - 1.0 + proj[2][2]);
}

// 0 where the fragment touches the scene behind it, 1 once it is softness blocks in front of it.
float hollowengine_soft_fade(sampler2D sceneDepth, vec2 screenSize, mat4 proj, float softness) {
    if (softness <= 0.0) return 1.0;
    float scene = hollowengine_view_depth(proj, texture(sceneDepth, gl_FragCoord.xy / screenSize).r);
    float own = hollowengine_view_depth(proj, gl_FragCoord.z);
    return clamp((scene - own) / softness, 0.0, 1.0);
}
