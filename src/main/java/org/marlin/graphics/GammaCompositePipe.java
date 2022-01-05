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
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.CompositePipe;

public final class GammaCompositePipe implements CompositePipe {

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

        int colorRGBA;
        PaintContext paintCtxt;
        BlendComposite.BlendingContext compCtxt;
        BlendComposite blendComposite = null;
        SurfaceData sd = null;

        TileContext() {
            // ThreadLocal constructor
        }

        void init(final SurfaceData sd,
                  final int colorRGBA, final PaintContext pCtx,
                  final BlendComposite.BlendingContext cCtx,
                  final BlendComposite blendComposite) {

            this.colorRGBA = colorRGBA;
            this.paintCtxt = pCtx;
            this.compCtxt = cCtx;
            this.blendComposite = blendComposite;
            this.sd = sd;
        }

        void dispose() {
            this.colorRGBA = 0;
            if (paintCtxt != null) {
                paintCtxt.dispose();
                paintCtxt = null;
            }
            compCtxt = null;
            blendComposite = null;
            sd = null;
        }
    }

    @Override
    public Object startSequence(final SunGraphics2D sg, final Shape s, final Rectangle devR,
                                final int[] abox) {

        final int colorRGBA;
        final PaintContext paintContext;
        if (sg.paint instanceof Color) {
            colorRGBA = ((Color) sg.paint).getRGB();
            paintContext = null;
        } else {
            colorRGBA = 0;
            // warning: clone hints map:
            paintContext = sg.paint.createContext(sg.getDeviceColorModel(), devR, s.getBounds2D(),
                    sg.cloneTransform(), sg.getRenderingHints());
        }

        final Composite origComposite = sg.composite;

        BlendComposite blendComposite = null;

        if (origComposite instanceof AlphaComposite) {
            final AlphaComposite ac = (AlphaComposite) origComposite;

            if (ac.getRule() == AlphaComposite.SRC_OVER) {
                // only SrcOver implemented for now
                // TODO: implement all Porter-Duff rules 
                // set (optional) extra alpha:
                blendComposite = BlendComposite.getInstance(BlendComposite.BlendingMode.SRC_OVER, ac.getAlpha());
            }
        }

        if (blendComposite == null) {
            throw new IllegalArgumentException("Unsupported blending mode for composite: " + origComposite);
        }

        final SurfaceData sd = sg.getSurfaceData();
        final SurfaceType sdt = sd.getSurfaceType();

        if ((sdt != SurfaceType.IntArgb) && (sdt != SurfaceType.IntArgbPre)
                && (sdt != SurfaceType.FourByteAbgr) && (sdt != SurfaceType.FourByteAbgrPre)) {
            throw new IllegalArgumentException("Unsupported surface type: " + sdt);
        }

        final BlendComposite.BlendingContext compositeContext = blendComposite.createContext(sdt);

        // use ThreadLocal (to reduce memory footprint):
        final TileContext tc = tileContextThreadLocal.get();
        tc.init(sd, colorRGBA, paintContext, compositeContext, blendComposite);
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
    public void renderPathTile(final Object ctx,
                               final byte[] atile, final int offset, final int tilesize,
                               final int x, final int y, final int w, final int h) {

        // System.out.println("render tile: (" + w + " x " + h + ")");
        final TileContext context = (TileContext) ctx;
        final BlendComposite.BlendingContext compCtxt = context.compCtxt;

        int rgba = 0;
        final PaintContext paintCtxt = context.paintCtxt;
        final Raster srcRaster;

        if (paintCtxt != null) {
            // // hack PaintContext -> cached tile is limited to 64x64 !
            srcRaster = paintCtxt.getRaster(x, y, w, h);
        } else {
            // hack ColorPaintContext -> to avoid fill color on complete tile (cached tile is limited to 64x64):
            rgba = context.colorRGBA;
            srcRaster = null;
        }

        final Raster dstRaster = context.sd.getRaster(x, y, w, h);

        if (!(dstRaster instanceof WritableRaster)) {
            throw new IllegalStateException("Raster is not writable [" + dstRaster + "]");
        }

        // System.out.println("createWritableChild: (" + w + " x " + h + ")");
        final WritableRaster dstOut
                             = ((WritableRaster) dstRaster).createWritableChild(x, y, w, h, 0, 0, null);
        // = (WritableRaster)dstRaster;

        // Perform compositing:
        // srcRaster = paint raster
        // dstIn = surface destination raster (input)
        // dstOut = writable destination raster (output)
        compCtxt.compose(rgba, srcRaster, atile, offset, tilesize, dstOut, w, h);
    }

    @Override
    public void skipTile(Object ctx, int x, int y) {
    }

    @Override
    public void endSequence(Object ctx) {
        ((TileContext) ctx).dispose();
    }
}
