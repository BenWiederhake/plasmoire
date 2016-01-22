# plasmoire

This is a simple viewer for what I used to call "plasma". This is
essentially a mapping from "distance to center" to a single grey-value, using
this weird approach:

    sin(pow(dist², distortion))

Now the funny thing is: it should be perfectly rotation-symmetric, since we
only use the distance, right? But it isn't. The core "weirdness" comes the
fact that if the derivative of pow(...) is exactly 2*Math.PI, then the
grey-value doesn't change. Thus, it is only 90°-rotation symmetric. Since I
don't want that, I further modified the expression so that it is highly
irregular despite looking regular.

Here's how it looks like:

![Screenshot after start](https://raw.githubusercontent.com/BenWiederhake/plasmoire/master/screnshot.png)

Once you understand the core, it's actually pretty straight-forward to find a
parameterization that allows one to choose the length until the first such
"pole", which is exactly what the GUI allows you to do.

The GUI currently allows you to:

- move around by dragging the mouse
- play around with distortion / pole length
- save it to a file, using the same upper left corner and a specified
width/height (great for wallpapers!)

This class is written in a semi-modular way. If you want to re-use core
drawing algorithm, you could either copy the last 30 lines (yes, it's that
short!), or call Plasmore.draw(). Everything else is necessary because GUI.

Note that the concept of "zooming in" does not make any sense with this
pattern, since the pattern *does not really exist*. Thus, if you were to zoom
in, you could see the variation between the sampled points, which would
immediately break the moire-ness, which is the great thing about this
pattern.

Have fun!

[!]
