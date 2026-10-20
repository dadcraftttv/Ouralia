package app.pdscenes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The app icon drawn in Java2D (the window icon, and the large PNG for a store listing): the same shapes and numbers
 * as tools/make_icon.py, which writes the phone's icon and docs/icon/ouralia-icon.svg. Keep the two in step.
 * {@code java desktop/src/app/pdscenes/AppIcon.java <file.png> <size>} writes one PNG.
 */
final class AppIcon {
    private static final Color BG = new Color(0x0d0b13), VIOLET = new Color(0xa28bf9), TEAL = new Color(0x55e7a5);
    private static final double C = 54, R = 26, BAND_W = 6.5, CUP_W = 14, KNOB_A = 40, KNOB_R = 5, DOT_R = 10.5;

    private AppIcon() { }

    /** The icon at {@code size} px, on its dark background (square: a window manager rounds it if it wants to). */
    static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 108.0, size / 108.0);
        g.setColor(BG);
        g.fillRect(0, 0, 108, 108);
        arc(g, 236, 484, BAND_W, new Color(0xa2, 0x8b, 0xf9, Math.round(0.18f * 255)));
        double[] knob = pt(KNOB_A);
        g.setPaint(new GradientPaint((float) (C - R), (float) pt(236)[1], TEAL, (float) knob[0], (float) (C - R), VIOLET));
        arc(g, 236, 400, BAND_W, null);
        arc(g, 216, 240, CUP_W, TEAL);
        arc(g, 120, 144, CUP_W, VIOLET);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(knob[0] - KNOB_R, knob[1] - KNOB_R, 2 * KNOB_R, 2 * KNOB_R));
        g.setColor(VIOLET);
        g.fill(new Ellipse2D.Double(C - DOT_R, C - DOT_R, 2 * DOT_R, 2 * DOT_R));
        g.dispose();
        return img;
    }

    /** The sizes a window list and a task bar pick from. */
    static List<Image> windowImages() {
        List<Image> out = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            out.add(image(s));
        }
        return out;
    }

    /** A point on the ring, {@code a} degrees clockwise from the top. */
    private static double[] pt(double a) {
        double t = Math.toRadians(a);
        return new double[]{C + R * Math.sin(t), C - R * Math.cos(t)};
    }

    /** The ring from a0 to a1 (degrees clockwise from the top), round-capped, in {@code color} (null: the paint set). */
    private static void arc(Graphics2D g, double a0, double a1, double width, Color color) {
        if (color != null) {
            g.setColor(color);
        }
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Arc2D: angles counter-clockwise from 3 o'clock
        g.draw(new Arc2D.Double(C - R, C - R, 2 * R, 2 * R, 90 - a0, -(a1 - a0), Arc2D.OPEN));
    }

    public static void main(String[] args) throws Exception {
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        javax.imageio.ImageIO.write(image(size), "png", new File(args.length > 0 ? args[0] : "icon.png"));
    }
}
