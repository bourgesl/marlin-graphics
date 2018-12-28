/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.marlin.graphics;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import static java.awt.Transparency.OPAQUE;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.security.AccessController;
import java.text.AttributedCharacterIterator;
import java.util.Map;
import org.marlin.geom.Path2D;
import sun.java2d.InvalidPipeException;
import sun.java2d.SunGraphics2D;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.MaskFill;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.AlphaColorPipe;
import sun.java2d.pipe.AlphaPaintPipe;
import sun.java2d.pipe.CompositePipe;
import sun.java2d.pipe.GeneralCompositePipe;
import sun.java2d.pipe.ParallelogramPipe;
import sun.java2d.pipe.PixelToParallelogramConverter;
import sun.java2d.pipe.ShapeDrawPipe;
import sun.java2d.pipe.SpanClipRenderer;
import sun.security.action.GetPropertyAction;

/*
 check AA hint => use marlin or use delegate !
 */
public final class MarlinGraphics2D extends Graphics2D {

    private final static boolean DEBUG = getBoolean("MarlinGraphics.debug", "false");
    /** force using blend composite (gamma correction) */
    private final static boolean FORCE_BLEND_COMPOSITE = getBoolean("MarlinGraphics.blendComposite", "false");

    /** redirect rectangle flag: true means to use Marlin instead of default rendering engine */
    private final static boolean REDIRECT_RECT = getBoolean("MarlinGraphics.redirectRect", "false");

    /* members */
    final SunGraphics2D delegate;
    /** redirect flag: true means to use Marlin instead of default rendering engine */
    private boolean redirect = true;
    /* flag to validate pipeline */
    private boolean validatePipe = true;
    /* shared shape instances */
    private Rectangle rect = null;
    private RoundRectangle2D.Float roundRect = null;
    private Line2D.Float line = null;
    private Ellipse2D.Float ellipse = null;
    private Arc2D.Float arc = null;
    private Path2D.Float path = null;

    public MarlinGraphics2D(final BufferedImage image) {
        // TODO: handle incompatiblity with BlendComposite (gamma correction) 
        final Graphics2D g2d = image.createGraphics();
        if (!(g2d instanceof SunGraphics2D)) {
            g2d.dispose();
            throw new IllegalStateException("BufferedImage.createGraphics() is not SunGraphics2D !");
        }
        this.delegate = (SunGraphics2D) g2d;
        // Set rendering hints:
        setDefaultRenderingHints();
    }
    
    public MarlinGraphics2D(final Graphics2D g2d) {
        // TODO: handle incompatiblity with BlendComposite (gamma correction) 
        if (g2d instanceof MarlinGraphics2D) {
            final MarlinGraphics2D mg2d = (MarlinGraphics2D) g2d;
            this.delegate = (SunGraphics2D)mg2d.delegate.create(); // clone delegate
        } else if (g2d instanceof SunGraphics2D) {
            this.delegate = (SunGraphics2D) g2d.create(); // clone delegate
        } else {
            g2d.dispose();
            throw new IllegalStateException("Incompatible Graphics2D implementation="
                    + g2d.getClass() +"; only SunGraphics2D or MarlinGraphics2D supported !");
        }
        // do not set rendering hints (already done ?)
        updateRedirect();
    }
    
