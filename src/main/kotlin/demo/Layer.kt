@file:Suppress("EnumEntryName")

package demo

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import demo.Layer.Object.Attributes.*
import demo.Layer.Object.Clipping.*
import demo.Layer.Object.Staggers.Stagger
import demo.Layer.Object.Staggers.Stagger.*
import demo.Layer.Object.Stepping.*
import mu.KotlinLogging
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.keyframer.FunctionExtensions
import org.openrndr.extra.keyframer.Keyframer
import org.openrndr.extra.easing.Easing
import org.openrndr.extra.easing.EasingFunction
import org.openrndr.math.Vector3
import org.openrndr.math.map
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.*
import org.openrndr.extra.filewatcher.watchFile
import java.io.File
import kotlin.math.floor
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/*
 * Here you will find some "interesting" choices have been made.
 * - All constructor arguments have default values. This is such that the zero-argument / default constructor is
 *   generated by the Kotlin compiler.
 * - Property names are in kebab-case. This makes the names used in the json inarguably nicer. We could have used Gson's
 *   annotations to set the name, but that creates clutter and a double naming scheme to work with.
 * - Excessive use of optional types. They are due to the cascading property of Layer.Object.
 * - Immutable data. Just makes things easier.
 */
/**
 * Layer class.
 * A  [Layer] is typically loaded from a json file.
 */
