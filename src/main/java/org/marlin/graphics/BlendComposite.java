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

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public final class BlendComposite implements Composite {

    // TODO: use System property
    /* 2.2 is the standard gamma for current LCD/CRT monitors */
    private final static double GAMMA = 2.2;
    private final static BlendComposite.GammaLUT gamma_LUT = new BlendComposite.GammaLUT(GAMMA);

    public static String getBlendingMode() {
        return "_gam_" + GAMMA;
    }

    public static class GammaLUT {

        private final static int MAX_COLORS_08 = (1 << 8) - 1; // 255
        private final static int MAX_COLORS_16 = MAX_COLORS_08 * MAX_COLORS_08; // 255 * 255
        // use byte / short
        final int[] dir = new int[MAX_COLORS_08 + 1];
        final int[] inv = new int[MAX_COLORS_16 + 1];

        GammaLUT(final double gamma) {
            final double invGamma = 1.0 / gamma;
            double max, scale;

            // [0; 255] to [0; 65535]
            max = (double) MAX_COLORS_08;
            scale = (double) MAX_COLORS_16;

            for (int i = 0; i <= MAX_COLORS_08; i++) {
                dir[i] = (int) Math.round(scale * Math.pow(i / max, gamma));
                // System.out.println("dir[" + i + "] = " + dir[i]);
            }

            // [0; 65535] to [0; 65535]
            scale = max = (double) MAX_COLORS_16;

            for (int i = 0; i <= MAX_COLORS_16; i++) {
                inv[i] = (int) Math.round(scale * Math.pow(i / max, invGamma));
                // System.out.println("inv[" + i + "] = " + inv[i]);
            }
        }
    }

    public enum BlendingMode {

        SRC_OVER
    }

    /* members */
    BlendComposite.BlendingMode mode;
    float extraAlpha;

    private BlendComposite(BlendComposite.BlendingMode mode) {
        this.mode = mode;
        this.extraAlpha = 1f;
    }

    public static BlendComposite getInstance(BlendComposite.BlendingMode mode) {
        // TODO: cache instances like AlphaComposite does:
        return new BlendComposite(mode);
    }

    public BlendComposite.BlendingMode getMode() {
        return mode;
    }
    
    /**
     * Returns the alpha value of this <code>AlphaComposite</code>.  If this
     * <code>AlphaComposite</code> does not have an alpha value, 1.0 is returned.
     * @return the alpha value of this <code>AlphaComposite</code>.
     */
    public float getAlpha() {
        return extraAlpha;
    }

    public void setAlpha(float alpha) {
        this.extraAlpha = alpha;
    }
    
    public boolean hasExtraAlpha() {
        return this.extraAlpha != 1f;
    }

    @Override
    public int hashCode() {
        return mode.ordinal();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlendComposite)) {
            return false;
        }

        BlendComposite bc = (BlendComposite) obj;

        return (mode == bc.mode);
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel,
                                          ColorModel dstColorModel, RenderingHints hints) {

        // use ThreadLocal (to reduce memory footprint):
        final BlendingContext bc = blendContextThreadLocal.get();
        bc.init(this);
        return bc;

    }
    /**
     * Per-thread BlendingContext (very small so do not use any Soft or Weak Reference)
     */
    private static final ThreadLocal<BlendingContext> blendContextThreadLocal = new ThreadLocal<BlendingContext>() {
        @Override
        protected BlendingContext initialValue() {
            return new BlendingContext();
        }
    };

    private static final class BlendingContext implements CompositeContext {

        private BlendComposite.Blender _blender;
        // recycled arrays into context (shared):
        final int[] _srcPixel = new int[4];
        final int[] _dstPixel = new int[4];
        final int[] _result = new int[4];
        int[] _srcPixels = new int[32];
        int[] _dstPixels = new int[32];
        int[] _maskPixels = new int[32];
        
        int alpha;

        BlendingContext() {
            // ThreadLocal constructor
        }

        void init(final BlendComposite composite) {
            this._blender = BlendComposite.Blender.getBlenderFor(composite);
            this.alpha = Math.round(255f * composite.extraAlpha);
        }

        int[] getSrcPixels(final int len) {
            int[] t = _srcPixels;
            if (t.length < len) {
                // create a larger stride and may free current maskStride (too small)
                _srcPixels = t = new int[len];
            }
            return t;
        }

        int[] getDstPixels(final int len) {
            int[] t = _dstPixels;
            if (t.length < len) {
                // create a larger stride and may free current maskStride (too small)
                _dstPixels = t = new int[len];
            }
            return t;
        }

        int[] getMaskPixels(final int len) {
            int[] t = _maskPixels;
            if (t.length < len) {
                // create a larger stride and may free current maskStride (too small)
                _maskPixels = t = new int[len];
            }
            return t;
        }

        @Override
        public void dispose() {
        }

        @Override
        public void compose(Raster srcIn, Raster dstIn, WritableRaster dstOut) {
            if (srcIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT
                    || dstIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT
                    || dstOut.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
                throw new IllegalStateException(
                        "Source and destination must store pixels as INT.");
            }
            /*
             System.out.println("src = " + src.getBounds());
             System.out.println("dstIn = " + dstIn.getBounds());
             System.out.println("dstOut = " + dstOut.getBounds());
             */
            final int width = Math.min(srcIn.getWidth(), dstIn.getWidth());
            final int height = Math.min(srcIn.getHeight(), dstIn.getHeight());

            final int[] gamma_dir = gamma_LUT.dir;
            final int[] gamma_inv = gamma_LUT.inv;
            
            final int extraAlpha = this.alpha;
            final int normAlpha = (0xFF * 0xFF);

            final BlendComposite.Blender blender = _blender;

            // use shared arrays:
            final int[] srcPixel = _srcPixel;
            final int[] dstPixel = _dstPixel;
            final int[] result = _result;

            final int[] srcPixels = getSrcPixels(width);
            final int[] dstPixels = getDstPixels(width);
            final int[] maskPixels = getMaskPixels(width);

            int pixel, am, as, ad, ar, fs, fd;

            for (int y = 0; y < height; y++) {
                // TODO: use directly BufferInt
                srcIn.getDataElements(0, y, width, 1, srcPixels);
                dstIn.getDataElements(0, y, width, 1, dstPixels);
                dstOut.getDataElements(0, y, width, 1, maskPixels);

                for (int x = 0; x < width; x++) {
                    // pixels are stored as INT_ARGB
                    // our arrays are [R, G, B, A]

                    // coverage is stored directly as byte in maskPixel:
                    am = maskPixels[x];

                    /*
                     * coverage = 0 means translucent: 
                     * result = destination (already the case)
                     */
                    if (am != 0) {
                        // ELSE: coverage between [1;255]:

                        // blend
                        pixel = srcPixels[x];
                        as = ((pixel >> 24) & 0xFF);

                        // fade operator:
                        // alpha in range [0; 255]
                        as = (as * am * extraAlpha) / normAlpha;

                        // RGBA: premultiply color component by alpha:
                        // color components in range [0; 65535]
                        srcPixel[0] = gamma_dir[(pixel >> 16) & 0xFF];
                        srcPixel[1] = gamma_dir[(pixel >> 8) & 0xFF];
                        srcPixel[2] = gamma_dir[(pixel) & 0xFF];
                        srcPixel[3] = as;

                        // Src Over Dst rule: factors in range [0; 255]
                        fs = 0xFF;
                        fd = 0xFF - as;
                        
                        if (as != 0xFF) {
                            srcPixel[0] = (srcPixel[0] * as) / 0xFF;
                            srcPixel[1] = (srcPixel[1] * as) / 0xFF;
                            srcPixel[2] = (srcPixel[2] * as) / 0xFF;

                            pixel = dstPixels[x];
                            ad = (pixel >> 24) & 0xFF;

                            // RGBA: premultiply color component by alpha:
                            // color components in range [0; 65535]
                            dstPixel[0] = gamma_dir[(pixel >> 16) & 0xFF];
                            dstPixel[1] = gamma_dir[(pixel >> 8) & 0xFF];
                            dstPixel[2] = gamma_dir[(pixel) & 0xFF];
                            dstPixel[3] = ad;

                            if (ad != 0xFF) {
                                dstPixel[0] = (dstPixel[0] * ad) / 0xFF;
                                dstPixel[1] = (dstPixel[1] * ad) / 0xFF;
                                dstPixel[2] = (dstPixel[2] * ad) / 0xFF;
                            }
                        }
                        
                        // recycle int[] instances:
                        blender.blend(srcPixel, dstPixel, fs, fd, result);

                        // mixes the result with the opacity
                        // alpha in range [0; 255]
                        ar = result[3];
                        if (ar == 0) {
                            dstPixels[x] = 0x00FFFFFF;
                        } else {
                            // RGBA: divide color component by alpha 
                            // color components in range [0; 65535]
                            dstPixels[x] = (ar << 24)
                                    | (gamma_inv[result[0]] / ar) << 16
                                    | (gamma_inv[result[1]] / ar) << 8
                                    | (gamma_inv[result[2]] / ar);
                        }
                    }
                }
                dstOut.setDataElements(0, y, width, 1, dstPixels);
            }
        }
    }

    private static abstract class Blender {

        private final static BlenderSrcOver srcOverBlender = new BlenderSrcOver();

        // fs and fs are in [0; 255] (8bits)
        public abstract void blend(int[] src, int[] dst, final int fs, final int fd, int[] result);

        public static BlendComposite.Blender getBlenderFor(BlendComposite composite) {
            switch (composite.getMode()) {
                case SRC_OVER:
                    return srcOverBlender;
                default:
                    throw new IllegalArgumentException("Blender not implement for " + composite.getMode().name());
            }
        }
    }

    private final static class BlenderSrcOver extends BlendComposite.Blender {

        @Override
        public void blend(final int[] src, final int[] dst, final int fs, final int fd, final int[] result) {
            // src & dst are gamma corrected and premultiplied by their alpha values:

            // ALPHA in [0; 255] so divide by 255 (not shift):
            // color components in range [0; 65535]
            result[0] = (src[0] * fs + dst[0] * fd) / 0xFF;
            result[1] = (src[1] * fs + dst[1] * fd) / 0xFF;
            result[2] = (src[2] * fs + dst[2] * fd) / 0xFF;
            // alpha in range [0; 255]
            result[3] = (src[3] * fs + dst[3] * fd) / 0xFF;
        }
    }
}
