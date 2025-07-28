package org.example

import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JPanel

class ImagePanel(var image: Image): JPanel() {

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g);
        val squareSize = if(width > height) height else width;
        g?.drawImage(image, 0, 0, squareSize, squareSize, null);
    }
}

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val frame = JFrame();

    val imgFile = File("test.png");
    val img = ImageIO.read(imgFile);

    val panel = ImagePanel(img);
    frame.add(panel);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.isVisible = true;
}