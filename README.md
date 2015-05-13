Marlin-graphics
===============

Marlin renderer wrapped as a Graphics2D implementation:

[Marlin-renderer](https://github.com/bourgesl/marlin-renderer) is an open source (GPL2+CP) Java2D RenderingEngine optimized for performance and scalability.

Marlin-Graphics provides an alternate Graphics2D implementation to redirect draw / fill shape operations to the Marlin-renderer instead of Ductus/Pisces renderers (Oracle JDK / OpenJDK): FAST and easy to use !

*Marlin-Graphics does not require any bootclasspath change*: 
Just add marlin-graphics and marlin-renderer jar files into your classpath to use it in your application !

Usage
=====

<pre>
// Use Premultiplied RGBA images (TYPE_INT_ARGB_PRE) for performance (15% faster than standard RGBA images (TYPE_INT_ARGB):
final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);

// Create the MarlinGraphics2D wrapping your image's Graphics2D implementation:
final MarlinGraphics2D g2d = new MarlinGraphics2D(image);

// Use g2d to perform java2d operations (draw, fill) as usual
//g2d.setColor(...);
//g2d.setComposite(...);
//g2d.draw(shape);
//g2d.fill(shape);

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