    public void setDefaultRenderingHints() {
        setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DEFAULT);
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public void finalize() {
        delegate.finalize();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    // --- other operations ---
    @Override
    public Graphics create() {
        return new MarlinGraphics2D(this);
    }

    @Override
    public Graphics create(int x, int y, int width, int height) {
        return super.create(x, y, width, height);
    }

    // --- shape operations (handled by Marlin) ---
    @Override
    public void draw(final Shape s) {
        if (DEBUG) {
            log("draw: " + s);
        }
        if (redirect) {
            if (validatePipe) {
                validatePipe(delegate);
            }
            try {
                shapepipe.draw(delegate, s);
                delegate.surfaceData.markDirty();
            } catch (InvalidPipeException e) {
                delegate.draw(s);
            }
        } else {
            delegate.draw(s);
        }
    }

    @Override
    public void fill(final Shape s) {
        if (DEBUG) {
            log("fill: " + s);
        }
        if (redirect) {
            if (validatePipe) {
                validatePipe(delegate);
            }
            try {
                shapepipe.fill(delegate, s);
                delegate.surfaceData.markDirty();
            } catch (InvalidPipeException e) {
                delegate.fill(s);
            }
        } else {
            delegate.fill(s);
        }
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        if (redirect) {
            if (line == null) {
                line = new Line2D.Float();
            }
            line.setLine(x1, y1, x2, y2);
            draw(line);
        } else {
            if (DEBUG) {
                log("drawLine: (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
            }
            delegate.drawLine(x1, y1, x2, y2);
        }
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        if (redirect) {
            if (ellipse == null) {
                ellipse = new Ellipse2D.Float();
            }
            ellipse.setFrame(x, y, width, height);
            draw(ellipse);
        } else {
            if (DEBUG) {
                log("drawOval: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.drawOval(x, y, width, height);
        }
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        if (redirect) {
            if (ellipse == null) {
                ellipse = new Ellipse2D.Float();
            }
            ellipse.setFrame(x, y, width, height);
            fill(ellipse);
        } else {
            if (DEBUG) {
                log("fillOval: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.fillOval(x, y, width, height);
        }
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (redirect) {
            if (arc == null) {
                arc = new Arc2D.Float();
            }
            arc.setArc(x, y, width, height, startAngle, arcAngle, Arc2D.OPEN);
            draw(arc);
        } else {
            if (DEBUG) {
                log("drawArc: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.drawArc(x, y, width, height, startAngle, arcAngle);
        }
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (redirect) {
            if (arc == null) {
                arc = new Arc2D.Float();
            }
            arc.setArc(x, y, width, height, startAngle, arcAngle, Arc2D.PIE);
            fill(arc);
        } else {
            if (DEBUG) {
                log("fillArc: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.fillArc(x, y, width, height, startAngle, arcAngle);
        }
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        if (redirect) {
            draw(createPath(xPoints, yPoints, nPoints, false));
        } else {
            if (DEBUG) {
                log("drawPolyline: (" + nPoints + " points)");
            }
            delegate.drawPolyline(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (redirect) {
            draw(createPath(xPoints, yPoints, nPoints, true));
        } else {
            if (DEBUG) {
                log("drawPolygon: (" + nPoints + " points)");
            }
            delegate.drawPolygon(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (redirect) {
            fill(createPath(xPoints, yPoints, nPoints, true));
        } else {
            if (DEBUG) {
                log("fillPolygon: (" + nPoints + " points)");
            }
            delegate.fillPolygon(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void drawPolygon(Polygon p) {
        draw(p);
    }

    @Override
    public void fillPolygon(Polygon p) {
        fill(p);
    }

    private Path2D.Float createPath(int[] xPoints, int[] yPoints, 
                                      int nPoints, boolean close) {
        if (path == null) {
            path = new Path2D.Float(Path2D.WIND_EVEN_ODD, 
                                    Math.max(1000, nPoints));
        }
        final Path2D.Float p = this.path;
        p.reset();
        p.moveTo(xPoints[0], yPoints[0]);
        
        for (int i = 1; i < nPoints; i++) {
            p.lineTo(xPoints[i], yPoints[i]);
        }
        if (close) {
            p.closePath();
        }
        return p;
    }    

    // --- rectangle operations ---
    @Override
    public void clearRect(int x, int y, int width, int height) {
        if (REDIRECT_RECT && redirect) {
            final Composite c = delegate.getComposite();
            final Paint p = delegate.getPaint();
            setComposite(AlphaComposite.Src);
            setColor(getBackground());
            fillRect(x, y, width, height);
            setPaint(p);
            setComposite(c);
        } else {
            if (DEBUG) {
                log("clearRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.clearRect(x, y, width, height);
        }
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        if (REDIRECT_RECT && redirect) {
            if (rect == null) {
                rect = new Rectangle();
            }
            rect.setBounds(x, y, width, height);
            draw(rect);
        } else {
            if (DEBUG) {
                log("drawRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.drawRect(x, y, width, height);
        }
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        if (REDIRECT_RECT && redirect) {
            if (rect == null) {
                rect = new Rectangle();
            }
            rect.setBounds(x, y, width, height);
            fill(rect);
        } else {
            if (DEBUG) {
                log("fillRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.fillRect(x, y, width, height);
        }
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (REDIRECT_RECT && redirect) {
            if (roundRect == null) {
                roundRect = new RoundRectangle2D.Float();
            }
            roundRect.setRoundRect(x, y, width, height, arcWidth, arcHeight);
            draw(roundRect);
        } else {
            if (DEBUG) {
                log("drawRoundRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
        }
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (REDIRECT_RECT && redirect) {
            if (roundRect == null) {
                roundRect = new RoundRectangle2D.Float();
            }
            roundRect.setRoundRect(x, y, width, height, arcWidth, arcHeight);
            fill(roundRect);
        } else {
            if (DEBUG) {
                log("fillRoundRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
        }
    }

    @Override
    public void draw3DRect(int x, int y, int width, int height, boolean raised) {
        if (REDIRECT_RECT && redirect) {
            super.draw3DRect(x, y, width, height, raised);
        } else {
            if (DEBUG) {
                log("draw3DRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.draw3DRect(x, y, width, height, raised);
        }
    }

    @Override
    public void fill3DRect(int x, int y, int width, int height, boolean raised) {
        if (REDIRECT_RECT && redirect) {
            super.fill3DRect(x, y, width, height, raised);
        } else {
            if (DEBUG) {
                log("fill3DRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
            }
            delegate.fill3DRect(x, y, width, height, raised);
        }
    }

    // --- style operations ---
    @Override
    public Color getBackground() {
        return delegate.getBackground();
    }

    @Override
    public void setBackground(Color color) {
        delegate.setBackground(color);
    }

    @Override
    public Color getColor() {
        return delegate.getColor();
    }

    @Override
    public void setColor(Color c) {
        delegate.setColor(c);
        validatePipe = true;
    }

    @Override
    public Composite getComposite() {
        return delegate.getComposite();
    }

    @Override
    public void setComposite(Composite comp) {
        delegate.setComposite(comp);
        validatePipe = true;
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return delegate.getDeviceConfiguration();
    }

    @Override
    public Font getFont() {
        return delegate.getFont();
    }

    @Override
    public void setFont(Font font) {
        delegate.setFont(font);
    }

    @Override
    public FontMetrics getFontMetrics() {
        return delegate.getFontMetrics();
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        return delegate.getFontMetrics(f);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return delegate.getFontRenderContext();
    }

    @Override
    public Paint getPaint() {
        return delegate.getPaint();
    }

    @Override
    public void setPaint(Paint paint) {
        delegate.setPaint(paint);
        validatePipe = true;
    }

    @Override
    public void setPaintMode() {
        delegate.setPaintMode();
    }

    @Override
    public Stroke getStroke() {
        return delegate.getStroke();
    }

    @Override
    public void setStroke(Stroke s) {
        delegate.setStroke(s);
    }

    @Override
    public void setXORMode(Color c1) {
        delegate.setXORMode(c1);
    }

    // --- rendering hints ---
    @Override
    public Object getRenderingHint(Key hintKey) {
        return delegate.getRenderingHint(hintKey);
    }

    @Override
    public void setRenderingHint(Key hintKey, Object hintValue) {
        delegate.setRenderingHint(hintKey, hintValue);
        updateRedirect();
    }

    @Override
    public RenderingHints getRenderingHints() {
        return delegate.getRenderingHints();
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        delegate.setRenderingHints(hints);
        updateRedirect();
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        delegate.addRenderingHints(hints);
        updateRedirect();
    }
    
    private final void updateRedirect() {
        this.redirect = (getRenderingHint(RenderingHints.KEY_ANTIALIASING) != RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    // --- transform ---
    @Override
    public AffineTransform getTransform() {
        return delegate.getTransform();
    }

    @Override
    public void setTransform(AffineTransform Tx) {
        delegate.setTransform(Tx);
    }

    @Override
    public void translate(int x, int y) {
        delegate.translate(x, y);
    }

    @Override
    public void translate(double tx, double ty) {
        delegate.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        delegate.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        delegate.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        delegate.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        delegate.shear(shx, shy);
    }

    @Override
    public void transform(AffineTransform Tx) {
        delegate.transform(Tx);
    }

    // --- clip ----
    @Override
    public void clip(Shape s) {
        delegate.clip(s);
        validatePipe = true;
    }

    @Override
    public void clipRect(int x, int y, int w, int h) {
        clip(new Rectangle(x, y, w, h));
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return delegate.hit(rect, s, onStroke);
    }

    @Override
    public boolean hitClip(int x, int y, int width, int height) {
        return delegate.hitClip(x, y, width, height);
    }

    @Override
    public Rectangle getClipBounds() {
        return delegate.getClipBounds();
    }

    @Override
    public Rectangle getClipBounds(Rectangle r) {
        return delegate.getClipBounds(r);
    }

    @Override
    public void setClip(int x, int y, int w, int h) {
        setClip(new Rectangle(x, y, w, h));
    }

    @Override
    public Shape getClip() {
        return delegate.getClip();
    }

    @Deprecated
    @Override
    public Rectangle getClipRect() {
        return getClipBounds();
    }

    @Override
    public void setClip(Shape clip) {
        delegate.setClip(clip);
        validatePipe = true;
    }

    // --- img operations ---
    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        delegate.copyArea(x, y, width, height, dx, dy);
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return delegate.drawImage(img, xform, obs);
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        delegate.drawImage(img, op, x, y);
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        delegate.drawRenderedImage(img, xform);
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        delegate.drawRenderableImage(img, xform);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return delegate.drawImage(img, x, y, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return delegate.drawImage(img, x, y, width, height, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        return delegate.drawImage(img, x, y, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor,
                             ImageObserver observer) {
        return delegate.drawImage(img, x, y, width, height, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1,
                             int sx2, int sy2, ImageObserver observer) {
        return delegate.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1,
                             int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        return delegate.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    // --- text operations ---
    @Override
    public void drawString(String str, int x, int y) {
        delegate.drawString(str, x, y);
    }

    @Override
    public void drawString(String str, float x, float y) {
        delegate.drawString(str, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        delegate.drawString(iterator, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        delegate.drawString(iterator, x, y);
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
        delegate.drawGlyphVector(g, x, y);
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
        delegate.drawChars(data, offset, length, x, y);
    }

    @Override
    public void drawBytes(byte[] data, int offset, int length, int x, int y) {
        delegate.drawBytes(data, offset, length, x, y);
    }

    // --- utility ---
    private static void log(final String msg) {
        System.out.println(msg);
    }
    
    static boolean getBoolean(final String key, final String def) {
        return Boolean.valueOf(AccessController.doPrivileged(
                  new GetPropertyAction(key, def)));
    }    

    // --- marlin integration: mimics java2d pipelines ---

    // SurfaceData instances:
    private static final AlphaColorPipe colorPipe;//I
    private static final CompositePipe clipColorPipe;//I
    private static final AAShapePipe AAColorShape;//I
    private static final PixelToParallelogramConverter AAColorViaShape;//U
    private static final PixelToParallelogramConverter AAColorViaPgram;//U
    private static final AAShapePipe AAClipColorShape;//I
    private static final PixelToParallelogramConverter AAClipColorViaShape;//U

    private static final CompositePipe paintPipe;//I
    private static final CompositePipe clipPaintPipe;//I
    private static final AAShapePipe AAPaintShape;//I
    private static final PixelToParallelogramConverter AAPaintViaShape;//U
    private static final AAShapePipe AAClipPaintShape;//I
    private static final PixelToParallelogramConverter AAClipPaintViaShape;//U

    private static final CompositePipe compPipe;//I
    private static final CompositePipe clipCompPipe;//I
    private static final AAShapePipe AACompShape;//I
    private static final PixelToParallelogramConverter AACompViaShape;//U
    private static final AAShapePipe AAClipCompShape;//I
    private static final PixelToParallelogramConverter AAClipCompViaShape;//U

    private static PixelToParallelogramConverter
            makeConverter(AAShapePipe renderer,
                          ParallelogramPipe pgrampipe) {
        return new PixelToParallelogramConverter(renderer,
                pgrampipe,
                1.0 / 8.0, 0.499,
                false);
    }

    private static PixelToParallelogramConverter
            makeConverter(AAShapePipe renderer) {
        return makeConverter(renderer, renderer);
    }

    static {
        try {
            colorPipe = new AlphaColorPipe();//I
            clipColorPipe = new SpanClipRenderer(colorPipe);//I
            AAColorShape = new AAShapePipe(colorPipe);//I
            AAColorViaShape = makeConverter(AAColorShape);//U
            AAColorViaPgram = makeConverter(AAColorShape, colorPipe);//U
            AAClipColorShape = new AAShapePipe(clipColorPipe);//I
            AAClipColorViaShape = makeConverter(AAClipColorShape);//U

            paintPipe = new AlphaPaintPipe();//I
            clipPaintPipe = new SpanClipRenderer(paintPipe);//I
            AAPaintShape = new AAShapePipe(paintPipe);//I
            AAPaintViaShape = makeConverter(AAPaintShape);//U
            AAClipPaintShape = new AAShapePipe(clipPaintPipe);//I
            AAClipPaintViaShape = makeConverter(AAClipPaintShape);//U

            compPipe = (FORCE_BLEND_COMPOSITE) ? new GammaCompositePipe() : new GeneralCompositePipe();//I
            clipCompPipe = new SpanClipRenderer(compPipe);//I
            AACompShape = new AAShapePipe(compPipe);//I
            AACompViaShape = makeConverter(AACompShape);//U
            AAClipCompShape = new AAShapePipe(clipCompPipe);//I
            AAClipCompViaShape = makeConverter(AAClipCompShape);//U

        } catch (Throwable th) {
            throw new IllegalStateException("Unable to create MarlinGraphics2D pipeline (MarlinRenderingEngine) !", th);
        }
    }

    // Custom SunGraphics2D pipeline:
    private ShapeDrawPipe shapepipe;
    private MaskFill alphafill;

    private void validatePipe(SunGraphics2D sg2d) {
        validatePipe = false;
   
        if (sg2d.compositeState == SunGraphics2D.COMP_XOR) {
            throw new IllegalStateException("Unsupported Xor mode !");
        }
     
        // to be in synch:
        sg2d.validatePipe();

        /*
         sg2d.imagepipe = imagepipe;
         if (sg2d.compositeState == SunGraphics2D.COMP_XOR) {
         if (sg2d.paintState > SunGraphics2D.PAINT_ALPHACOLOR) {
         sg2d.drawpipe = paintViaShape;
         sg2d.fillpipe = paintViaShape;
         sg2d.shapepipe = paintShape;
         // REMIND: Ideally custom paint mode would use glyph
         // rendering as opposed to outline rendering but the
         // glyph paint rendering pipeline uses MaskBlit which
         // is not defined for XOR.  This means that text drawn
         // in XOR mode with a Color object is different than
         // text drawn in XOR mode with a Paint object.
         sg2d.textpipe = outlineTextRenderer;
         } else {
         PixelToShapeConverter converter;
         if (canRenderParallelograms(sg2d)) {
         converter = colorViaPgram;
         // Note that we use the transforming pipe here because it
         // will examine the shape and possibly perform an optimized
         // operation if it can be simplified.  The simplifications
         // will be valid for all STROKE and TRANSFORM types.
         sg2d.shapepipe = colorViaPgram;
         } else {
         converter = colorViaShape;
         sg2d.shapepipe = colorPrimitives;
         }
         if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
         sg2d.drawpipe = converter;
         sg2d.fillpipe = converter;
         // REMIND: We should not be changing text strategies
         // between outline and glyph rendering based upon the
         // presence of a complex clip as that could cause a
         // mismatch when drawing the same text both clipped
         // and unclipped on two separate rendering passes.
         // Unfortunately, all of the clipped glyph rendering
         // pipelines rely on the use of the MaskBlit operation
         // which is not defined for XOR.
         sg2d.textpipe = outlineTextRenderer;
         } else {
         if (sg2d.transformState >= SunGraphics2D.TRANSFORM_TRANSLATESCALE) {
         sg2d.drawpipe = converter;
         sg2d.fillpipe = converter;
         } else {
         if (sg2d.strokeState != SunGraphics2D.STROKE_THIN) {
         sg2d.drawpipe = converter;
         } else {
         sg2d.drawpipe = colorPrimitives;
         }
         sg2d.fillpipe = colorPrimitives;
         }
         sg2d.textpipe = solidTextRenderer;
         }
         // assert(sg2d.surfaceData == this);
         }
         } else 
         */
        if (FORCE_BLEND_COMPOSITE
                || sg2d.compositeState == SunGraphics2D.COMP_CUSTOM) {
//            if (sg2d.antialiasHint == SunHints.INTVAL_ANTIALIAS_ON) {
            if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
//                    drawpipe = AAClipCompViaShape;
//                    fillpipe = AAClipCompViaShape;
                shapepipe = AAClipCompViaShape;
                //textpipe = clipCompText;
            } else {
//                    drawpipe = AACompViaShape;
//                    fillpipe = AACompViaShape;
                shapepipe = AACompViaShape;
                //textpipe = compText;
            }
            /*                
             } else {
             drawpipe = compViaShape;
             fillpipe = compViaShape;
             shapepipe = compShape;
             /*
             if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
             textpipe = clipCompText;
             } else {
             textpipe = compText;
             }
             }
             */
        } else /*if (sg2d.antialiasHint == SunHints.INTVAL_ANTIALIAS_ON)*/ {
            alphafill = getMaskFill(sg2d);
            if (alphafill != null) {
                if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
//                    drawpipe = AAClipColorViaShape;
//                    fillpipe = AAClipColorViaShape;
                    shapepipe = AAClipColorViaShape;
                    //textpipe = clipColorText;
                } else {
                    PixelToParallelogramConverter converter
                                                  = (alphafill.canDoParallelograms()
                                    ? AAColorViaPgram
                                    : AAColorViaShape);
//                    drawpipe = converter;
//                    fillpipe = converter;
                    shapepipe = converter;
                    /*
                     if (sg2d.paintState > SunGraphics2D.PAINT_ALPHACOLOR ||
                     sg2d.compositeState > SunGraphics2D.COMP_ISCOPY)
                     {
                     textpipe = colorText;
                     } else {
                     textpipe = getTextPipe(sg2d, true); // AA==ON
                     }
                     */
                }
            } else {
                if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
//                    drawpipe = AAClipPaintViaShape;
//                    fillpipe = AAClipPaintViaShape;
                    shapepipe = AAClipPaintViaShape;
                    //textpipe = clipPaintText;
                } else {
//                    drawpipe = AAPaintViaShape;
//                    fillpipe = AAPaintViaShape;
                    shapepipe = AAPaintViaShape;
                    //textpipe = paintText;
                }
            }
        }
        /*
         else if (sg2d.paintState > SunGraphics2D.PAINT_ALPHACOLOR ||
         sg2d.compositeState > SunGraphics2D.COMP_ISCOPY ||
         sg2d.clipState == SunGraphics2D.CLIP_SHAPE)
         {
         sg2d.drawpipe = paintViaShape;
         sg2d.fillpipe = paintViaShape;
         sg2d.shapepipe = paintShape;
         sg2d.alphafill = getMaskFill(sg2d);
         // assert(sg2d.surfaceData == this);
         if (sg2d.alphafill != null) {
         if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
         sg2d.textpipe = clipColorText;
         } else {
         sg2d.textpipe = colorText;
         }
         } else {
         if (sg2d.clipState == SunGraphics2D.CLIP_SHAPE) {
         sg2d.textpipe = clipPaintText;
         } else {
         sg2d.textpipe = paintText;
         }
         }
         } else {
         PixelToShapeConverter converter;
         if (canRenderParallelograms(sg2d)) {
         converter = colorViaPgram;
         // Note that we use the transforming pipe here because it
         // will examine the shape and possibly perform an optimized
         // operation if it can be simplified.  The simplifications
         // will be valid for all STROKE and TRANSFORM types.
         sg2d.shapepipe = colorViaPgram;
         } else {
         converter = colorViaShape;
         sg2d.shapepipe = colorPrimitives;
         }
         if (sg2d.transformState >= SunGraphics2D.TRANSFORM_TRANSLATESCALE) {
         sg2d.drawpipe = converter;
         sg2d.fillpipe = converter;
         } else {
         if (sg2d.strokeState != SunGraphics2D.STROKE_THIN) {
         sg2d.drawpipe = converter;
         } else {
         sg2d.drawpipe = colorPrimitives;
         }
         sg2d.fillpipe = colorPrimitives;
         }

         sg2d.textpipe = getTextPipe(sg2d, false); // AA==OFF
         // assert(sg2d.surfaceData == this);
         }

         // check for loops
         if (sg2d.textpipe  instanceof LoopBasedPipe ||
         sg2d.shapepipe instanceof LoopBasedPipe ||
         sg2d.fillpipe  instanceof LoopBasedPipe ||
         sg2d.drawpipe  instanceof LoopBasedPipe ||
         sg2d.imagepipe instanceof LoopBasedPipe)
         {
         sg2d.loops = getRenderLoops(sg2d);
         }
         */
    }

    private static SurfaceType getPaintSurfaceType(SunGraphics2D sg2d) {
        switch (sg2d.paintState) {
            case SunGraphics2D.PAINT_OPAQUECOLOR:
                return SurfaceType.OpaqueColor;
            case SunGraphics2D.PAINT_ALPHACOLOR:
                return SurfaceType.AnyColor;
            case SunGraphics2D.PAINT_GRADIENT:
                if (sg2d.paint.getTransparency() == OPAQUE) {
                    return SurfaceType.OpaqueGradientPaint;
                } else {
                    return SurfaceType.GradientPaint;
                }
            case SunGraphics2D.PAINT_LIN_GRADIENT:
                if (sg2d.paint.getTransparency() == OPAQUE) {
                    return SurfaceType.OpaqueLinearGradientPaint;
                } else {
                    return SurfaceType.LinearGradientPaint;
                }
            case SunGraphics2D.PAINT_RAD_GRADIENT:
                if (sg2d.paint.getTransparency() == OPAQUE) {
                    return SurfaceType.OpaqueRadialGradientPaint;
                } else {
                    return SurfaceType.RadialGradientPaint;
                }
            case SunGraphics2D.PAINT_TEXTURE:
                if (sg2d.paint.getTransparency() == OPAQUE) {
                    return SurfaceType.OpaqueTexturePaint;
                } else {
                    return SurfaceType.TexturePaint;
                }
            default:
            case SunGraphics2D.PAINT_CUSTOM:
                return SurfaceType.AnyPaint;
        }
    }

    private static CompositeType getFillCompositeType(SunGraphics2D sg2d) {
        CompositeType compType = sg2d.imageComp;
        if (sg2d.compositeState == SunGraphics2D.COMP_ISCOPY) {
            if (compType == CompositeType.SrcOverNoEa) {
                compType = CompositeType.OpaqueSrcOverNoEa;
            } else {
                compType = CompositeType.SrcNoEa;
            }
        }
        return compType;
    }

    /**
     * Returns a MaskFill object that can be used on this destination
     * with the source (paint) and composite types determined by the given
     * SunGraphics2D, or null if no such MaskFill object can be located.
     * Subclasses can override this method if they wish to filter other
     * attributes (such as the hardware capabilities of the destination
     * surface) before returning a specific MaskFill object.
     */
    private static MaskFill getMaskFill(SunGraphics2D sg2d) {
        SurfaceType src = getPaintSurfaceType(sg2d);
        CompositeType comp = getFillCompositeType(sg2d);
        SurfaceType dst = sg2d.surfaceData.getSurfaceType();
        return MaskFill.getFromCache(src, comp, dst);
    }

}
