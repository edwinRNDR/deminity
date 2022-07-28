package demo

import bass.Channel
import demo.Layer.Blend.BlendMode
import demo.Layer.Object.Attributes.AttributeSource
import demo.Layer.Object.Clipping.ClipMask
import demo.Layer.Object.Target
import demo.Layer.Object.ObjectType
import mu.KotlinLogging
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.events.listen
import org.openrndr.extra.dnk3.Scene
import org.openrndr.extra.dnk3.SceneNode
import org.openrndr.extra.dnk3.SceneRenderer
import org.openrndr.extra.dnk3.gltf.buildSceneNodes
import org.openrndr.extra.dnk3.gltf.loadGltfFromFile
import org.openrndr.extra.dnk3.renderers.dryRenderer
import org.openrndr.extra.fx.blend.Multiply
import org.openrndr.extra.fx.dither.ADither
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.shape.*
import org.openrndr.svg.loadSVG
import org.openrndr.extra.filewatcher.watch
import org.openrndr.extra.filewatcher.watchFile
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

class LayerRenderer(val program: Program, val demo: Demo, val targetWidth: Int, val targetHeight: Int) {
    var channel = Channel()
    var enableUI = false

    private var cuePoint = 0.0

    private class ObjectDraw3D(
        val shapeIndex: Int,
        val fill: ColorRGBa?,
        val triangulation: VertexBuffer?,
        val paths: List<ObjectPath3D>
    )

    private class ObjectPath3D(val stroke: ColorRGBa?, val strokeWeight: Double?, val path3D: Path3D)

    private class SceneDraw(val renderer: SceneRenderer, val scene: Scene)

    private val layerTarget = renderTarget(targetWidth, targetHeight, multisample = BufferMultisample.SampleCount(8)) {
        colorBuffer()
        depthBuffer()
    }
    private val layerResolved = colorBuffer(targetWidth, targetHeight)

    private val compositionWatchers = mutableMapOf<String, () -> Composition>()
    private val compositionShapes = mutableMapOf<String, List<ShapeNode>>()
    private val compositionDraws3D = mutableMapOf<String, List<ObjectDraw3D>>()
    private val textCurtains = mutableMapOf<String, TextCurtain>()
    private val sceneDraws = mutableMapOf<String, SceneDraw>()

    private val images = mutableMapOf<String, ColorBuffer>()
    private val processedImages = mutableMapOf<String, ColorBuffer>()

    private val dither by lazy { ADither() }

    private val blendModeTarget by lazy {
        renderTarget(
            RenderTarget.active.width,
            RenderTarget.active.height,
            multisample = BufferMultisample.SampleCount(8)
        ) {
            colorBuffer()
            depthBuffer()
        }
    }

    private val blendModeResolved by lazy {
        colorBuffer(RenderTarget.active.width, RenderTarget.active.height)
    }

    private val clipMaskTargets by lazy {
        List(2) {
            renderTarget(
                RenderTarget.active.width,
                RenderTarget.active.height,
                multisample = BufferMultisample.SampleCount(8)
            ) {
                colorBuffer()
                depthBuffer()
            }
        }
    }
    private val clipMaskResolved by lazy { colorBuffer(RenderTarget.active.width, RenderTarget.active.height) }
    private val clipMasks by lazy {
        List(2) {
            colorBuffer(RenderTarget.active.width, RenderTarget.active.height, format = ColorFormat.R)
        }
    }
    private val alphaToRed by lazy { AlphaToRed() }
    private val clipStyle by lazy { ClipStyle() }

    private val multiplyFilter by lazy { Multiply() }

    val billOfMaterials = mutableSetOf<String>()

    val finalTarget by lazy {
        renderTarget(targetWidth, targetHeight) {
            colorBuffer()
        }
    }

