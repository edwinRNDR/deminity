package demo

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ShadeStyle
import org.openrndr.draw.glsl

class ClipStyle : ShadeStyle() {
    var clipMask: ColorBuffer by Parameter()
    var clipBlend: Double by Parameter()

    init {
        fragmentTransform = glsl(
            """
                vec2 uv = c_screenPosition.xy / textureSize(p_clipMask, 0);
                uv.y = 1.0 - uv.y;
                float mask = mix(1.0, texture(p_clipMask, uv).r, p_clipBlend);
                x_fill *= mask;
                x_stroke *= mask;
        """
        )

        clipBlend = 1.0
    }
}
