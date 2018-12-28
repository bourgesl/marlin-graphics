/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.marlin.geom.Path2D;
import org.marlin.graphics.MarlinGraphics2D;

/**
 * Simple wrapper on MarlinGraphics tests
 */
public class RunJUnitTest {

    @Test
    public void testAPI() {

        final int width = 510;
        final int height = 210;

        System.out.println("RunJUnitTest: size= (" + width + " x " + height + ")");

        final BufferedImage image
                            = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);

        final MarlinGraphics2D g2d = new MarlinGraphics2D(image);

        g2d.setClip(0, 0, width, height);

        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, width, height);

        g2d.setStroke(new BasicStroke(4f));

        // draw / fill shape
        final Path2D.Double path = new Path2D.Double();
        path.moveTo(10, 10);
        path.lineTo(10, 50);
        path.quadTo(20, 80, 90, 90);
        path.curveTo(60, 80, 30, 20, 10, 10);
        path.closePath();

        // fill(Shape s)
        g2d.setColor(Color.PINK);
        g2d.fill(path);

        // draw(Shape s)
        g2d.setColor(Color.BLACK);
        g2d.draw(path);

        // drawLine(int x1, int y1, int x2, int y2)
        g2d.setColor(Color.BLACK);
        g2d.drawLine(100, 10, 160, 80);

        // fillOval(int x, int y, int width, int height)
        g2d.setColor(Color.PINK);
        g2d.fillOval(200, 10, 60, 80);

        // drawOval(int x, int y, int width, int height)
        g2d.setColor(Color.BLACK);
        g2d.drawOval(200, 10, 60, 80);

        // fillArc(int x, int y, int width, int height, int startAngle, int arcAngle)
        g2d.setColor(Color.PINK);
        g2d.fillArc(300, 10, 60, 80, 30, 120);

        // drawArc(int x, int y, int width, int height, int startAngle, int arcAngle)
        g2d.setColor(Color.BLACK);
        g2d.drawArc(300, 10, 60, 80, 30, 120);

        int[] xp = new int[5];
        int[] yp = new int[5];
        xp[0] = 10;
        yp[0] = 100;
        xp[1] = 80;
        yp[1] = 140;
        xp[2] = 70;
        yp[2] = 180;
        xp[3] = 30;
        yp[3] = 110;
        xp[4] = 20;
        yp[4] = 150;

        // fillPolygon(int[] xPoints, int[] yPoints, int nPoints)
        g2d.setColor(Color.PINK);
        g2d.fillPolygon(xp, yp, xp.length);

        // drawPolygon(int[] xPoints, int[] yPoints, int nPoints)
        g2d.setColor(Color.BLACK);
        g2d.drawPolygon(xp, yp, xp.length);

        // drawPolyline(int[] xPoints, int[] yPoints, int nPoints)
        g2d.setColor(Color.GREEN);
        g2d.drawPolyline(xp, yp, xp.length);

        /*
        Following operations on polyline/polygon are redirected to draw/fill(Shape)
            public void drawPolygon(Polygon p)
            public void fillPolygon(Polygon p)
         */
        // Rectangle tests requires system property '-DMarlinGraphics.redirectRect=true':
        // clearRect(int x, int y, int width, int height)
        g2d.setBackground(Color.PINK);
        g2d.clearRect(100, 100, 100, 100);

        // fillRect(int x, int y, int width, int height)
        g2d.setColor(Color.PINK);
        g2d.fillRect(220, 120, 60, 70);

        // drawRect(int x, int y, int width, int height)
        g2d.setColor(Color.BLACK);
        g2d.drawRect(220, 120, 60, 70);

        // fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
        g2d.setColor(Color.PINK);
        g2d.fillRoundRect(320, 120, 60, 70, 8, 6);

        // drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
        g2d.setColor(Color.BLACK);
        g2d.drawRoundRect(320, 120, 60, 70, 8, 6);

        // fill3DRect(int x, int y, int width, int height, boolean raised)
        g2d.setColor(Color.PINK);
        g2d.fill3DRect(420, 120, 60, 70, true);

        // draw3DRect(int x, int y, int width, int height, boolean raised)
        g2d.setColor(Color.BLACK);
        g2d.draw3DRect(420, 120, 60, 70, true);

        try {
            final File file = new File("test-MarlinGraphics2D-API.png");

            System.out.println("Writing file: " + file.getAbsolutePath());
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

}