    init {
        program.mouse.cursorVisible = false

        listOf(program.mouse.dragged, program.mouse.buttonDown).listen {
            val timescale = (RenderTarget.active.width - 160) / (demo.duration * demo.`time-scale`)
            val time = ((it.position.x - 150.0) / timescale).coerceAtLeast(0.0)
            if (KeyModifier.SHIFT in it.modifiers) {
                cuePoint = time
            }
            channel.setPosition(time / demo.`time-scale`)
        }

        program.keyboard.keyDown.listen {
            when (it.key) {
                KEY_TAB -> {
                    enableUI = !enableUI
                    program.mouse.cursorVisible = enableUI
                }
                KEY_ARROW_DOWN -> {
                    cuePoint = program.seconds * demo.`time-scale`
                }
                KEY_ARROW_UP -> {
                    channel.setPosition(cuePoint / demo.`time-scale`)
                }
            }
        }
    }

    /** This is a hack that enables jumping to cue-point when post.json is changed */
    private val postWatcher = program.watchFile(File(demo.dataBase, "post/post.json")) {
        channel.setPosition(cuePoint / demo.`time-scale`)
    }

    /** Layer watchers are at the core of [LayerRenderer]. All json files in the animations directory are watched.
     * On file change we do the following:
     *  - jump to [cuePoint]
     *  - reload [Layer] from json
     *  - scan [Layer.Object] instances for assets, watch and load assets.
     *
     *  SVG assets are preprocessed: triangulations and Path3Ds are calculated to faster rendering.
     * */
    private val layerWatchers =
        File(demo.dataBase, "animations").listFiles { it -> it.isFile && it.extension == "json" }!!.map {
            Layer.watch(program, demo, it).apply {
                watch { layer ->
                    channel.setPosition(cuePoint / demo.`time-scale`)

                    /* preload images */
                    layer.objects.filter { obj ->
                        obj.type == ObjectType.image
                    }.map { obj ->
                        obj.assets!!
                        for (asset in obj.assets) {
                            val image = images.getOrPut(asset) {
                                val imageFile = File(demo.dataBase, "assets/${asset}")
                                billOfMaterials.add(imageFile.path)
                                loadImage(imageFile)
                            }
                            processedImages.getOrPut(asset) {
                                image.createEquivalent(format = ColorFormat.RGBa, type = ColorType.UINT8)
                            }
                        }
                    }
                    /** preload svg */
                    layer.objects.filter { obj ->
                        obj.type == ObjectType.svg || obj.type == ObjectType.`svg-3d`
                    }.map { obj ->
                        obj.assets!!
                        for (asset in obj.assets) {
                            compositionWatchers.getOrPut(asset) {
                                val svgFile = File(demo.dataBase, "assets/${asset}")
                                billOfMaterials.add(svgFile.path)
                                watchFile(program, svgFile) {
                                    try {
                                        loadSVG(it)
                                    } catch (e : FileNotFoundException) {
                                        logger.error {
                                            "asset ${svgFile.path} not found in layer ${layer.sourceFile}. ${obj}"

                                        }
                                        error("asset ${svgFile.path} not found in layer ${layer.sourceFile}")
                                    }
                                }.apply {
                                    this.watch { composition ->
                                        compositionShapes[asset] = composition.findShapes().map {
                                            it.flatten()
                                        }
                                        var contourIndex = 0
                                        compositionDraws3D[asset] =
                                            composition.findShapes().mapIndexed { shapeIndex, shape ->
                                                val flattened = shape.flatten()
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
                                                    ObjectPath3D(
                                                        shape.effectiveStroke,
                                                        shape.effectiveStrokeWeight,
                                                        path
                                                    )
                                                }
                                                val triangulation = if (shape.shape.topology != ShapeTopology.OPEN) {
                                                    val fixedShape = flattened.shape
                                                    val triangles = fixedShape.triangulation
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
                                                ObjectDraw3D(shapeIndex, shape.effectiveFill, triangulation, paths)
                                            }
                                    }
                                }
                            }
                        }
                    }
                    /** preload gltf */
                    layer.objects.filter { obj ->  obj.type == ObjectType.gltf }.map { obj ->
                        obj.assets!!
                        for (asset in obj.assets) {
                            sceneDraws.getOrPut(asset) {
                                val gltfFile = File(demo.dataBase, "assets/${asset}")
                                if (gltfFile.isFile) {
                                    val gltfScene = loadGltfFromFile(gltfFile)
                                    val scene = Scene(SceneNode())
                                    val sceneData = gltfScene.buildSceneNodes()
                                    scene.root.children.addAll(sceneData.scenes.first())
                                    SceneDraw(dryRenderer(), scene)
                                } else {
                                    error("gltf asset not found ${gltfFile.absolutePath}")
                                }
                            }
                        }
                    }

                    /** preload text-curtain */
                    layer.objects.filter { obj ->  obj.type == ObjectType.`text-curtain` }.map { obj ->
                        obj.assets!!
                        for (asset in obj.assets) {
                            textCurtains.getOrPut(asset) {
                                val txtFile = File(demo.dataBase, "assets/${asset}")
                                if (txtFile.isFile) {
                                    loadTextCurtain(txtFile)
                                } else {
                                    error("text curtain asset not found ${txtFile.absolutePath}")
                                }
                            }
                        }
                    }
                }
            }
        }

