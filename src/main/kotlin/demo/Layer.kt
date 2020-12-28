package demo

import bass.Channel
import com.google.gson.Gson
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.events.listen
import org.openrndr.extra.fx.dither.ADither
import org.openrndr.extra.keyframer.FunctionExtensions
import org.openrndr.extra.keyframer.Keyframer
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.map
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.*
import org.openrndr.svg.loadSVG
import org.operndr.extras.filewatcher.watch
import org.operndr.extras.filewatcher.watchFile
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

class ObjectAnimation : Keyframer() {
    val position by Vector3Channel(arrayOf("x", "y", "z"), Vector3.ZERO)
    val orientation by Vector3Channel(arrayOf("rx", "ry", "rz"), Vector3.ZERO)
    val scale by DoubleChannel("scale", 1.0)
    val opacity by DoubleChannel("opacity")
    val c0 by DoubleChannel("c0", defaultValue = 0.0)
    val c1 by DoubleChannel("c1", defaultValue = 1.0)

    val strokeWeight by DoubleChannel("stroke-weight", defaultValue = 1.0)
    val stroke by RGBaChannel(arrayOf("stroke-r", "stroke-g", "stroke-b", "stroke-a"), defaultValue = ColorRGBa.WHITE)
    val fill by RGBaChannel(arrayOf("fill-r", "fill-g", "fill-b", "fill-a"), defaultValue = ColorRGBa.TRANSPARENT)

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

    val transform
        get() = buildTransform {
            translate(position)
            scale(this@ObjectAnimation.scale)
            rotate(Vector3.UNIT_Z, orientation.z)
            rotate(Vector3.UNIT_Y, orientation.y)
            rotate(Vector3.UNIT_X, orientation.x)
        }
}

class CameraAnimation : Keyframer() {
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
            scale(this@CameraAnimation.scale)
            rotate(Vector3.UNIT_Z, orientation.z)
            rotate(Vector3.UNIT_Y, orientation.y)
            rotate(Vector3.UNIT_X, orientation.x)
        }
}

class Layer(val zIndex: Int = 0, val camera: Camera = Camera(), val objects: List<Object> = emptyList()) {

    var sourceFile = File("[unknown-source]")

    class Camera(val type: CameraType = CameraType.ortho, val keyframer: List<Map<String, Any>> = emptyList()) {
        val animation by lazy {
            CameraAnimation().apply {
                loadFromKeyObjects(
                    keyframer,
                    emptyMap(),
                    FunctionExtensions.EMPTY
                )
            }
        }

        enum class CameraType {
            ortho,
            perspective
        }
    }

    class Object(
        val time: Double = 0.0,
        val `z-index`: Int = 0,
        val type: ObjectType = ObjectType.svg,
        val asset: String = "default-asset",
        var assets: List<String> = emptyList(),
        val keyframer: List<Map<String, Any>> = emptyList(),
        val repetitions: Repetitions = Repetitions(),
        val repetitionCounter: Int = 0,
        val stagger: Stagger = Stagger(),
        val stepping: Stepping = Stepping(),
        val attributes: Attributes = Attributes()
    ) {
        val animation by lazy {
            ObjectAnimation().apply {
                loadFromKeyObjects(
                    keyframer,
                    mapOf("rep" to repetitionCounter.toDouble()),
                    FunctionExtensions.EMPTY
                )
            }
        }
        val duration
            get() = animation.duration

        enum class ObjectType {
            svg,
            image,
            `svg-3d`
        }

        enum class SteppingMode {
            none,
            discrete
        }

        class Stepping(val mode: SteppingMode = SteppingMode.none, val steps: Int = 10, val inertia: Double = 0.0) {
            fun stepTime(animation: Keyframer, time: Double): Double {
                val duration by lazy { animation.duration }

                return when (mode) {
                    SteppingMode.none -> time
                    SteppingMode.discrete -> {
                        val stepDuration = duration / steps
                        val step = time / stepDuration
                        val stepIndex = floor(step)
                        val stepProgress = step - stepIndex
                        (stepIndex * stepDuration + stepProgress * stepDuration * inertia).coerceIn(0.0, duration)
                    }
                }
            }
        }


        enum class StaggerMode {
            none,
            `in-out`,
        }

        enum class StaggerOrder {
            `contour-index`,
            `reverse-contour-index`,
            random,
        }

        class Stagger(
            val mode: StaggerMode = StaggerMode.none,
            val order: StaggerOrder = StaggerOrder.`contour-index`,
            val seed: Int = 100,
            val window: Int = 0
        )

        class Repetitions(val count: Int = 1, val interval: Double = 0.0)

        enum class AttributeSource {
            user,
            asset,
            modulate
        }

        class Attributes(
            val `stroke-weight`: AttributeSource = AttributeSource.user,
            val stroke: AttributeSource = AttributeSource.user,
            val fill: AttributeSource = AttributeSource.user
        )


        fun flattenRepetitions(demo: Demo) = (0 until repetitions.count).map {
            Object(
                time = time + it * repetitions.interval,
                `z-index` = `z-index`,
                type = type,
                asset = asset,
                assets = assets.let { if (it.isEmpty()) listOf(asset) else it }.flatMap {
                    if (it.contains("*")) {
                        val path = it.split("*").first()
                        val ext = it.split("*.")[1].toLowerCase()
                        File(demo.dataBase, "assets/$path").listFiles { it -> it.extension.toLowerCase() == ext }.map {
                            it.relativeTo(File(demo.dataBase, "assets")).path
                        }.sorted()
                    } else {
                        listOf(it)
                    }
                },
                keyframer = keyframer,
                repetitions = Repetitions(1, 0.0),
                repetitionCounter = it,
                stagger = stagger,
                stepping = stepping,
                attributes = attributes
            )
        }
    }

