/*
 * Plasmoire, a viewer for phase-aligned plasma/moire images
 * Copyright (C) Ben Wiederhake, 2016
 * Released to the Public Domain
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/*
 * "Plasmoire" is a simple viewer for what I used to call "plasma". This is
 * essentially a mapping from "distance to center" to a single grey-value, using
 * this weird approach:
 *
 * sin(pow(dist, distortion))
 *
 * Now the funny thing is: it should be perfectly rotation-symmetric, since we
 * only use the distance, right? But it isn't. The core "weirdness" comes the
 * fact that if the derivative of pow(...) is exactly 2*Math.PI, then the
 * grey-value doesn't change. Thus, it is only 90°-rotation symmetric. Since I
 * don't want that, I further modified the expression so that it is highly
 * irregular despite looking regular.
 *
 * Once you understand the core, it's actually pretty straight-forward to find a
 * parameterization that allows one to choose the length until the first such
 * "pole", which is exactly what the GUI allows you to do.
 *
 * The GUI currently allows you to:
 * - move around by dragging the mouse
 * - play around with distortion / pole length
 * - save it to a file, using the same upper left corner and a specified
 * width/height (great for wallpapers!)
 *
 * This class is written in a semi-modular way. If you want to re-use core
 * drawing algorithm, you could either copy the last 30 lines (yes, it's that
 * short!), or call Plasmore.draw(). Everything else is necessary because GUI.
 *
 * Note that the concept of "zooming in" does not make any sense with this
 * pattern, since the pattern *does not really exist*. Thus, if you were to zoom
 * in, you could see the variation between the sampled points, which would
 * immediately break the moire-ness, which is the great thing about this
 * pattern.
 *
 * Have fun!
 */

/* == Overly verbose bullshit to do GUI in Java == */

final class Parameter extends Box {
    /** Not meant for serialization. */
    private static final long serialVersionUID = 1L;

    private final JSpinner spinner;

    private final SpinnerNumberModel numModel;

    public Parameter(final String text, final SpinnerNumberModel numModel) {
        super(BoxLayout.X_AXIS);
        this.numModel = numModel;
        final JLabel label = new JLabel(text);
        add(label);
        add(Box.createHorizontalGlue());
        spinner = new JSpinner(numModel);
        add(spinner);
        final Dimension dim = getMaximumSize();
        /*
         * I would use .getHeight(), but that returns a double, for whatever
         * reason.
         */
        dim.height = Math.max(label.getPreferredSize().height,
            spinner.getPreferredSize().height);
        setMaximumSize(dim);
    }

    public void addChangeListener(final ChangeListener listener) {
        spinner.addChangeListener(listener);
    }

    public Number getValue() {
        return numModel.getNumber();
    }
}

final class JRestrictedSeparator extends JSeparator {
    /** Not meant for serialization. */
    private static final long serialVersionUID = 1L;

    public JRestrictedSeparator() {
        super();
        final Dimension max = getMaximumSize();
        /* Make it work well with BoxLayout */
        max.height = getPreferredSize().height;
        setMaximumSize(max);
    }
}

public final class Plasmoire extends JComponent {
    public static final int INIT_POLE_DIST = 100;

    public static final double INIT_SCALE = 1.3;

    public static final int MARGIN = 10;

    /** Not meant for serialization. */
    private static final long serialVersionUID = 1L;

    private int firstPoleDist = INIT_POLE_DIST;

    private double distortion = INIT_SCALE;

    private int startX = -2 * INIT_POLE_DIST;

    private int startY = -2 * INIT_POLE_DIST;

    public Plasmoire() {
        /* Nothing to do here */
    }

    public int getFirstPoleDistance() {
        return firstPoleDist;
    }

    public void setFirstPoleDistance(final int firstPoleDist) {
        this.firstPoleDist = firstPoleDist;
        repaint();
    }

    public double getDistortion() {
        return distortion;
    }

    public void setDistortion(final double scale) {
        this.distortion = scale;
        repaint();
    }

    public int getStartX() {
        return startX;
    }

    public void setStartX(final int startX) {
        this.startX = startX;
        repaint();
    }

    public int getStartY() {
        return startY;
    }

    public void setStartY(final int startY) {
        this.startY = startY;
        repaint();
    }

    @Override
    protected void paintComponent(final Graphics g) {
        final BufferedImage img = draw(
            getWidth(), getHeight(),
            startX, startY,
            firstPoleDist, distortion);
        g.drawImage(img, 0, 0, null);
    }

    /* Oh come on. */
    private static final class DragHandler extends MouseAdapter {
        private final Plasmoire plasmoire;

        private int lastX = 0;

        private int lastY = 0;

        public DragHandler(final Plasmoire plasmoire) {
            this.plasmoire = plasmoire;
        }

        public void register() {
            plasmoire.addMouseListener(this);
            plasmoire.addMouseMotionListener(this);
        }

        /*
         * Why are these even separate? I mean, it doesn't often make sense to
         * know *that* there was drag without knowing *how much*.
         */
        @Override
        public void mousePressed(final MouseEvent e) {
            lastX = e.getX();
            lastY = e.getY();
        }

        @Override
        public void mouseDragged(final MouseEvent e) {
            final int diffX = e.getX() - lastX;
            final int diffY = e.getY() - lastY;
            lastX = e.getX();
            lastY = e.getY();
            plasmoire.setStartX(plasmoire.getStartX() - diffX);
            plasmoire.setStartY(plasmoire.getStartY() - diffY);
        }
    }

