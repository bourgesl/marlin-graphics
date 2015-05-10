/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package test;

import org.marlin.graphics.BlendComposite;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.marlin.graphics.MarlinGraphics2D;
import org.marlin.pisces.PiscesRenderingEngine;
import org.marlin.pisces.stats.StatLong;
import sun.java2d.pipe.RenderingEngine;

/**
 * Simple Line rendering test using GeneralPath to enable Pisces / marlin / ductus renderers
 */
public class GLineTests {

    private final static String FILE_NAME = "LinesTest-gamma-norm-subpix_lg_";
    private final static boolean useColor = true;
    private final static Color COL_1 = (useColor) ? Color.blue : Color.white;
    private final static Color COL_2 = (useColor) ? Color.yellow : Color.black;
//    private final static Color COL_3 = (useColor) ? Color.green : Color.white;
//    private final static Color COL_1 = Color.white;
//    private final static Color COL_2 = Color.black;
    private final static Color COL_3 = (useColor) ? Color.green : Color.white;
    //new Color(192, 255, 192)

    private final static boolean useCustomComposite = false;
    private final static boolean drawThinLine = true;

    public static void main(String[] args) {
        final int N = 100;
        final boolean premultiplied = true;

        final int size = 600;
        final int width = size + 100;
        final int height = size;

        System.out.println("Testing renderer = " + RenderingEngine.getInstance().getClass().getName());

        System.out.println("LineTests: size= (" + width + " x " + height + ") - premultiplied: " + premultiplied);

        final BufferedImage image
                            = new BufferedImage(width, height,
                        (premultiplied) ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_ARGB);

        final MarlinGraphics2D g2d = new MarlinGraphics2D(image);

        g2d.setClip(0, 0, width, height);

        StatLong stats = new StatLong("Lines");

        for (int i = 0; i < N; i++) {
            g2d.setBackground(COL_1);
            g2d.clearRect(0, 0, width, height);

            final long start = System.nanoTime();

            paint(g2d, width, height);

            final long time = System.nanoTime() - start;
            if (i > 3) {
                // skip first iterations:
                stats.add(time / 1000);
            }

            System.out.println("paint: duration= " + (1e-6 * time) + " ms.");
        }
        System.out.println("paint: stats µs: " + stats.toString());
        
        /*
        premultiplied = false:
            paint: duration= 35.923755 ms.
            paint: stats µs: Lines[96] sum: 3476752 avg: 36216.166 [35915 | 37200]
        
        premultiplied = true:
            paint: duration= 29.032576 ms.
            paint: stats µs: Lines[96] sum: 2787396 avg: 29035.375 [28694 | 30081]
        */

        try {
            final File file = new File(FILE_NAME + PiscesRenderingEngine.getSubPixel_Log2_X()
                    + "x" + PiscesRenderingEngine.getSubPixel_Log2_Y() + BlendComposite.getBlendingMode()
                    + (premultiplied ? "_pre" : "_nopre")
                    + ".png");

            System.out.println("Writing file: " + file.getAbsolutePath());;
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

    private static void paint(final Graphics2D g2d, final double width, final double height) {

        final double size = Math.min(width, height);

        // Use BlendComposite.BlendingMode.SRC_OVER to perform gamma correction (2.2)
        Composite c = (useCustomComposite)
                ? BlendComposite.getInstance(BlendComposite.BlendingMode.SRC_OVER)
                : AlphaComposite.SrcOver;

        g2d.setComposite(c);

        g2d.setColor(Color.RED);
        final double radius = 0.25 * Math.min(width, height);
        g2d.fillOval((int) (0.5 * width - radius), (int) (0.5 * height - radius),
                (int) (2.0 * radius), (int) (2.0 * radius));

        double thinStroke = 1.5;
        double lineStroke = 2.5;

        final Path2D.Float path = new Path2D.Float();

        for (double angle = 1d / 5d; angle <= 90d; angle += 1d) {
            double angRad = Math.toRadians(angle);

            double cos = Math.cos(angRad);
            double sin = Math.sin(angRad);

            // same algo as agg:
            g2d.setColor(COL_2);
            drawLine(path, 5d * cos, 5d * sin, size * cos, size * sin, lineStroke);
            g2d.fill(path);

            if (drawThinLine) {
                g2d.setColor(COL_3);
                drawLine(path, 5d * cos, 5d * sin, size * cos, size * sin, thinStroke);
                g2d.fill(path);
            }
        }

        final double rectW = Math.abs(width - height);
        if (rectW > 0.0) {
            final int w = (int) (rectW / 2.);
            final double step = 0.01;
            final double yStep = step * height;
            double alpha = 0.0;

            // BlendingMode.SRC_OVER
            for (double y = 0; y < height; y += yStep, alpha += step) {
                g2d.setColor(new Color(COL_2.getRed(), COL_2.getGreen(), COL_2.getBlue(), (int) (255 * alpha)));
                g2d.fillRect((int) height, (int) y, w, (int) yStep);
            }

            c = AlphaComposite.SrcOver;
            g2d.setComposite(c);
            alpha = 0.0;

            for (double y = 0; y < height; y += yStep, alpha += step) {
                g2d.setColor(new Color(COL_2.getRed(), COL_2.getGreen(), COL_2.getBlue(), (int) (255 * alpha)));
                g2d.fillRect((int) height + w, (int) y, w, (int) yStep);
            }
        }
    }

    private static void drawLine(final Path2D.Float path,
                                 double x1, double y1,
                                 double x2, double y2,
                                 double width) {

        double dx = x2 - x1;
        double dy = y2 - y1;
        double d = Math.sqrt(dx * dx + dy * dy);

        dx = width * (y2 - y1) / d;
        dy = width * (x2 - x1) / d;

        path.reset();

        path.moveTo(x1 - dx, y1 + dy);
        path.lineTo(x2 - dx, y2 + dy);
        path.lineTo(x2 + dx, y2 - dy);
        path.lineTo(x1 + dx, y1 - dy);
    }
}