data class Layer(
    val enabled: Boolean = true,
    val `z-index`: Int = 0,
    val camera: Camera = Camera(),
    val blend: Blend = Blend(),
    val prototypes: Map<String, Object> = emptyMap(),
    val objects: List<Object> = emptyList(),
    val properties: Map<String, Double> = emptyMap(),
) {
    @Transient
    var sourceFile = File("[unknown-source]")

    /**
     * Layer blend settings
     */
    class Blend(val mode: BlendMode = BlendMode.normal) {
        enum class BlendMode {
            normal,
            multiply
        }
    }

    /**
     * Layer camera settings
     */
    class Camera(val type: CameraType = CameraType.ortho, val keyframer: List<Map<String, Any>> = emptyList()) {
        enum class CameraType {
            ortho,
            perspective
        }

        @Suppress("MemberVisibilityCanBePrivate")
        class Animation : Keyframer() {
            val position by Vector3Channel(arrayOf("x", "y", "z"), Vector3.ZERO)
            val orientation by Vector3Channel(arrayOf("rx", "ry", "rz"), Vector3.ZERO)
            val scale by DoubleChannel("scale", 1.0)
            val fov by DoubleChannel("fov", 45.0)
            val magnitude by DoubleChannel("magnitude", 720.0)
            val orthoNear by DoubleChannel("ortho-near", 1000.0)
            val orthoFar by DoubleChannel("ortho-far", -1000.0)
            val perspectiveNear by DoubleChannel("perspective-near", 0.1)
            val perspectiveFar by DoubleChannel("perspective-far", 1000.0)

            val transform
                get() = buildTransform {
                    translate(-position)
                    scale(this@Animation.scale)
                    rotate(Vector3.UNIT_Z, orientation.z)
                    rotate(Vector3.UNIT_Y, orientation.y)
                    rotate(Vector3.UNIT_X, orientation.x)
                }
        }

        val animation by lazy {
            Animation().apply {
                loadFromKeyObjects(
                    keyframer,
                    emptyMap(),
                    FunctionExtensions.EMPTY
                )
            }
        }
    }


    /**
     * Object is a renderable entity
     */
    data class Object(
        val prototype: String = "",
        val time: Double? = null,
        val `z-index`: Int? = null,
        val type: ObjectType? = null,
        val target: Target? = null,
        val clipping: Clipping = Clipping(),
        val assets: List<String>? = null,
        val keyframer: List<Map<String, Any>> = emptyList(),
        val repetitions: Repetitions = Repetitions(),
        val repetitionCounter: Int = 0,
        val staggers: Staggers? = null,
        val stepping: Stepping = Stepping(),
        val attributes: Attributes = Attributes(),
        val properties: Map<String, Double> = emptyMap()
    ) : Cascadable<Object> {
        /**
         * Object animation keyframer
         */
        @Suppress("MemberVisibilityCanBePrivate")
        class Animation : Keyframer() {
            val position by Vector3Channel(arrayOf("x", "y", "z"), Vector3.ZERO)
            val orientation by Vector3Channel(arrayOf("rx", "ry", "rz"), Vector3.ZERO)
            val scale by DoubleChannel("scale", 1.0)
            val c0 by DoubleChannel("c0", defaultValue = 0.0)
            val c1 by DoubleChannel("c1", defaultValue = 1.0)

            val strokeWeight by DoubleChannel("stroke-weight", defaultValue = 1.0)
            val stroke by RGBaChannel(
                arrayOf("stroke-r", "stroke-g", "stroke-b", "stroke-a"),
                defaultValue = ColorRGBa.WHITE
            )
            val fill by RGBaChannel(
                arrayOf("fill-r", "fill-g", "fill-b", "fill-a"),
                defaultValue = ColorRGBa.TRANSPARENT
            )

            val imageTint by RGBaChannel(
                arrayOf("image-tint-r", "image-tint-g", "image-tint-b", "image-tint-a"),
                ColorRGBa.WHITE
            )
            val imageDither by DoubleChannel("image-dither", 0.0)
            val imageLeft by DoubleChannel("image-left", 0.0)
            val imageRight by DoubleChannel("image-right", 1.0)
            val imageTop by DoubleChannel("image-top", 0.0)
            val imageBottom by DoubleChannel("image-bottom", 1.0)

            val assetIndex by DoubleChannel("asset-index", 0.0)

            val clipBlend by DoubleChannel("clip-blend", 1.0)
            val objectClipBlend by DoubleChannel("object-clip-blend", 1.0)

            val curtainStart by DoubleChannel("curtain-start", 0.0)
            val curtainEnd by DoubleChannel("curtain-end", 1.0)
            val transform
                get() = buildTransform {
                    translate(position)
                    scale(this@Animation.scale)
                    rotate(Vector3.UNIT_Z, orientation.z)
                    rotate(Vector3.UNIT_Y, orientation.y)
                    rotate(Vector3.UNIT_X, orientation.x)
                }
        }

        val animation by lazy {
            Animation().apply {
                loadFromKeyObjects(
                    keyframer,
                    properties + mapOf("rep" to repetitionCounter.toDouble()),
                    FunctionExtensions.EMPTY
                )
            }
        }
        val duration
            get() = animation.duration

        override fun over(lower: Object): Object {
            return copy(
                prototype,
                time over lower.time,
                `z-index` over lower.`z-index`,
                type over lower.type,
                target over lower.target,
                clipping over lower.clipping,
                assets over lower.assets,
                keyframer + lower.keyframer,
                repetitions over lower.repetitions,
                repetitionCounter,
                staggers over lower.staggers,
                stepping over lower.stepping,
                attributes over lower.attributes,
                lower.properties + properties
            )
        }

        /**
         * Resolve the object.
         * apply defaults and prototypes and return a [Layer.Object] copy without null properties
         */
        fun resolve(demo: Demo, prototypes: Map<String, Object>, properties: Map<String, Double>): Object {
            val toCascade = listOfNotNull(
                default.copy(properties = default.properties + properties),
                prototypes["*"]
            ) + prototype.split(" ").map { it.trim() }.mapNotNull { prototypes[it] } + listOf(this)
            val resolved = toCascade.reduce { acc, p -> p over acc }
            resolved.staggers!!
            resolved.assets!!
            return resolved.copy(
                staggers = resolved.staggers.resolve(resolved.animation),
                assets = resolved.assets.flatMap { assetPath ->
                    if (assetPath.contains("*")) {
                        val path = assetPath.split("*").first()
                        val ext = assetPath.split("*.")[1].toLowerCase()
                        File(demo.dataBase, "assets/$path").listFiles { it -> it.extension.toLowerCase() == ext }!!
                            .map {
                                it.relativeTo(File(demo.dataBase, "assets")).path
                            }.sorted()
                    } else {
                        listOf(assetPath)
                    }
                },
            )
        }

        enum class Target {
            image,
            `clip-a`,
            `clip-b`
        }

        enum class ObjectType {
            svg,
            image,
            `svg-3d`,
            gltf,
            `text-curtain`
        }

        /**
         * [Layer.Object] clipping settings
         */
        class Clipping(val mask: ClipMask? = null) : Cascadable<Clipping> {
            enum class ClipMask {
                none,
                a,
                b,
                `invert-a`,
                `invert-b`
            }

            override fun over(lower: Clipping): Clipping {
                return Clipping(mask over lower.mask)
            }
        }

        /**
         * [Layer.Object] animation stepping settings
         */
        class Stepping(
            val mode: SteppingMode? = null,
            val steps: Int? = null,
            val inertia: Double? = null
        ) : Cascadable<Stepping> {
            enum class SteppingMode {
                none,
                discrete
            }

            fun stepTime(animation: Keyframer, time: Double): Double {
                val duration by lazy { animation.duration }
                return when (mode) {
                    null, SteppingMode.none -> time
                    SteppingMode.discrete -> {
                        steps!!; inertia!!
                        val stepDuration = duration / steps
                        val step = time / stepDuration
                        val stepIndex = floor(step)
                        val stepProgress = step - stepIndex
                        (stepIndex * stepDuration + stepProgress * stepDuration * inertia).coerceIn(0.0, duration)
                    }
                }
            }

            override fun over(lower: Stepping) = Stepping(
                mode over lower.mode,
                steps over lower.steps,
                inertia over lower.inertia
            )
        }

        class Staggers : ArrayList<Stagger>() {
            companion object {
                fun of(vararg staggers: Stagger): Staggers {
                    val s = Staggers()
                    s.addAll(staggers)
                    return s
                }

                fun of(staggers: List<Stagger>): Staggers {
                    val s = Staggers()
                    s.addAll(staggers)
                    return s
                }
            }

            /**
             * [Layer.Object] animation staggering settings
             */
            data class Stagger(
                val time: Double = 0.0,
                val duration: Double = 0.0,
                val mode: StaggerMode = StaggerMode.none,
                val order: StaggerOrder = StaggerOrder.`contour-index`,
                val easing: String = "linear",
                val seed: Int = 100,
                val window: Int = 0
            ) {
                val easingFunction by lazy { easingFunctionFromName(easing) }

                enum class StaggerMode {
                    none,
                    `in-out`,
                    `in`,
                    out,
                }

                enum class StaggerOrder {
                    `contour-index`,
                    `reverse-contour-index`,
                    random,
                }

                val end: Double
                    get() {
                        return time + duration
                    }
                private val shapeOrder = mutableMapOf<Int, List<Int>>()

                /**
                 * calculate staggered time
                 */
                fun stagger(objectTime: Double, shapeCount: Int, shapeIndex: Int): Double {
                    val shapeOrder = shapeOrder.getOrPut(shapeCount) {
                        when (order) {
                            StaggerOrder.`contour-index` -> List(shapeCount) { it }
                            StaggerOrder.`reverse-contour-index` -> List(shapeCount) { it }.reversed()
                            StaggerOrder.random -> List(shapeCount) { it }.shuffled(Random(seed))
                        }
                    }[shapeIndex]
                    val segmentTime =
                        easingFunction.invoke(((objectTime - time) / duration).coerceIn(0.0, 1.0), 0.0, 1.0, 1.0)
                    return when (mode) {
                        StaggerMode.none -> objectTime
                        StaggerMode.`in-out` -> {
                            val staggerStart = (shapeOrder * 1.0) / (shapeCount + window)
                            val staggerEnd = (shapeOrder + 1.0 + window) / (shapeCount + window)
                            segmentTime.map(staggerStart, staggerEnd, time, end, clamp = true)
                        }
                        StaggerMode.`in` -> {
                            val staggerStart = (shapeOrder * 1.0) / (shapeCount + window)
                            val staggerEnd = 1.0
                            segmentTime.map(staggerStart, staggerEnd, time, end, clamp = true)
                        }
                        StaggerMode.out -> {
                            val staggerStart = 0.0
                            val staggerEnd = (shapeOrder + 1.0 + window) / (shapeCount + window)
                            segmentTime.map(staggerStart, staggerEnd, time, end, clamp = true)
                        }
                    }
                }
            }

            /**
             * Find the active stagger segment
             */
            fun stagger(objectTime: Double): Stagger {
                if (objectTime <= 0.0) {
                    return first()
                }
                if (objectTime >= last().time) {
                    return last()
                }
                return find { it.time <= objectTime && it.end > objectTime }!!
            }

            /**
             * Resolve stagger duration and shape count
             */
            fun resolve(animation: Animation): Staggers {
                if (isEmpty()) {
                    return of(Stagger(0.0, duration = animation.duration))
                }
                val durations = this.windowed(2, 1).map { it[1].time - it[0].time } + listOf(
                    animation.duration - last().time
                )
                return of((this zip durations).map {
                    it.first.copy(duration = it.second)
                })
            }
        }

        /**
         * [Layer.Object] repetition settings
         */
        data class Repetitions(
            val count: Int? = null,
            val interval: Double? = null,
        ) : Cascadable<Repetitions> {
            override fun over(lower: Repetitions) = Repetitions(
                count over lower.count,
                interval over lower.interval
            )
        }

        /**
         * Attributes describes where draw attribute data should come from
         */
        data class Attributes(
            val `stroke-weight`: AttributeSource? = null,
            val stroke: AttributeSource? = null,
            val fill: AttributeSource? = null
        ) : Cascadable<Attributes> {
            enum class AttributeSource {
                user,
                asset,
                modulate
            }

            override fun over(lower: Attributes) = Attributes(
                `stroke-weight` over lower.`stroke-weight`,
                stroke over lower.stroke,
                fill over lower.fill
            )
        }

        /**
         * Flatten repetitions.
         * Create a copy of this [Layer] in which all [Layer.Object] repetitions have been flattened out.
         * That means that [Layer.Object] is cloned as many times as it is repeated.
         */
        fun flattenRepetitions() = (0 until repetitions.count!!).map { repetition ->
            copy(
                time = time!! + repetition * repetitions.interval!!,
                repetitionCounter = repetition,
                repetitions = Repetitions(1, 0.0),
            )
        }

        companion object {
            /**
             * Object default values, this is used to resolve Object properties to guaranteed non-null properties
             */
            val default = Object(
                time = 0.0,
                `z-index` = 0,
                type = ObjectType.svg,
                target = Target.image,
                clipping = Clipping(mask = ClipMask.none),
                assets = listOf("asset-not-specified"),
                keyframer = emptyList(),
                repetitions = Repetitions(
                    count = 1,
                    interval = 0.0
                ),
                repetitionCounter = 0,
                staggers = Staggers.of(
                    Stagger(
                        time = 0.0,
                        mode = StaggerMode.none,
                        order = StaggerOrder.`contour-index`,
                        seed = 100,
                        window = 0
                    )
                ),
                stepping = Stepping(
                    mode = SteppingMode.none,
                    steps = 10,
                    inertia = 0.0
                ),
                attributes = Attributes(
                    `stroke-weight` = AttributeSource.user,
                    stroke = AttributeSource.user,
                    fill = AttributeSource.user
                )
            )
        }
    }

    /**
     * Create a resolved copy of the [Layer].
     * In a resolved copy all [Layer.Object] prototypes have been applied and repetitions have been flattened.
     */
    fun resolve(demo: Demo) = copy(
        objects = objects.map { it.resolve(demo, prototypes, properties) }.flatMap { it.flattenRepetitions() },
    ).also {
        it.sourceFile = this.sourceFile
    }

    companion object {
        fun loadFromJson(file: File): Layer {
            try {
                return GsonBuilder().setLenient().create().fromJson(file.readText(), Layer::class.java)
            } catch (e: Throwable) {
                error("failed to load layer from ${file.path}. ${e.message}")
            }
        }

        fun watch(program: Program, demo: Demo, file: File): () -> Layer {
            return program.watchFile(file) {
                val layer = loadFromJson(file)
                layer.sourceFile = file
                layer.resolve(demo)
            }
        }
    }
}

