/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import sun.awt.image.BufImgSurfaceData;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.Blit;
import sun.java2d.loops.MaskBlit;
import sun.java2d.loops.CompositeType;
import sun.java2d.pipe.CompositePipe;

public final class GeneralCompositePipe implements CompositePipe {

    // TODO: use System property (2.2 or 1.8 ...)
    private final static boolean FORCE_CUSTOM_BLEND = false;

    /**
     * Per-thread TileContext (very small so do not use any Soft or Weak Reference)
     */
    private static final ThreadLocal<TileContext> tileContextThreadLocal = new ThreadLocal<TileContext>() {
        @Override
        protected TileContext initialValue() {
            return new TileContext();
        }
    };

    final static class TileContext {

        SunGraphics2D sunG2D;
        PaintContext paintCtxt;
        CompositeContext compCtxt;
        ColorModel compModel;
        Object pipeState;
        // LBO: cached values
        boolean isBlendComposite;
        int[] maskStride = new int[32];
        BlendComposite blendComposite = null;
        boolean hasExtraAlpha = false;

        TileContext() {
            // ThreadLocal constructor
        }

        void init(SunGraphics2D sg, PaintContext pCtx,
                  CompositeContext cCtx, ColorModel cModel,
                  boolean blendComposite, boolean extraAlpha)
        {
            sunG2D = sg;
            paintCtxt = pCtx;
            compCtxt = cCtx;
            compModel = cModel;
            isBlendComposite = blendComposite;
            hasExtraAlpha = extraAlpha;
        }

        int[] getMaskStride(final int len) {
            int[] t = maskStride;
            if (t.length < len) {
                // create a larger stride and may free current maskStride (too small)
                maskStride = t = new int[len];
            }
            return t;
        }
        
        BlendComposite getBlendComposite(BlendComposite.BlendingMode mode) {
            if (blendComposite == null) {
                blendComposite = BlendComposite.getInstance(mode);
            }
            return blendComposite;
        }
    }

    @Override
    public Object startSequence(SunGraphics2D sg, Shape s, Rectangle devR,
                                int[] abox) {
        // warning: clone map:
        final RenderingHints hints = sg.getRenderingHints();
        final ColorModel model = sg.getDeviceColorModel();
        final PaintContext paintContext = sg.paint.createContext(model, devR, s.getBounds2D(), sg.cloneTransform(), hints);
        final Composite origComposite = sg.composite;
        
        // use ThreadLocal (to reduce memory footprint):
        final TileContext tc = tileContextThreadLocal.get();

        boolean isBlendComposite = false;
        boolean extraAlpha = false;
        
        Composite composite = origComposite;
        if (FORCE_CUSTOM_BLEND) {
            if (origComposite instanceof AlphaComposite) {
                final AlphaComposite ac = (AlphaComposite)origComposite;

                if (ac.getRule() == AlphaComposite.SRC_OVER) {
                    // only SrcOver implemented for now
                    // TODO: implement all Porter-Duff rules 
                    BlendComposite blendComposite = tc.getBlendComposite(BlendComposite.BlendingMode.SRC_OVER);
                    // set (optional) extra alpha:
                    blendComposite.setAlpha(ac.getAlpha());
                    
                    isBlendComposite = true;
                    extraAlpha = blendComposite.hasExtraAlpha();
                    composite = blendComposite;
                }
            }
        } else {
            isBlendComposite = BlendComposite.class.equals(composite.getClass());
        }
        
        final CompositeContext compositeContext = composite.createContext(paintContext.getColorModel(), model, hints);

        tc.init(sg, paintContext, compositeContext, model, isBlendComposite, extraAlpha);
        return tc;
    }

    @Override
    public boolean needTile(Object ctx, int x, int y, int w, int h) {
        return true;
    }

    /**
     * GeneralCompositePipe.renderPathTile works with custom composite operator provided by an application
     */
    @Override
    public void renderPathTile(Object ctx,
                               byte[] atile, int offset, int tilesize,
                               int x, int y, int w, int h) {
        final TileContext context = (TileContext) ctx;
        final PaintContext paintCtxt = context.paintCtxt;
        final CompositeContext compCtxt = context.compCtxt;
        final SunGraphics2D sg = context.sunG2D;
        final boolean blendComposite = context.isBlendComposite;
        final boolean extraAlpha = context.hasExtraAlpha;

        final Raster srcRaster = paintCtxt.getRaster(x, y, w, h);

        final Raster dstIn;
        final WritableRaster dstOut;

        final SurfaceData sd = sg.getSurfaceData();
        final Raster dstRaster = sd.getRaster(x, y, w, h);
        if ((dstRaster instanceof WritableRaster) && (atile == null) && (!extraAlpha)) {
            dstOut = ((WritableRaster) dstRaster).createWritableChild(x, y, w, h, 0, 0, null);
            dstIn = dstOut;
        } else {
            dstIn = dstRaster.createChild(x, y, w, h, 0, 0, null);

            // TODO: cache such raster as it is very costly (int[])
            dstOut = dstIn.createCompatibleWritableRaster();
        }

        if (blendComposite) {
            // define mask alpha into dstOut:

            // INT_RGBA only: TODO: check raster format !
            final int[] maskPixels = context.getMaskStride(w);

            // atile = null means mask=255 (src opacity full)
            if (atile == null) {
                // TODO: use fill ?
                for (int i = 0; i < w; i++) {
                    maskPixels[i] = 0xFF;
                }
                for (int j = 0; j < h; j++) {
                    // TODO: find most efficient method:
                    dstOut.setDataElements(0, j, w, 1, maskPixels);
                }
            } else {
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        maskPixels[i] = atile[j * tilesize + (i + offset)] & 0xFF;
                    }
                    // TODO: find most efficient method:
                    dstOut.setDataElements(0, j, w, 1, maskPixels);
                }
            }
        }
        compCtxt.compose(srcRaster, dstIn, dstOut);

        if (dstRaster != dstOut && dstOut.getParent() != dstRaster) {
            if (dstRaster instanceof WritableRaster
                    && ((blendComposite) || (atile == null))) {
                // TODO: find most efficient method to copy between rasters (use transfer type ?)
                ((WritableRaster) dstRaster).setDataElements(x, y, dstOut);
            } else {
                final ColorModel cm = sg.getDeviceColorModel();
                final BufferedImage resImg
                                    = new BufferedImage(cm, dstOut,
                                cm.isAlphaPremultiplied(),
                                null);
                final SurfaceData resData = BufImgSurfaceData.createData(resImg);
                if (atile == null) {
                    final Blit blit = Blit.getFromCache(resData.getSurfaceType(),
                            CompositeType.SrcNoEa,
                            sd.getSurfaceType());
                    blit.Blit(resData, sd, AlphaComposite.Src, null,
                            0, 0, x, y, w, h);
                } else {
                    final MaskBlit blit = MaskBlit.getFromCache(resData.getSurfaceType(),
                            CompositeType.SrcNoEa,
                            sd.getSurfaceType());
                    blit.MaskBlit(resData, sd, AlphaComposite.Src, null,
                            0, 0, x, y, w, h,
                            atile, offset, tilesize);
                }
            }
        }
    }

    @Override
    public void skipTile(Object ctx, int x, int y) {
    }

    @Override
    public void endSequence(Object ctx) {
        final TileContext context = (TileContext) ctx;
        if (context.paintCtxt != null) {
            context.paintCtxt.dispose();
        }
        if (context.compCtxt != null) {
            context.compCtxt.dispose();
        }
    }
}