    public static void main(final String[] args) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                final JFrame win = new JFrame("Plasmoire - parameterized plasma"
                    + " like I knew them from age 8 or so");
                final JPanel content =
                    new JPanel(new BorderLayout(MARGIN, MARGIN));
                content.setBorder(new EmptyBorder(
                    MARGIN, MARGIN, MARGIN, MARGIN));
                win.setContentPane(content);

                final Plasmoire plasmoire = new Plasmoire();
                new DragHandler(plasmoire).register();
                content.add(plasmoire);

                /* == Behold! The sidebar! == */
                final Box sidebar = Box.createVerticalBox();
                {
                    final Parameter p = new Parameter("Pole distance:",
                        new SpinnerNumberModel(INIT_POLE_DIST, 10, 1000, 10));
                    p.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(final ChangeEvent e) {
                            plasmoire.setFirstPoleDistance(
                                p.getValue().intValue());
                        }
                    });
                    sidebar.add(p);
                }
                sidebar.add(Box.createRigidArea(new Dimension(0, MARGIN)));
                {
                    final Parameter p = new Parameter("Distortion:",
                        new SpinnerNumberModel(INIT_SCALE, 0.7, 2.5, 0.1));
                    // TODO: Maybe change from "factor" to "log(factor)" ?
                    p.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(final ChangeEvent e) {
                            plasmoire.setDistortion(p.getValue().doubleValue());
                        }
                    });
                    sidebar.add(p);
                }
                sidebar.add(Box.createRigidArea(new Dimension(0, MARGIN)));
                sidebar.add(new JRestrictedSeparator());
                sidebar.add(Box.createRigidArea(new Dimension(0, MARGIN)));
                {
                    final Parameter fW = new Parameter("File width:",
                        new SpinnerNumberModel(1920, 320, 9999, 1));
                    sidebar.add(fW);
                    sidebar.add(Box.createRigidArea(new Dimension(0, MARGIN)));
                    final Parameter fH = new Parameter("File width:",
                        new SpinnerNumberModel(1080, 200, 9999, 1));
                    sidebar.add(fH);
                    sidebar.add(Box.createRigidArea(new Dimension(0, MARGIN)));
                    final JButton btnFile = new JButton("Draw to file");
                    sidebar.add(btnFile);
                    final JFileChooser jfc = new JFileChooser();
                    btnFile.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            jfc.setDialogTitle("Save Plasmoire to file");
                            final int ret = jfc.showSaveDialog(win);
                            if (ret != JFileChooser.APPROVE_OPTION) {
                                return;
                            }
                            final File file = jfc.getSelectedFile();
                            if (file.exists()) {
                                JOptionPane.showMessageDialog(win,
                                    "Don't want to overwrite existing file.",
                                    "Plasmoire: couldn't write",
                                    JOptionPane.ERROR_MESSAGE);
                            }
                            final BufferedImage img = draw(
                                fW.getValue().intValue(),
                                fH.getValue().intValue(),
                                plasmoire.getStartX(),
                                plasmoire.getStartY(),
                                plasmoire.getFirstPoleDistance(),
                                plasmoire.getDistortion());
                            try {
                                ImageIO.write(img, "png", file);
                            } catch (final IOException ex) {
                                JOptionPane.showMessageDialog(win,
                                    "Some write error occurred:\n"
                                        + ex.getLocalizedMessage(),
                                    "Plasmore: couldn't write",
                                    JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    });
                }
                sidebar.add(Box.createVerticalGlue());
                content.add(sidebar, BorderLayout.EAST);
                /* == End of sidebar == */

                win.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                win.setMinimumSize(new Dimension(300, 200));
                win.setPreferredSize(new Dimension(800, 600));
                win.pack();
                win.setVisible(true);
            }
        });
    }

    /* == Actual functionality == */

    public static BufferedImage draw(final int width, final int height,
    final int startX, final int startY,
    final double firstPoleDist, final double distort) {
        final int[] pixels = new int[width * height];
        /* Make it so that the derivative is exactly 2pi at firstPoleDist */
        final double magic =
            Math.PI / (distort * Math.pow(firstPoleDist, 2 * distort - 1));
        int px = 0;
        final int maxX = startX + width;
        final int maxY = startY + height;
        for (int y = startY; y < maxY; ++y) {
            for (int x = startX; x < maxX; ++x) {
                /* Distance */
                /*
                 * The extra stuff is just to make sure there is no symmetry
                 * whatsoever, even though it looks highly regular. Where does
                 * the 1.618 come from? Simple, it's the Golden Ratio, it's the
                 * number that looks the least like a pattern. Why "+1"? Well,
                 * "dist" needs to stay positive (try it out!). Finally, there's
                 * always a factor of two because, well, that's dueto the
                 * interaction with 'magic'.
                 */
                final double dist =
                    x * x + y * y - x / 1.618 - 2 * y * 1.618 + 1;
                /* Basically: "sin of pow(dist, scale)" */
                final double d = Math.sin(Math.pow(dist, distort) * magic);
                /* Conversion of [0,1) to a color */
                int val = (int) ((d + 1) * 128);
                val = Math.min(255, Math.max(0, val));
                val *= 0x010101;
                pixels[px++] = val;
                /*
                 * So yeah, the pattern you see actually *does not exist*. All
                 * the magic is due to the derivative being exactly 2pi at
                 * several points, and when that happens, then a change of 1
                 * (integer step) means the color stays exactly the same.
                 */
            }
        }
        final BufferedImage img =
            new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }
}
