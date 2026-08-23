package com.azezy.azezyball.engine

import android.opengl.GLES20
import android.util.Log

object ShaderHelper {
    private const val TAG = "ShaderHelper"

    fun compileShader(type: Int, shaderCode: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        if (shaderId == 0) {
            Log.e(TAG, "Could not create new shader.")
            return 0
        }

        GLES20.glShaderSource(shaderId, shaderCode)
        GLES20.glCompileShader(shaderId)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compilation failed: " + GLES20.glGetShaderInfoLog(shaderId))
            GLES20.glDeleteShader(shaderId)
            return 0
        }

        return shaderId
    }

    fun createAndLinkProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Error compiling shaders for program.")
            return 0
        }

        val programId = GLES20.glCreateProgram()
        if (programId == 0) {
            Log.e(TAG, "Could not create GL program.")
            return 0
        }

        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program linking failed: " + GLES20.glGetProgramInfoLog(programId))
            GLES20.glDeleteProgram(programId)
            return 0
        }

        // Shaders can be safely flagged for deletion once linked
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return programId
    }

    // Standard Shaders
    val STANDARD_VERTEX_SHADER = """
        uniform mat4 u_MVPMatrix;
        uniform mat4 u_MVMatrix;
        uniform mat4 u_NormalMatrix;
        
        attribute vec3 a_Position;
        attribute vec3 a_Normal;
        attribute vec2 a_TexCoordinate;
        
        varying vec3 v_Position;
        varying vec3 v_Normal;
        varying vec2 v_TexCoordinate;
        
        void main() {
            v_Position = vec3(u_MVMatrix * vec4(a_Position, 1.0));
            v_Normal = vec3(u_NormalMatrix * vec4(a_Normal, 0.0));
            v_TexCoordinate = a_TexCoordinate;
            gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
        }
    """.trimIndent()

    val STANDARD_FRAGMENT_SHADER = """
        precision mediump float;
        
        uniform vec3 u_LightPos;
        uniform vec4 u_Color;
        uniform sampler2D u_Texture;
        uniform int u_UseTexture;
        uniform float u_SpecularPower;
        uniform float u_Metallic;
        
        varying vec3 v_Position;
        varying vec3 v_Normal;
        varying vec2 v_TexCoordinate;
        
        void main() {
            vec3 N = normalize(v_Normal);
            vec3 L = normalize(u_LightPos - v_Position);
            vec3 V = normalize(-v_Position);
            vec3 R = reflect(-L, N);
            
            // Ambient
            float ambient = 0.35;
            
            // Diffuse
            float diffuse = max(dot(N, L), 0.0) * 0.75;
            
            // Specular (Golden metallic highlights)
            float specular = 0.0;
            if (diffuse > 0.0) {
                specular = pow(max(dot(R, V), 0.0), u_SpecularPower) * u_Metallic;
            }
            
            vec4 baseColor = u_Color;
            if (u_UseTexture == 1) {
                baseColor = texture2D(u_Texture, v_TexCoordinate) * u_Color;
            }
            
            vec3 finalColor = baseColor.rgb * (ambient + diffuse) + vec3(1.0, 0.9, 0.6) * specular;
            gl_FragColor = vec4(finalColor, baseColor.a);
        }
    """.trimIndent()

    // Particle Shader
    val PARTICLE_VERTEX_SHADER = """
        uniform mat4 u_MVPMatrix;
        attribute vec3 a_Position;
        attribute vec4 a_Color;
        attribute float a_PointSize;
        
        varying vec4 v_Color;
        
        void main() {
            v_Color = a_Color;
            gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
            gl_PointSize = a_PointSize;
        }
    """.trimIndent()

    val PARTICLE_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 v_Color;
        
        void main() {
            // Circular particle with soft radial fade
            vec2 coord = gl_PointCoord - vec2(0.5);
            float distSq = dot(coord, coord);
            if (distSq > 0.25) {
                discard;
            }
            float alpha = smoothstep(0.25, 0.0, distSq) * v_Color.a;
            gl_FragColor = vec4(v_Color.rgb, alpha);
        }
    """.trimIndent()

    // Line / Trajectory Shader
    val LINE_VERTEX_SHADER = """
        uniform mat4 u_MVPMatrix;
        attribute vec3 a_Position;
        attribute vec4 a_Color;
        varying vec4 v_Color;
        
        void main() {
            v_Color = a_Color;
            gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
        }
    """.trimIndent()

    val LINE_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 v_Color;
        
        void main() {
            gl_FragColor = v_Color;
        }
    """.trimIndent()
}
