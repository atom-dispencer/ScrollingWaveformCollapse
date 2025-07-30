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
};

data class BlobPattern(val north: HashSet<Blob>, val east: HashSet<Blob>, val south: HashSet<Blob>, val west: HashSet<Blob>);

data class Superposition(val positions: ArrayList<Blob>) {

    var collapsed = positions.size == 1;

    fun collapseTo(blob: Blob) {
        positions.clear()
        positions.add(blob)
    }

    fun collapseRandom() {
        positions.shuffle()
        collapseTo(positions[0])
    }
}

class WaveformCollapse(private val baseImage: BufferedImage, private val gridResolution: Int, private val gridDimensions: Dimension) {

    private var baseImageDimensions: Dimension = Dimension(baseImage.getWidth(null), baseImage.getHeight(null))

    private val uniqueBlobs = ArrayList<Blob>();
    private val patterns = HashMap<Blob, BlobPattern>();

    private var grid: ArrayList<ArrayList<Superposition>>
    var image: BufferedImage

    init {
        if (baseImageDimensions.width % gridResolution != 0)
        {
            throw IllegalStateException("Width " + baseImageDimensions.width + " is not a multiple of " + gridResolution);
        }
        if (baseImageDimensions.height % gridResolution != 0)
        {
            throw IllegalStateException("Height " + baseImageDimensions.height + " is not a multiple of " + gridResolution);
        }

        findPatterns(baseImage);
        grid = initialiseGrid(gridDimensions);
        val random = Random.Default;
        val rx = random.nextInt(0..gridDimensions.width)
        val ry = random.nextInt(0..gridDimensions.height)
        collapseAllStartingAt(rx, ry)

        image = gridToImage();
    }


    private fun initialiseGrid(dim: Dimension): ArrayList<ArrayList<Superposition>> {
        val columns = ArrayList<ArrayList<Superposition>>();

        for (x in 0..dim.width) {
            val row = ArrayList<Superposition>()
            columns.add(row)

            for (y in 0..dim.height) {
                val positions = ArrayList<Blob>();
                positions.addAll(uniqueBlobs);
                val superposition = Superposition(positions)
                row.add(superposition)
            }
        }

        return columns;
    }

    private fun collapseAllStartingAt(x: Int, y: Int) {
        if (grid[x][y].positions.size < 1) {
            return;
        }

        grid[x][y].collapseRandom();

        var collapsed = 0
        var r = 1
        var forceRandomCollapse = false
        while (collapsed < gridDimensions.width * gridDimensions.height) {

            var collapsedThisRound = 0
            var dx = x - r
            var dy = y - r

            while(dx++ < x + r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }
            while(dy++ < y + r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }
            while(dx-- > x - r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }
            while(dy-- > y - r) {
                if (collapseAt(dx, dy, forceRandomCollapse)) {
                    collapsedThisRound++
                    forceRandomCollapse = false
                }
            }

            if (collapsedThisRound == 0) {
                forceRandomCollapse = true
            }

            collapsed += collapsedThisRound
        }
    }

    private fun getSuperposition(x: Int, y: Int): Superposition? {
        if (x < 0 || y < 0 || x > gridDimensions.width || y > gridDimensions.height) {
            return null;
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

        val blobNorth = getSuperposition(x, y-1);
        val blobEast = getSuperposition(x+1, y);
        val blobSouth = getSuperposition(x, y+1);
        val blobWest = getSuperposition(x-1, y);

        val blockedPositions = HashSet<Blob>()

        for (position in blob.positions) {
            var blocked = false
            val pattern: BlobPattern = patterns[position]!!

            // North
            for(nblob in pattern.north) {
                if(blobNorth != null && blobNorth.positions.contains(nblob)) {
                    continue
                }
                blocked = true;
                blockedPositions.add(nblob)
                continue;
            }
            if (blocked) continue

            // East
            for(nblob in pattern.east) {
                if(blobEast != null && blobEast.positions.contains(nblob)) {
                    continue
                }
                blocked = true;
                blockedPositions.add(nblob)
                continue;
            }
            if (blocked) continue

            // South
            for(nblob in pattern.south) {
                if(blobSouth != null && blobSouth.positions.contains(nblob)) {
                    continue
                }
                blocked = true;
                blockedPositions.add(nblob)
                continue;
            }
            if (blocked) continue

            // West
            for(nblob in pattern.west) {
                if(blobWest != null && blobWest.positions.contains(nblob)) {
                    continue
                }
                blocked = true;
                blockedPositions.add(nblob)
                continue;
            }
            if (blocked) continue
        }

        // Remove blocked positions.
        // If no positions would remain, we also randomly fix the discontinuity
        blob.positions.removeAll(blockedPositions);
        if (blob.positions.size < 1) {
            blob.positions.add(blockedPositions.shuffled()[0])
        }

        return true;
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

    

    private fun findPatterns(baseImage: BufferedImage) {
        for (baseX in 0..baseImageDimensions.width)
        {
            for (baseY in 0..baseImageDimensions.height)
            {
                addBlobPatterns(baseX, baseY);
            }
        }
    }

    private fun getBlobFromImage(x: Int, y: Int): Blob? {
        if (x < 0 || y < 0 || x > baseImageDimensions.width - gridResolution || y > baseImageDimensions.height - gridResolution) {
            return null;
        }

        val arr = IntArray(gridResolution * gridResolution);
        baseImage.getRGB(x, y, gridResolution, gridResolution, arr, 0, gridResolution);
        val blob = Blob(arr);

        if (uniqueBlobs.contains(blob)) {
            val index = uniqueBlobs.indexOf(blob);
            return uniqueBlobs[index];
        } else {
            uniqueBlobs.add(blob);
            return blob;
        }
    }

    private fun addBlobPatterns(x: Int, y: Int) {
        val blob = getBlobFromImage(x, y) ?: return;

        val blobNorth = getBlobFromImage(x, y-1);
        val blobEast = getBlobFromImage(x+1, y);
        val blobSouth = getBlobFromImage(x, y+1);
        val blobWest = getBlobFromImage(x-1, y);

        val bp: BlobPattern
        if (patterns.contains(blob)) {
             bp = patterns[blob]!!;
        } else {
            bp = BlobPattern(HashSet(), HashSet(), HashSet(), HashSet());
            patterns[blob] = bp;
        }

        if (patterns.contains(blob)) {

            if (blobNorth != null) {
                bp.north.add(blobNorth);
            }
            if (blobEast != null) {
                bp.north.add(blobEast);
            }
            if (blobSouth != null) {
                bp.north.add(blobSouth);
            }
            if (blobWest != null) {
                bp.north.add(blobWest);
            }
        }
    }
}

class ImagePanel(var image: Image): JPanel() {
    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g);

        val squareSize = if(width > height) height else width
        g?.drawImage(image, 0, 0, squareSize, squareSize, null);
    }
}

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val frame = JFrame();

    val imgFile = File("base.png");
    val img = ImageIO.read(imgFile);

    val collapse = WaveformCollapse(img, 3, Dimension(6,6));
    val panel = ImagePanel(collapse.image);
    frame.add(panel);

    var image: Image = collapse.image;

    val timer = Timer(2000) {
        val collapse2 = WaveformCollapse(img, 3, Dimension(6,6));
        panel.image = collapse2.image;
        frame.repaint()
    };
    timer.isRepeats = true;
    timer.start()

    ImageIO.write(collapse.image, "PNG", File("test.png"))

    frame.size = Dimension(512, 512);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.isVisible = true;
}