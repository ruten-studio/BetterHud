package kr.toxicity.hud.shader

import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.location.PixelLocation

class RenderScale(
    val relativeOffset: PixelLocation,
    val scale: Scale,
) : Comparable<RenderScale> {
    companion object {
        private val scaleComparator = Comparator.comparing { scale: Scale ->
            scale.x
        }.thenComparingDouble { scale: Scale ->
            scale.y
        }
        private val comparator = Comparator.comparing { scale: RenderScale ->
            scale.relativeOffset
        }.thenComparing { scale: RenderScale ->
            scale.scale
        }

        private val scaleOne = Scale(1.0, 1.0, false)

        /**
         * The pivot is the element location. `render-scale.pivot-x` / `pivot-y` (ygg) override it,
         * so that several elements scale around one point and keep their positions to each other.
         */
        fun fromConfig(offset: PixelLocation, yamlObject: YamlObject): RenderScale {
            val section = yamlObject["render-scale"]?.asObject() ?: return RenderScale(offset, scaleOne)
            val pivot = if (section["pivot-x"] != null || section["pivot-y"] != null) PixelLocation(
                section.getAsInt("pivot-x", offset.x),
                section.getAsInt("pivot-y", offset.y),
                offset.opacity
            ) else offset
            return RenderScale(
                pivot,
                Scale(section.getAsDouble("x", 1.0), section.getAsDouble("y", 1.0), section.getAsBoolean("static-scale", false))
            )
        }
    }

    operator fun plus(other: PixelLocation) = RenderScale(relativeOffset + other, scale)
    operator fun times(multiplier: Double) = RenderScale(relativeOffset, scale * multiplier)

    data class Scale(val x: Double, val y: Double, val staticScale: Boolean) : Comparable<Scale> {
        operator fun times(multiplier: Double) = Scale(x * multiplier, y * multiplier, staticScale)
        override fun compareTo(other: Scale): Int = scaleComparator.compare(this, other)
    }
    override fun compareTo(other: RenderScale): Int = if (scale == scaleOne) scale.compareTo(other.scale) else {
        comparator.compare(this, other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RenderScale

        return if (scale == scaleOne) scale == other.scale else relativeOffset == other.relativeOffset && scale == other.scale
    }

    override fun hashCode(): Int {
        return if (scale == scaleOne) {
            scale.hashCode()
        } else {
            31 * relativeOffset.hashCode() + scale.hashCode()
        }
    }
}