    /**
     * Render a simple user-interface for the demo
     */
    fun renderUI(time: Double) {
        if (!enableUI) {
            return
        }

        val sortedLayers = layerWatchers.map { it() }.sortedBy { it.`z-index` }
        val drawer = program.drawer
        val timescale = (RenderTarget.active.width - 160) / (demo.duration * demo.`time-scale`)

        for ((layerIndex, layer) in sortedLayers.withIndex()) {
            drawer.fill = ColorRGBa.BLACK
            drawer.text(layer.sourceFile.nameWithoutExtension, 11.0, layerIndex * 24.0 + 12.0 + 25.0)
            drawer.fill = if (layer.enabled) {
                if (layerIndex % 2 == 0) ColorRGBa.WHITE else ColorRGBa.WHITE.shade(0.85)
            } else {
                ColorRGBa.WHITE.shade(0.2)
            }
            drawer.text(layer.sourceFile.nameWithoutExtension, 10.0, layerIndex * 24.0 + 12.0 + 24.0)
        }
        drawer.translate(150.0, 24.0)
        for ((layerIndex, layer) in sortedLayers.withIndex()) {
            for (obj in layer.objects) {
                obj.time!!

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
            drawer.text(String.format("%.3fs", time / demo.`time-scale`), lx + 64.0, -12.0)
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
                String.format("%.3fs", cuePoint / demo.`time-scale`),
                lx + 64.0,
                -12.0 + (sortedLayers.size + 2) * 24.0
            )
        }
        drawer.image(clipMasks[0], 0.0, (sortedLayers.size + 3) * 24.0, 192.0, 108.0)
        drawer.image(clipMasks[1], 192 + 24.0, (sortedLayers.size + 3) * 24.0, 192.0, 108.0)
    }

