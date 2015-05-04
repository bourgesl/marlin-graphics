package org.marlin.graphics;

/*******************************************************************************
 * Marlin-graphics project (GPLv2 + CP)
 ******************************************************************************/
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
import java.text.AttributedCharacterIterator;
import java.util.Map;
import org.marlin.pisces.MarlinRenderingEngine;
import sun.java2d.SunGraphics2D;

/*
 check AA hint => use marlin or use delegate !
 */
public final class MarlinGraphics2D extends Graphics2D {

    private final static boolean DEBUG = false;

    // --- marlin integration ---
    /** AAShapePipe singleton */
    private static final AAShapePipe shapePipe;

    static {
        try {
            final MarlinRenderingEngine renderengine = new MarlinRenderingEngine();
            shapePipe = new AAShapePipe(renderengine, new GeneralCompositePipe());
        } catch (Throwable th) {
            throw new IllegalStateException("Unable to create MarlinRenderingEngine !", th);
        }
    }

    /* members */
    private final SunGraphics2D delegate;
    private final BufferedImage image;
    /** redirect flag: true means to use Marlin instead of default rendering engine */
    private boolean redirect = true;

    public MarlinGraphics2D(final BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            throw new IllegalStateException("Unsupported image type: only TYPE_INT_ARGB !");
        }
        final Graphics2D g2d = image.createGraphics();
        if (!(g2d instanceof SunGraphics2D)) {
            g2d.dispose();
            throw new IllegalStateException("BufferedImage.createGraphics() is not SunGraphics2D !");
        }
        this.delegate = (SunGraphics2D) g2d;
        this.image = image;

        // Set rendering hints:
        setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
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

    // --- shape operations (handled by Marlin) ---
    @Override
    public void draw(Shape s) {
        if (DEBUG) {
            System.out.println("draw: " + s);
        }
        if (redirect) {
            shapePipe.draw(delegate, s);
        } else {
            delegate.draw(s);
        }
    }

    @Override
    public void fill(Shape s) {
        if (DEBUG) {
            System.out.println("fill: " + s);
        }
        if (redirect) {
            shapePipe.fill(delegate, s);
        } else {
            delegate.fill(s);
        }
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        if (DEBUG) {
            System.out.println("clearRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            Composite c = delegate.getComposite();
            Paint p = delegate.getPaint();
            setComposite(AlphaComposite.Src);
            setColor(getBackground());
            fillRect(x, y, width, height);
            setPaint(p);
            setComposite(c);
        } else {
            delegate.clearRect(x, y, width, height);
        }
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        if (DEBUG) {
            System.out.println("drawRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new Rectangle(x, y, width, height));
        } else {
            delegate.drawRect(x, y, width, height);
        }
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        if (DEBUG) {
            System.out.println("fillRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            fill(new Rectangle(x, y, width, height));
        } else {
            delegate.fillRect(x, y, width, height);
        }
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (DEBUG) {
            System.out.println("drawRoundRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new RoundRectangle2D.Float(x, y, width, height, arcWidth, arcHeight));
        } else {
            delegate.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
        }
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (DEBUG) {
            System.out.println("fillRoundRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            fill(new RoundRectangle2D.Float(x, y, width, height, arcWidth, arcHeight));
        } else {
            delegate.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
        }
    }

    @Override
    public void draw3DRect(int x, int y, int width, int height, boolean raised) {
        if (DEBUG) {
            System.out.println("draw3DRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            super.draw3DRect(x, y, width, height, raised);
        } else {
            delegate.draw3DRect(x, y, width, height, raised);
        }
    }

    @Override
    public void fill3DRect(int x, int y, int width, int height, boolean raised) {
        if (DEBUG) {
            System.out.println("fill3DRect: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            super.fill3DRect(x, y, width, height, raised);
        } else {
            delegate.fill3DRect(x, y, width, height, raised);
        }
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        if (DEBUG) {
            System.out.println("drawLine: (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new Line2D.Float(x1, y1, x2, y2));
        } else {
            delegate.drawLine(x1, y1, x2, y2);
        }
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        if (DEBUG) {
            System.out.println("drawOval: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new Ellipse2D.Float(x, y, width, height));
        } else {
            delegate.drawOval(x, y, width, height);
        }
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        if (DEBUG) {
            System.out.println("fillOval: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            fill(new Ellipse2D.Float(x, y, width, height));
        } else {
            delegate.fillOval(x, y, width, height);
        }
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (DEBUG) {
            System.out.println("drawArc: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new Arc2D.Float(x, y, width, height,
                    startAngle, arcAngle,
                    Arc2D.OPEN));
        } else {
            delegate.drawArc(x, y, width, height, startAngle, arcAngle);
        }
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (DEBUG) {
            System.out.println("fillArc: (" + x + "," + y + ") to (" + (x + width) + "," + (y + height) + ")");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            fill(new Arc2D.Float(x, y, width, height,
                    startAngle, arcAngle,
                    Arc2D.PIE));
        } else {
            delegate.fillArc(x, y, width, height, startAngle, arcAngle);
        }
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        if (DEBUG) {
            System.out.println("drawPolyline: (" + nPoints + " points)");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new Polygon(xPoints, yPoints, nPoints));
        } else {
            delegate.drawPolyline(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (DEBUG) {
            System.out.println("drawPolygon: (" + nPoints + " points)");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            draw(new Polygon(xPoints, yPoints, nPoints));
        } else {
            delegate.drawPolygon(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (DEBUG) {
            System.out.println("fillPolygon: (" + nPoints + " points)");
        }
        if (redirect) {
            // TODO: cache shapes instances:
            fill(new Polygon(xPoints, yPoints, nPoints));
        } else {
            delegate.fillPolygon(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void drawPolygon(Polygon p) {
        if (DEBUG) {
            System.out.println("drawPolygon: " + p);
        }
        draw(p);
    }

    @Override
    public void fillPolygon(Polygon p) {
        if (DEBUG) {
            System.out.println("fillPolygon: " + p);
        }
        fill(p);
    }

    // --- other operations ---
    @Override
    public Graphics create() {
        return delegate.create();
    }

    @Override
    public Graphics create(int x, int y, int width, int height) {
        return delegate.create(x, y, width, height);
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
    }

    @Override
    public Composite getComposite() {
        return delegate.getComposite();
    }

    @Override
    public void setComposite(Composite comp) {
        delegate.setComposite(comp);
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
    }

    @Override
    public RenderingHints getRenderingHints() {
        return delegate.getRenderingHints();
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        delegate.setRenderingHints(hints);
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        delegate.addRenderingHints(hints);
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
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        delegate.clipRect(x, y, width, height);
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
    public void setClip(int x, int y, int width, int height) {
        delegate.setClip(x, y, width, height);
    }

    @Override
    public Shape getClip() {
        return delegate.getClip();
    }

    @Override
    public Rectangle getClipRect() {
        return getClipBounds();
    }

    @Override
    public void setClip(Shape clip) {
        delegate.setClip(clip);
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

}