fun easingFunctionFromName(easingCandidate: String): EasingFunction {
    return when (easingCandidate) {
        "linear" -> Easing.Linear.function
        "back-in" -> Easing.BackIn.function
        "back-out" -> Easing.BackOut.function
        "back-in-out" -> Easing.BackInOut.function
        "bounce-in" -> Easing.BounceIn.function
        "bounce-out" -> Easing.BounceOut.function
        "bounce-in-out" -> Easing.BackInOut.function
        "circ-in" -> Easing.CircIn.function
        "circ-out" -> Easing.CircOut.function
        "circ-in-out" -> Easing.CircInOut.function
        "cubic-in" -> Easing.CubicIn.function
        "cubic-out" -> Easing.CubicOut.function
        "cubic-in-out" -> Easing.CubicInOut.function
        "elastic-in" -> Easing.ElasticIn.function
        "elastic-out" -> Easing.ElasticInOut.function
        "elastic-in-out" -> Easing.ElasticOut.function
        "expo-in" -> Easing.ExpoIn.function
        "expo-out" -> Easing.ExpoOut.function
        "expo-in-out" -> Easing.ExpoInOut.function
        "quad-in" -> Easing.QuadIn.function
        "quad-out" -> Easing.QuadOut.function
        "quad-in-out" -> Easing.QuadInOut.function
        "quart-in" -> Easing.QuartIn.function
        "quart-out" -> Easing.QuartOut.function
        "quart-in-out" -> Easing.QuartInOut.function
        "quint-in" -> Easing.QuintIn.function
        "quint-out" -> Easing.QuintOut.function
        "quint-in-out" -> Easing.QuintInOut.function
        "sine-in" -> Easing.SineIn.function
        "sine-out" -> Easing.SineOut.function
        "sine-in-out" -> Easing.SineInOut.function
        "one" -> Easing.One.function
        "zero" -> Easing.Zero.function
        else -> error("unknown easing name '$easingCandidate'")
    }
}
