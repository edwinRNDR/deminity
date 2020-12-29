package demo

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ShadeStyle
import org.openrndr.draw.glsl
import org.openrndr.math.Vector2

class ClipStyle : ShadeStyle() {
    var clipMask: ColorBuffer by Parameter()
    init {
        fragmentTransform = glsl(
            """
                vec2 uv = c_screenPosition.xy / textureSize(p_clipMask, 0);
                uv.y = 1.0 - uv.y;
                float mask = texture(p_clipMask, uv).r;
                x_fill *= mask;
                x_stroke *= mask;
        """
        )
    }

}
