package org.example

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.random.Random
import kotlin.random.nextInt

data class Blob(val arr: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Blob

        return arr.contentEquals(other.arr)
    }

    override fun hashCode(): Int {
        return arr.contentHashCode()
    }
}

data class BlobPattern(
    val north: HashSet<Blob>,
    val east: HashSet<Blob>,
    val south: HashSet<Blob>,
    val west: HashSet<Blob>
)

data class Superposition(val positions: ArrayList<Blob>) {

    var collapsed = positions.size == 1

    fun collapseTo(blob: Blob) {
        positions.clear()
        positions.add(blob)
    }

    fun collapseRandom() {
        positions.shuffle()
        collapseTo(positions[0])
    }
}

class WaveformCollapse(
    private val baseImage: BufferedImage,
    private val gridResolution: Int,
    private val gridDimensions: Dimension
) {

    private var baseImageDimensions: Dimension = Dimension(baseImage.getWidth(null), baseImage.getHeight(null))

    private val uniqueBlobs = ArrayList<Blob>()
    private val patterns = HashMap<Blob, BlobPattern>()

    private var grid: ArrayList<ArrayList<Superposition>>
    var image: BufferedImage

    init {
        if (baseImageDimensions.width % gridResolution != 0) {
            throw IllegalStateException("Width " + baseImageDimensions.width + " is not a multiple of " + gridResolution)
        }
        if (baseImageDimensions.height % gridResolution != 0) {
            throw IllegalStateException("Height " + baseImageDimensions.height + " is not a multiple of " + gridResolution)
        }

        findPatterns(baseImage)
        grid = initialiseGrid(gridDimensions)
        val random = Random.Default
        val rx = random.nextInt(0..gridDimensions.width)
        val ry = random.nextInt(0..gridDimensions.height)
        collapseAllStartingAt(rx, ry)

        image = gridToImage()
    }


    private fun initialiseGrid(dim: Dimension): ArrayList<ArrayList<Superposition>> {
        val columns = ArrayList<ArrayList<Superposition>>()

        for (x in 0..dim.width) {
            val row = ArrayList<Superposition>()
            columns.add(row)

            for (y in 0..dim.height) {
                val positions = ArrayList<Blob>()
                positions.addAll(uniqueBlobs)
                val superposition = Superposition(positions)
                row.add(superposition)
            }
        }

        return columns
    }

    private fun collapseAllStartingAt(x: Int, y: Int) {
        if (grid[x][y].positions.size < 1) {
            return
        }

        grid[x][y].collapseRandom()

        val dirty = HashSet<Pair<Int, Int>>();

        var collapsed = 1
        var r = 1
        var forceRandomCollapse = false
        while (collapsed < gridDimensions.width * gridDimensions.height) {

            var collapsedThisRound = 0
            var dx = x - r
            var dy = y - r

            while (dx++ < x + r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }
            while (dy++ < y + r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }
            while (dx-- > x - r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }
            while (dy-- > y - r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }

            // If none have collapsed this round and the force flag has not been reset by a successful force collapse,
            // all the superpositions have collapsed, and we can increase our radius.
            //
            // If the force flag was not set, then we should raise it to see if any superpositions have not yet
            // collapsed.
            if (collapsedThisRound == 0 && forceRandomCollapse) {
                r++
            } else if (collapsedThisRound == 0) {
                forceRandomCollapse = true
            }

            collapsed += collapsedThisRound
        }
    }

    private fun getSuperposition(x: Int, y: Int): Superposition? {
        if (x < 0 || y < 0 || x > gridDimensions.width || y > gridDimensions.height) {
            return null
        }

        return grid[x][y]
    }

    private fun collapseAt(x: Int, y: Int, forceRandomCollapse: Boolean): Boolean {
        val blob = getSuperposition(x, y) ?: return false
        if (blob.positions.size <= 1) return false

        if (forceRandomCollapse) {
            blob.collapseRandom()
            return true
        }

        val blobNorth = getSuperposition(x, y - 1)
        val blobEast = getSuperposition(x + 1, y)
        val blobSouth = getSuperposition(x, y + 1)
        val blobWest = getSuperposition(x - 1, y)

        val toRemove = HashSet<Blob>()
        for (position in blob.positions) {
            val pattern = patterns[position] ?: continue

            // Check if there's at least one valid north neighbor
            val validNorth = blobNorth == null || pattern.north.any { blobNorth.positions.contains(it) }
            val validEast = blobEast == null || pattern.east.any { blobEast.positions.contains(it) }
            val validSouth = blobSouth == null || pattern.south.any { blobSouth.positions.contains(it) }
            val validWest = blobWest == null || pattern.west.any { blobWest.positions.contains(it) }

            if (!(validNorth && validEast && validSouth && validWest)) {
                toRemove.add(position)
            }
        }

        // Remove blocked positions.
        // If no positions would remain, we also randomly fix the discontinuity
        blob.positions.removeAll(toRemove)
        if (blob.positions.size < 1) {
            blob.positions.add(toRemove.shuffled()[0])
        }

        return true
    }

    fun prepareNextGrid() {

    }

    private fun gridToImage(): BufferedImage {
        val outputWidth = gridDimensions.width * gridResolution
        val outputHeight = gridDimensions.height * gridResolution

        val output = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = output.graphics

        for (x in 0..gridDimensions.width) {
            for (y in 0..gridDimensions.height) {

                val superposition = getSuperposition(x, y) ?: continue

                if (superposition.positions.isNotEmpty()) {
                    val blob = superposition.positions[0]
                    val pixels = blob.arr

                    val tile = BufferedImage(gridResolution, gridResolution, BufferedImage.TYPE_INT_RGB)
                    tile.setRGB(0, 0, gridResolution, gridResolution, pixels, 0, gridResolution)

                    graphics.drawImage(tile, x * gridResolution, y * gridResolution, null)
                }
            }
        }

        graphics.dispose()
        return output
    }

    private fun rotateImage(image: BufferedImage, degrees: Int): BufferedImage {

        if (degrees == 0) {
            return image
        }

        val w = image.width
        val h = image.height
        val rotated = when (degrees) {
            90, 270 -> BufferedImage(h, w, image.type)
            else -> BufferedImage(w, h, image.type)
        }

        val g = rotated.createGraphics()
        when (degrees) {
            90 -> {
                g.translate(h, 0)
                g.rotate(Math.toRadians(90.0))
            }

            180 -> {
                g.translate(w, h)
                g.rotate(Math.toRadians(180.0))
            }

            270 -> {
                g.translate(0, w)
                g.rotate(Math.toRadians(270.0))
            }
        }

        g.drawImage(image, 0, 0, null)
        g.dispose()
        return rotated
    }

    private fun findPatterns(image: BufferedImage) {
        for (rotation in listOf(0, 90, 180, 270)) {
            val rotated = rotateImage(image, rotation)

            val gridWidth = image.width / gridResolution
            val gridHeight = image.height / gridResolution

            for (x in 0 until gridWidth) {
                for (y in 0 until gridHeight) {
                    addBlobPatterns(rotated, x, y)
                }
            }
        }

        saveBlobsToDisk(uniqueBlobs, File(".unique_blobs"))
        for ((index, b) in uniqueBlobs.withIndex()) {
            val p = patterns[b]!!

            saveBlobsToDisk(ArrayList(p.north.toList()), File(".unique_blobs/north_$index"))
            saveBlobsToDisk(ArrayList(p.east.toList()), File(".unique_blobs/east_$index"))
            saveBlobsToDisk(ArrayList(p.south.toList()), File(".unique_blobs/south_$index"))
            saveBlobsToDisk(ArrayList(p.west.toList()), File(".unique_blobs/west_$index"))
        }
    }

    private fun saveBlobsToDisk(blobs: ArrayList<Blob>, outputDir: File) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        for ((index, blob) in blobs.withIndex()) {
            val image = BufferedImage(gridResolution, gridResolution, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, gridResolution, gridResolution, blob.arr, 0, gridResolution)

            val file = File(outputDir, "blob_$index.png")
            ImageIO.write(image, "png", file)
        }
    }

    private fun getBlobFromGriddedImage(image: BufferedImage, gx: Int, gy: Int): Blob? {
        val gridWidth = image.width / gridResolution
        val gridHeight = image.height / gridResolution
        if (gx < 0 || gy < 0 || gx >= gridWidth || gy >= gridHeight) {
            return null
        }

        val arr = IntArray(gridResolution * gridResolution)
        image.getRGB(gx * gridResolution, gy * gridResolution, gridResolution, gridResolution, arr, 0, gridResolution)
        val blob = Blob(arr)

        if (uniqueBlobs.contains(blob)) {
            val index = uniqueBlobs.indexOf(blob)
            return uniqueBlobs[index]
        } else {
            uniqueBlobs.add(blob)
            return blob
        }
    }

    private fun addBlobPatterns(image: BufferedImage, x: Int, y: Int) {
        val blob = getBlobFromGriddedImage(image, x, y) ?: return

        val blobNorth = getBlobFromGriddedImage(image, x, y - 1)
        val blobEast = getBlobFromGriddedImage(image, x + 1, y)
        val blobSouth = getBlobFromGriddedImage(image, x, y + 1)
        val blobWest = getBlobFromGriddedImage(image, x - 1, y)

        val bp: BlobPattern
        if (patterns.contains(blob)) {
            bp = patterns[blob]!!
        } else {
            bp = BlobPattern(HashSet(), HashSet(), HashSet(), HashSet())
            patterns[blob] = bp
        }

        if (blobNorth != null) {
            bp.north.add(blobNorth)
        }
        if (blobEast != null) {
            bp.east.add(blobEast)
        }
        if (blobSouth != null) {
            bp.south.add(blobSouth)
        }
        if (blobWest != null) {
            bp.west.add(blobWest)
        }
    }
}

class ImagePanel(var image: Image) : JPanel() {
    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)

        val squareSize = if (width > height) height else width
        g?.drawImage(image, 0, 0, squareSize, squareSize, null)
    }
}

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val frame = JFrame()

    val imgFile = File("base.png")
    val img = ImageIO.read(imgFile)

    val collapse = WaveformCollapse(img, 3, Dimension(6, 6))
    val panel = ImagePanel(collapse.image)
    frame.add(panel)

    var image: Image = collapse.image

    val timer = Timer(2000) {
        val collapse2 = WaveformCollapse(img, 3, Dimension(6, 6))
        panel.image = collapse2.image
        frame.repaint()
    }
    timer.isRepeats = true
    timer.start()

    ImageIO.write(collapse.image, "PNG", File("test.png"))

    frame.size = Dimension(512, 512)
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    frame.isVisible = true
}