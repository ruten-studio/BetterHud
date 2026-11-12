package kr.toxicity.hud.text

import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.layout.TextLayout
import kr.toxicity.hud.manager.EncodeManager
import kr.toxicity.hud.manager.ShaderManagerImpl
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.util.*
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import kotlin.math.roundToInt

/**
 * Shares glyph images between text layouts (config: `share-text-glyphs`).
 *
 * Vanilla BetterHud writes one font per text layout line, because the layout id is encoded in the provider ascent.
 * Minecraft loads the bitmap of every provider separately, so the same glyph sheet is loaded once per layout,
 * which costs a lot of client memory when many text layouts exist.
 *
 * In shared mode, all text layouts with the same providers (file, height, relative ascent, space) use one font,
 * whose ascent carries the reserved id [SHARED_ID]. The layout is identified by a horizontal shift of
 * `element * SHIFT` instead: the component is wrapped in a shift-in and a shift-out space (net advance 0),
 * and text.vsh reads the element from `pos.x`, then applies the layout's shader id and vertical offset
 * from the arrays written by the `#CreateTextElement` tag. Glyph layout, spacing and shadows are unchanged.
 */
object SharedTextGlyphs {
    /** Reserved layout id put into the ascent of shared fonts. Normal shader ids must stay below this. */
    const val SHARED_ID = (1 shl HUD_MAX_BIT) - 1
    /** Horizontal shift per element. Normal HUD x positions must stay within half of this. */
    const val SHIFT = 4096
    private const val SHIFT_IN_CODEPOINT = 0xF0000
    private const val SHIFT_OUT_CODEPOINT = 0xF8000
    private const val MAX_ELEMENTS = 0x7FFF
    private const val SHIFT_FONT = "text_element_shift"

    private val fonts = HashMap<String, Key>()
    private val elements = HashMap<Pair<HudShader, Int>, Int>()
    private val elementY = HashMap<Int, Int>()
    private val elementShader = HashMap<Int, Int>()

    var lines: List<String> = emptyList()
        private set

    fun shiftKey(): Key = createAdventureKey(SHIFT_FONT.encodeKey(EncodeManager.EncodeNamespace.FONT))

    private fun sharedAscent(y: Int) = -((SHARED_ID + (1 shl HUD_MAX_BIT) shl HUD_DEFAULT_BIT) + HUD_ADD_HEIGHT + y)

    /** Returns the element id (1..) of a text layout line placed by [shader] at [y]. */
    fun element(shader: HudShader, y: Int): Int = synchronized(this) {
        elements[shader to y] ?: run {
            val id = elements.size + 1
            if (id > MAX_ELEMENTS) throw RuntimeException("Too many shared text elements: $id")
            elements[shader to y] = id
            elementY[id] = y
            ShaderManagerImpl.addHudShader(shader) { shaderId ->
                synchronized(this) {
                    elementShader[id] = shaderId
                }
            }
            id
        }
    }

    /** Returns the shared font of [text]. The font is written once per distinct provider set. */
    fun font(resource: GlobalResource, text: TextLayout): Key {
        val array = text.startJson()
        text.source.array.forEach {
            array += jsonObjectOf(
                "type" to "bitmap",
                "file" to "$NAME_SPACE_ENCODED:${it.file}",
                "ascent" to sharedAscent(-it.ascent(text.scale)),
                "height" to (it.height * text.scale).roundToInt(),
                "chars" to it.chars
            )
        }
        val json = jsonObjectOf("providers" to array)
        val signature = json.toString()
        return synchronized(this) {
            fonts[signature] ?: run {
                val name = "shared_text_${fonts.size + 1}".encodeKey(EncodeManager.EncodeNamespace.FONT)
                PackGenerator.addTask(resource.font + "$name.json") {
                    json.toByteArray()
                }
                createAdventureKey(name).also {
                    fonts[signature] = it
                }
            }
        }
    }

    /** Wraps a line component so that text.vsh can find its element. The width is unchanged. */
    fun wrap(key: Key, element: Int, component: WidthComponent): WidthComponent {
        fun shift(codepoint: Int) = WidthComponent(Component.text().content(codepoint.parseChar()).font(key), 0)
        return shift(SHIFT_IN_CODEPOINT + element) + component + shift(SHIFT_OUT_CODEPOINT + element)
    }

    /** Called after shader ids are assigned. Writes the shift font and the element arrays for text.vsh. */
    fun compile(resource: GlobalResource) {
        synchronized(this) {
            val count = elements.size
            val shader = (0..count).map { if (it == 0) 0 else elementShader[it] ?: 0 }
            val y = (0..count).map { if (it == 0) 0 else elementY[it] ?: 0 }
            lines = listOf(
                "const int SHARED_TEXT_ID = $SHARED_ID;",
                "const float TEXT_ELEMENT_SHIFT = $SHIFT.0;",
                "const int TEXT_ELEMENT_COUNT = $count;",
                "const int TEXT_ELEMENT_SHADER[${count + 1}] = int[](${shader.joinToString(",")});",
                "const float TEXT_ELEMENT_Y[${count + 1}] = float[](${y.joinToString(",") { "$it.0" }});"
            )
            if (count == 0) return
            val advances = buildJsonObject {
                for (i in 1..count) {
                    addProperty((SHIFT_IN_CODEPOINT + i).parseChar(), i * SHIFT)
                    addProperty((SHIFT_OUT_CODEPOINT + i).parseChar(), -i * SHIFT)
                }
            }
            val json = jsonObjectOf("providers" to jsonArrayOf(jsonObjectOf("type" to "space", "advances" to advances)))
            PackGenerator.addTask(resource.font + "${SHIFT_FONT.encodeKey(EncodeManager.EncodeNamespace.FONT)}.json") {
                json.toByteArray()
            }
        }
    }

    fun clear() {
        synchronized(this) {
            fonts.clear()
            elements.clear()
            elementY.clear()
            elementShader.clear()
            lines = emptyList()
        }
    }
}