    /**
     * Render the layers. Result can be found in [finalTarget]
     */
    fun renderLayers(time: Double) {
        val drawer = program.drawer
        val layers = layerWatchers.map { it() }.filter { it.enabled }
        val sortedLayers = layers.sortedBy { it.`z-index` }

        layerTarget.bind()
        drawer.clear(ColorRGBa.TRANSPARENT)

        clipMaskTargets.forEach {
            it.clearColor(0, ColorRGBa.TRANSPARENT)
            it.clearDepth()
        }
        clipMasks.forEach {
            it.fill(ColorRGBa.TRANSPARENT)
        }

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

                drawer.view *= ca.transform

                val objectGroups = layer.objects
                    .filter { it.time!!; time >= it.time && time < (it.time + it.duration) }
                    .sortedBy { it.`z-index` }
                    .groupBy { it.target }

                val targetOrder = listOf(
                    Target.`clip-a`,
                    Target.`clip-b`,
                    Target.image
                )

                for (target in targetOrder) {
                    val renderObjects = objectGroups[target].orEmpty()
                    if (renderObjects.isNotEmpty()) {
                        when (target) {
                            Target.image -> {
                                if (layer.blend.mode != BlendMode.normal) {
                                    blendModeTarget.clearColor(0, ColorRGBa.TRANSPARENT)
                                    blendModeTarget.clearDepth()
                                    blendModeTarget.bind()
                                }
                            }
                            Target.`clip-a` -> {
                                clipMaskTargets[0].bind()
                            }
                            Target.`clip-b` -> {
                                clipMaskTargets[1].bind()
                            }
                        }
                    }

                    for (obj in renderObjects) {
                        when (obj.clipping.mask) {
                            ClipMask.none -> drawer.shadeStyle = null
                            ClipMask.a -> {
                                clipStyle.clipMask = clipMasks[0]
                                clipStyle.invertMask = false
                                drawer.shadeStyle = clipStyle
                            }
                            ClipMask.`invert-a` -> {
                                clipStyle.clipMask = clipMasks[0]
                                clipStyle.invertMask = true
                                drawer.shadeStyle = clipStyle
                            }
                            ClipMask.b -> {
                                clipStyle.clipMask = clipMasks[1]
                                clipStyle.invertMask = false
                                drawer.shadeStyle = clipStyle
                            }
                            ClipMask.`invert-b` -> {
                                clipStyle.clipMask = clipMasks[1]
                                clipStyle.invertMask = true
                                drawer.shadeStyle = clipStyle
                            }
                        }

                        if (obj.type == ObjectType.image) {
                            obj.time!!
                            obj.assets!!

                            obj.animation(time - obj.time)
                            val a = obj.animation
                            val objectClipBlend = a.objectClipBlend
                            val asset = obj.assets[a.assetIndex.roundToInt().coerceIn(0, obj.assets.size - 1)]
                            val image = images[asset] ?: error("no image")
                            val processed = processedImages[asset] ?: error("no image")
                            drawer.isolated {
                                drawer.model *= a.transform
                                drawer.scale(1.0, -1.0, 1.0)
                                drawer.drawStyle.colorMatrix = tint(a.imageTint)

                                val source = image.bounds.sub(a.imageLeft, a.imageTop, a.imageRight, a.imageBottom)
                                val target = source.moved(Vector2(-image.width / 2.0, -image.height / 2.0))

                                clipStyle.clipBlend = objectClipBlend * a.clipBlend
                                if (a.imageDither >= 1.0) {
                                    dither.levels = 1
                                    dither.apply(image, processed)
                                    drawer.image(processed, source, target)
                                } else {
                                    drawer.image(image, source, target)
                                }
                            }
                        } else if (obj.type == ObjectType.svg || obj.type == ObjectType.`svg-3d`) {
                            val animation = obj.animation
                            obj.time!!; obj.assets!!; obj.staggers!!
                            animation(time - obj.time)
                            val objectClipBlend = animation.objectClipBlend
                            val assetIndex = animation.assetIndex.roundToInt().coerceIn(0, obj.assets.size - 1)
                            val asset = obj.assets[assetIndex]

                            val shapeCount = when (obj.type) {
                                ObjectType.svg -> compositionShapes[asset].orEmpty().size
                                ObjectType.`svg-3d` -> compositionDraws3D[asset].orEmpty().size
                                else -> error("unreachable")
                            }

                            val duration = animation.duration
                            val objectTime = time - obj.time
                            val stagger = obj.staggers.stagger(objectTime)

                            /** [Layer.Object] fil color resolver */
                            fun Layer.Object.fill(objectFill: ColorRGBa?) = when (this.attributes.fill) {
                                null, AttributeSource.user -> animation.fill
                                AttributeSource.asset -> objectFill
                                    ?: ColorRGBa.TRANSPARENT
                                AttributeSource.modulate -> animation.fill * (objectFill
                                    ?: ColorRGBa.TRANSPARENT)
                            }

                            /** [Layer.Object] stroke color resolver */
                            fun Layer.Object.stroke(objectStroke: ColorRGBa?) = when (this.attributes.stroke) {
                                null, AttributeSource.user -> animation.stroke
                                AttributeSource.asset -> objectStroke
                                    ?: ColorRGBa.TRANSPARENT
                                AttributeSource.modulate -> animation.fill * (objectStroke
                                    ?: ColorRGBa.TRANSPARENT)
                            }

                            /** [Layer.Object] stroke weight resolver */
                            fun Layer.Object.strokeWeight(objectStrokeWeight: Double?) =
                                when (this.attributes.`stroke-weight`) {
                                    null, AttributeSource.user -> animation.strokeWeight
                                    AttributeSource.asset ->
                                        objectStrokeWeight ?: 0.0
                                    AttributeSource.modulate -> animation.strokeWeight * (objectStrokeWeight
                                        ?: 1.0)
                                }

                            when (obj.type) {
                                ObjectType.svg -> {
                                    for ((shapeIndex, shape) in compositionShapes[asset].orEmpty().withIndex()) {
                                        drawer.isolated {
                                            animation(
                                                obj.stepping.stepTime(
                                                    obj.animation,
                                                    stagger.stagger(objectTime, shapeCount, shapeIndex)
                                                )
                                            )
                                            clipStyle.clipBlend = objectClipBlend * animation.clipBlend
                                            drawer.model *= animation.transform
                                            drawer.scale(1.0, -1.0, 1.0)
                                            drawer.translate(-640.0, -360.0)
                                            drawer.fill = obj.fill(shape.effectiveFill)
                                            drawer.strokeWeight = 0.0
                                            drawer.stroke = null
                                            drawer.shape(shape.shape)
                                            drawer.fill = null
                                            for (contour in shape.shape.contours) {
                                                val drawContour =
                                                    if (animation.c0 == 0.0 && animation.c1 == 1.0) contour else {
                                                        contour.sub(animation.c0, animation.c1)
                                                    }
                                                drawer.stroke = obj.stroke(shape.effectiveStroke)
                                                drawer.strokeWeight = obj.strokeWeight(shape.effectiveStrokeWeight)
                                                drawer.contour(drawContour)
                                            }
                                        }
                                    }
                                }
                                ObjectType.`svg-3d` -> {
                                    val strokeGain = when (layer.camera.type) {
                                        Layer.Camera.CameraType.ortho -> 1.0
                                        Layer.Camera.CameraType.perspective -> 256.0
                                    }
                                    val objectDraws = compositionDraws3D[asset].orEmpty()
                                    for (objectDraw in objectDraws) {
                                        animation(
                                            obj.stepping.stepTime(
                                                obj.animation,
                                                stagger.stagger(objectTime, shapeCount, objectDraw.shapeIndex)
                                            )
                                        )
                                        clipStyle.clipBlend = objectClipBlend * animation.clipBlend
                                        drawer.isolated {
                                            drawer.model = Matrix44.IDENTITY
                                            drawer.model *= animation.transform
                                            drawer.scale(1.0, -1.0, 1.0)
                                            drawer.translate(-640.0, -360.0, 0.0)
                                            if (objectDraw.triangulation != null) {
                                                drawer.stroke = null
                                                drawer.fill = obj.fill(objectDraw.fill)
                                                drawer.vertexBuffer(objectDraw.triangulation, DrawPrimitive.TRIANGLES)
                                            }
                                            for (objectPath in objectDraw.paths) {
                                                val drawPath =
                                                    if (animation.c0 == 0.0 && animation.c1 == 1.0) objectPath.path3D else {
                                                        objectPath.path3D.sub(animation.c0, animation.c1)
                                                    }
                                                drawer.stroke = obj.stroke(objectPath.stroke)
                                                drawer.strokeWeight =
                                                    obj.strokeWeight(objectPath.strokeWeight) * strokeGain
                                                drawer.fill = null
                                                drawer.path(drawPath)
                                            }
                                        }
                                    }
                                }
                                else -> error("unreachable")
                            }
                        } else if (obj.type == ObjectType.gltf) {
                            obj.time!!; obj.assets!!; obj.staggers!!
                            val animation = obj.animation
                            animation(time - obj.time)
                            val assetIndex = animation.assetIndex.roundToInt().coerceIn(0, obj.assets.size - 1)
                            val asset = obj.assets[assetIndex]
                            val sceneDraw = sceneDraws[asset] ?: error("scene asset missing for $asset")
                            //drawer.model *= obj.animation.transform
                            sceneDraw.scene.root.transform = obj.animation.transform
                            drawer.rotate(Vector3.UNIT_Y, time * 180.0)
                            sceneDraw.renderer.draw(drawer, sceneDraw.scene)
                        } else if (obj.type == ObjectType.`text-curtain`) {
                            obj.time!!; obj.assets!!; obj.staggers!!
                            val animation = obj.animation
                            animation(time - obj.time)
                            val assetIndex = animation.assetIndex.roundToInt().coerceIn(0, obj.assets.size - 1)
                            val asset = obj.assets[assetIndex]
                            val curtain = textCurtains[asset] ?: error("scene asset missing for $asset")
                            drawer.isolated {
                                drawer.fill = animation.fill
                                //drawer.model *= animation.transform
                                drawer.scale(1.0, -1.0, 1.0)
                                drawer.translate(-640.0, -360.0)
                                val text = curtain.prepareText(animation.curtainStart, animation.curtainEnd)
                                for (line in text) {
                                    drawer.text(line)
                                    drawer.translate(0.0, 14.0)
                                }

                            }
                        }
                    }
                    if (renderObjects.isNotEmpty()) {
                        when (target) {
                            Target.image -> {
                                if (layer.blend.mode != BlendMode.normal) {
                                    blendModeTarget.unbind()
                                    blendModeTarget.colorBuffer(0).copyTo(blendModeResolved)
                                    layerTarget.colorBuffer(0).copyTo(layerResolved)
                                    when (layer.blend.mode) {
                                        BlendMode.multiply -> {
                                            multiplyFilter.apply(
                                                arrayOf(layerResolved, blendModeResolved),
                                                layerResolved
                                            )
                                        }
                                        else -> {
                                            error("unreachable")
                                        }
                                    }
                                    layerResolved.copyTo(layerTarget.colorBuffer(0))
                                }
                            }
                            Target.`clip-a` -> {
                                clipMaskTargets[0].unbind()
                                clipMaskTargets[0].colorBuffer(0).copyTo(clipMaskResolved)
                                alphaToRed.apply(clipMaskResolved, clipMasks[0])
                            }
                            Target.`clip-b` -> {
                                clipMaskTargets[1].unbind()
                                clipMaskTargets[1].colorBuffer(0).copyTo(clipMaskResolved)
                                alphaToRed.apply(clipMaskResolved, clipMasks[1])
                            }
                        }
                    }
                }
            }
        }
        layerTarget.unbind()
        layerTarget.colorBuffer(0).copyTo(layerResolved)
        drawer.isolatedWithTarget(finalTarget) {
            ortho(finalTarget)
            clear(ColorRGBa.BLACK)
            image(layerResolved)
        }
    }
}


private operator fun ColorRGBa.times(other: ColorRGBa): ColorRGBa {
    return ColorRGBa(r * other.r, g * other.g, b * other.b, a * other.a)
}