    fun flattenRepetitions(demo: Demo) = Layer(
        zIndex = zIndex,
        camera = camera,
        objects = objects.flatMap { it.flattenRepetitions(demo) },
    ).also {
        it.sourceFile = this.sourceFile
    }

    companion object {
        fun loadFromJson(file: File): Layer {
            return Gson().fromJson(file.readText(), Layer::class.java)
        }

        fun watch(program: Program, demo: Demo, file: File): () -> Layer {
            return program.watchFile(file) {
                val layer = loadFromJson(file)
                layer.sourceFile = file
                layer.flattenRepetitions(demo)
            }
        }
    }
}

class LayerRenderer(val program: Program, val demo: Demo) {

    var channel = Channel()
    var enableUI = false

    class ObjectDraw3D(
        val shapeIndex: Int,
        val fill: ColorRGBa?,
        val triangulation: VertexBuffer?,
        val paths: List<ObjectPath3D>
    )

    class ObjectPath3D(val stroke: ColorRGBa?, val strokeWeight: Double?, val path3D: Path3D)


    private val compositionWatchers = mutableMapOf<String, () -> Composition>()
    private val compositionShapes = mutableMapOf<String, List<ShapeNode>>()
    private val compositionDraws3D = mutableMapOf<String, List<ObjectDraw3D>>()
    private val images = mutableMapOf<String, ColorBuffer>()
    private val processedImages = mutableMapOf<String, ColorBuffer>()

    private val dither by lazy { ADither() }

    private var cuePoint = 0.0

    init {
        program.mouse.cursorVisible = false

        listOf(program.mouse.dragged, program.mouse.buttonDown).listen {
            val timescale = (RenderTarget.active.width - 160) / (demo.duration * demo.timescale)
            val time = ((it.position.x - 150.0) / timescale).coerceAtLeast(0.0)
            if (KeyModifier.SHIFT in it.modifiers) {
                cuePoint = time
            }
            channel.setPosition(time / demo.timescale)
        }

        program.keyboard.keyDown.listen {
            if (it.key == KEY_TAB) {
                enableUI = !enableUI
                program.mouse.cursorVisible = enableUI
            }

            if (it.key == KEY_ARROW_DOWN) {
                cuePoint = program.seconds * demo.timescale
            }

            if (it.key == KEY_ARROW_UP) {
                channel.setPosition(cuePoint / demo.timescale)
            }
        }

    }


