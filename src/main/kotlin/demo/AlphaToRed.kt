package demo

import org.openrndr.draw.Filter
import org.openrndr.draw.filterShaderFromCode

class AlphaToRed:Filter(filterShaderFromCode("""
#version 330

uniform sampler2D tex0;
in vec2 v_texCoord0;
out vec4 o_color;
     
void main() {
    float r = texture(tex0, v_texCoord0).a;
    o_color = vec4(r, r, r, 1.0);         
}
""", "alpha-to-red"))