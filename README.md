Marlin-graphics
===============

Marlin renderer wrapped as a Graphics2D implementation:

Marlin-renderer is an open source (GPL2+CP) Java2D RenderingEngine optimized for performance.

Marlin-Graphics provides an alternate Graphics2D implementation to redirect shape draw/fill operations to Marlin-renderer instead of Ductus/Pisces renderers (Oracle JDK / OpenJDK).

Usage
=====

<pre>
final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

// Create the MarlinGraphics2D wrapping your image's Graphics2D implementation:
final MarlinGraphics2D g2d = new MarlinGraphics2D(image);

// Use g2d to perform java2d operations (draw, fill) as usual

// Do not forget to dispose the graphics2D instance:
g2d.dispose();
</pre>

License
=======

As marlin is a fork from OpenJDK 8 pisces, its license is the OpenJDK's license = GPL2+CP:

GNU General Public License, version 2,
with the Classpath Exception

The GNU General Public License (GPL)

Version 2, June 1991

See License.md

Getting in touch
================

Users and developers interested in the Marlin-renderer are kindly invited to join the [marlin-renderer](https://groups.google.com/forum/#!forum/marlin-renderer) Google Group.

Related projects
===============

[Marlin-renderer](https://github.com/bourgesl/marlin-renderer)