    private val layerWatchers =
        File(demo.dataBase, "animations").listFiles { it -> it.isFile && it.extension == "json" }.map {
            Layer.watch(program, demo, it).apply {
                watch {
                    channel.setPosition(cuePoint / demo.timescale)

                    it.objects.filter {
                        it.type == Layer.Object.ObjectType.image
                    }.map { obj ->
                        for (asset in obj.assets) {
                            val image = images.getOrPut(asset) {
                                loadImage(File(demo.dataBase, "assets/${asset}"))
                            }
                            processedImages.getOrPut(asset) {
                                image.createEquivalent(format = ColorFormat.RGBa, type = ColorType.UINT8)
                            }
                        }
                    }

                    it.objects.filter {
                        it.type == Layer.Object.ObjectType.svg
                    }.map { obj ->
                        for (asset in obj.assets) {
                            compositionWatchers.getOrPut(asset) {
                                watchFile(program, File(demo.dataBase, "assets/${asset}")) {
                                    loadSVG(it)
                                }.apply {
                                    this.watch {
                                        compositionShapes[asset] = it.findShapes().map {
                                            it.flatten()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    it.objects.filter {
                        it.type == Layer.Object.ObjectType.`svg-3d`
                    }.map { obj ->
                        for (asset in obj.assets) {
                            compositionWatchers.getOrPut(asset) {
                                watchFile(program, File(demo.dataBase, "assets/${asset}")) {
                                    loadSVG(it)
                                }.apply {
                                    this.watch {
                                        var contourIndex = 0
                                        compositionDraws3D[asset] = it.findShapes().mapIndexed { shapeIndex, it ->
                                            val flattened = it.flatten()
                                            val paths = flattened.shape.contours.map { contour ->
                                                val path = path3D {
                                                    moveTo(contour.position(0.0).xy0)
                                                    for (c in contour.segments) {
                                                        when (c.type) {
                                                            SegmentType.LINEAR -> lineTo(c.end.xy0)
                                                            SegmentType.QUADRATIC -> curveTo(
                                                                c.control[0].xy0,
                                                                c.end.xy0
                                                            )
                                                            SegmentType.CUBIC -> curveTo(
                                                                c.control[0].xy0,
                                                                c.control[1].xy0,
                                                                c.end.xy0
                                                            )
                                                        }
                                                    }
                                                    if (contour.closed) {
                                                        close()
                                                    }
                                                }
                                                contourIndex++
                                                ObjectPath3D(it.effectiveStroke, it.effectiveStrokeWeight, path)
                                            }
                                            val triangulation = if (it.shape.topology != ShapeTopology.OPEN) {
                                                val triangles = flattened.shape.triangulation
                                                val vb = vertexBuffer(vertexFormat {
                                                    position(3)
                                                    textureCoordinate(2)
                                                }, triangles.size * 3)
                                                vb.put {
                                                    for (triangle in triangles) {
                                                        write(triangle.x1.xy0)
                                                        write(triangle.x1 / Vector2(1280.0, 720.0))
                                                        write(triangle.x2.xy0)
                                                        write(triangle.x2 / Vector2(1280.0, 720.0))
                                                        write(triangle.x3.xy0)
                                                        write(triangle.x3 / Vector2(1280.0, 720.0))
                                                    }
                                                }
                                                vb
                                            } else {
                                                null
                                            }
                                            ObjectDraw3D(shapeIndex, it.effectiveFill, triangulation, paths)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    fun renderUI(time: Double) {

        if (!enableUI) {
            return
        }

        val sortedLayers = layerWatchers.map { it() }.sortedBy { it.zIndex }
        val drawer = program.drawer


        val timescale = (RenderTarget.active.width - 160) / (demo.duration * demo.timescale)

        for ((layerIndex, layer) in sortedLayers.withIndex()) {
            drawer.fill = ColorRGBa.BLACK
            drawer.text(layer.sourceFile.nameWithoutExtension, 11.0, layerIndex * 24.0 + 12.0 + 25.0)
            drawer.fill = if (layerIndex % 2 == 0) ColorRGBa.WHITE else ColorRGBa.WHITE.shade(0.85)
            drawer.text(layer.sourceFile.nameWithoutExtension, 10.0, layerIndex * 24.0 + 12.0 + 24.0)
        }

        drawer.translate(150.0, 24.0)
        for ((layerIndex, layer) in sortedLayers.withIndex()) {

            for (obj in layer.objects) {
                drawer.fill = if (layerIndex % 2 == 0) ColorRGBa.GRAY else ColorRGBa.GRAY.shade(0.5)

                if (obj.time <= time && (obj.time + obj.duration) > time) {
                    drawer.fill = ColorRGBa.WHITE
                }

                drawer.rectangle(obj.time * timescale, layerIndex * 24.0, obj.duration * timescale, 16.0)
            }
        }


        run {
            drawer.stroke = ColorRGBa.GREEN
            val x = time * timescale
            drawer.lineSegment(x, 0.0, x, (sortedLayers.size + 1) * 24.0)
            drawer.fill = ColorRGBa.YELLOW
            val lx = x.coerceAtMost(RenderTarget.active.width - 128.0 - 150.0)
            drawer.text(String.format("%.3f", time), lx, -12.0)
            drawer.fill = ColorRGBa.RED
            drawer.text(String.format("%.3fs", time / demo.timescale), lx + 64.0, -12.0)
        }
        run {
            drawer.stroke = ColorRGBa.BLUE
            val x = cuePoint * timescale
            drawer.lineSegment(x, 0.0, x, (sortedLayers.size + 1) * 24.0)
            drawer.fill = ColorRGBa.YELLOW
            val lx = x.coerceAtMost(RenderTarget.active.width - 128.0 - 150.0)
            drawer.text(String.format("%.3f", cuePoint), lx, -12.0 + (sortedLayers.size + 2) * 24.0)
            drawer.fill = ColorRGBa.RED
            drawer.text(
                String.format("%.3fs", cuePoint / demo.timescale),
                lx + 64.0,
                -12.0 + (sortedLayers.size + 2) * 24.0
            )
        }
    }

    fun renderLayers(time: Double) {
        val drawer = program.drawer
        val layers = layerWatchers.map { it() }
        val sortedLayers = layers.sortedBy { it.zIndex }

        for (layer in sortedLayers) {
            drawer.isolated {
                layer.camera.animation(time)
                val ca = layer.camera.animation
                val aspectRatio = RenderTarget.active.let { it.width.toDouble() / it.height }
                if (layer.camera.type == Layer.Camera.CameraType.ortho) {
                    drawer.ortho(
                        -ca.magnitude * aspectRatio / 2.0,
                        ca.magnitude * aspectRatio / 2.0,
                        -ca.magnitude / 2.0,
                        ca.magnitude / 2.0,
                        ca.orthoNear,
                        ca.orthoFar
                    )
                } else if (layer.camera.type == Layer.Camera.CameraType.perspective) {
                    drawer.perspective(ca.fov, aspectRatio, ca.perspectiveNear, ca.perspectiveFar)
                }

                drawer.translate(640.0, 360.0, TransformTarget.VIEW)
                drawer.view *= ca.transform
                drawer.translate(-640.0, -360.0, TransformTarget.VIEW)

                val objectGroups = layer.objects
                    .filter { time >= it.time && time < (it.time + it.duration) }
                    .sortedBy { it.`z-index` }
                    .groupBy { it.type }

                for ((type, objects) in objectGroups) {
                    if (type == Layer.Object.ObjectType.image) {
                        for (obj in objects) {
                            obj.animation(time - obj.time)
                            val a = obj.animation
                            val asset = obj.assets[a.assetIndex.roundToInt().coerceIn(0, obj.assets.size - 1)]
                            val image = images[asset] ?: error("no image")
                            val processed = processedImages[asset] ?: error("no image")
                            drawer.isolated {
                                drawer.translate(640.0, 360.0)
                                drawer.model *= a.transform

                                drawer.scale(1.0, -1.0, 1.0)
                                drawer.drawStyle.colorMatrix = tint(a.imageTint)

                                val sr = image.bounds.sub(a.imageLeft, a.imageTop, a.imageRight, a.imageBottom)
                                val tr = sr.moved(Vector2(-image.width / 2.0, -image.height / 2.0))

                                if (a.imageDither >= 1.0) {
                                    dither.levels = 1
                                    dither.apply(image, processed)
                                    drawer.image(processed, sr, tr)
                                } else {
                                    drawer.image(image, sr, tr)
                                }
                            }
                        }
                    }

                    if (type == Layer.Object.ObjectType.svg || type == Layer.Object.ObjectType.`svg-3d`) {
                        for (obj in objects) {
                            val a = obj.animation
                            a(time - obj.time)
                            val assetIndex = a.assetIndex.roundToInt().coerceIn(0, obj.assets.size - 1)
                            val asset = obj.assets[assetIndex]

                            val shapeCount = when (obj.type) {
                                Layer.Object.ObjectType.svg -> compositionShapes[asset].orEmpty().size
                                Layer.Object.ObjectType.`svg-3d` -> compositionDraws3D[asset].orEmpty().size
                                else -> error("unreachable")
                            }

                            val duration = a.duration
                            val objectTime = time - obj.time
                            val unitTime = objectTime / duration

                            val staggerOrder = when (obj.stagger.order) {
                                Layer.Object.StaggerOrder.`contour-index` -> List(shapeCount) { it }
                                Layer.Object.StaggerOrder.`reverse-contour-index` -> List(shapeCount) { it }.reversed()
                                Layer.Object.StaggerOrder.random -> List(shapeCount) { it }.shuffled(Random(obj.stagger.seed))
                            }

                            val stagger: (Int) -> Double = when (obj.stagger.mode) {
                                Layer.Object.StaggerMode.`in-out` -> { shapeIndex ->
                                    val staggerIndex = staggerOrder[shapeIndex]
                                    val staggerStart =
                                        (staggerIndex * 1.0) / (shapeCount + obj.stagger.window)
                                    val staggerEnd =
                                        (staggerIndex + 1.0 + obj.stagger.window) / (shapeCount + obj.stagger.window)

                                    unitTime.map(staggerStart, staggerEnd, 0.0, duration, clamp = true)
                                }
                                else -> { shapeIndex -> objectTime }
                            }

                            fun Layer.Object.fill(objectFill: ColorRGBa?) = when (obj.attributes.fill) {
                                Layer.Object.AttributeSource.user -> animation.fill
                                Layer.Object.AttributeSource.asset -> objectFill
                                    ?: ColorRGBa.TRANSPARENT
                                Layer.Object.AttributeSource.modulate -> animation.fill * (objectFill
                                    ?: ColorRGBa.TRANSPARENT)
                            }

                            fun Layer.Object.stroke(objectStroke: ColorRGBa?) = when (obj.attributes.stroke) {
                                Layer.Object.AttributeSource.user -> animation.stroke
                                Layer.Object.AttributeSource.asset -> objectStroke
                                    ?: ColorRGBa.TRANSPARENT
                                Layer.Object.AttributeSource.modulate -> animation.fill * (objectStroke
                                    ?: ColorRGBa.TRANSPARENT)
                            }

                            fun Layer.Object.strokeWeight(objectStrokeWeight: Double?) =
                                when (obj.attributes.`stroke-weight`) {
                                    Layer.Object.AttributeSource.user -> animation.strokeWeight
                                    Layer.Object.AttributeSource.asset ->
                                        objectStrokeWeight ?: 0.0
                                    Layer.Object.AttributeSource.modulate -> animation.strokeWeight * (objectStrokeWeight
                                        ?: 1.0)
                                }

                            when (obj.type) {
                                Layer.Object.ObjectType.svg -> {
                                    for ((shapeIndex, shape) in compositionShapes[asset].orEmpty().withIndex()) {
                                        drawer.isolated {
                                            a(obj.stepping.stepTime(obj.animation, stagger(shapeIndex)))
                                            drawer.translate(640.0, 360.0)
                                            drawer.model *= a.transform
                                            drawer.scale(1.0, -1.0, 1.0)
                                            drawer.translate(-640.0, -360.0)
                                            drawer.fill = obj.fill(shape.effectiveFill)
                                            drawer.strokeWeight = 0.0
                                            drawer.stroke = null
                                            drawer.shape(shape.shape)
                                            drawer.fill = null
                                            for (contour in shape.shape.contours) {
                                                val drawContour = if (a.c0 == 0.0 && a.c1 == 1.0) contour else {
                                                    contour.sub(a.c0, a.c1)
                                                }
                                                drawer.stroke = obj.stroke(shape.effectiveStroke)
                                                drawer.strokeWeight = obj.strokeWeight(shape.effectiveStrokeWeight)
                                                drawer.contour(drawContour)
                                            }
                                        }
                                    }
                                }
                                Layer.Object.ObjectType.`svg-3d` -> {
                                    val objectDraws = compositionDraws3D[asset].orEmpty()
                                    for (objectDraw in objectDraws) {
                                        a(obj.stepping.stepTime(obj.animation, stagger(objectDraw.shapeIndex)))
                                        drawer.isolated {
                                            drawer.translate(640.0, 360.0)
                                            drawer.model *= a.transform
                                            drawer.scale(1.0, -1.0, 1.0)
                                            drawer.translate(-640.0, -360.0)
                                            if (objectDraw.triangulation != null) {
                                                drawer.stroke = null
                                                drawer.fill = obj.fill(objectDraw.fill)
                                                drawer.vertexBuffer(objectDraw.triangulation, DrawPrimitive.TRIANGLES)
                                            }
                                            for (objectPath in objectDraw.paths) {
                                                val drawPath = if (a.c0 == 0.0 && a.c1 == 1.0) objectPath.path3D else {
                                                    objectPath.path3D.sub(a.c0, a.c1)
                                                }
                                                drawer.stroke = obj.stroke(objectPath.stroke)
                                                drawer.strokeWeight = obj.strokeWeight(objectPath.strokeWeight)
                                                drawer.fill = null
                                                drawer.path(drawPath)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private operator fun ColorRGBa.times(other: ColorRGBa): ColorRGBa {
    return ColorRGBa(r * other.r, g * other.g, b * other.b, a * other.a)
}


