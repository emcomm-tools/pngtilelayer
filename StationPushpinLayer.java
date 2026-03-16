package org.ka2ddo.yaac.gui.tile;

import com.bbn.openmap.Layer;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.event.MapMouseListener;
import com.bbn.openmap.event.ProjectionEvent;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.proj.coords.UTMPoint;

import org.ka2ddo.aprs.ObjectReport;
import org.ka2ddo.aprs.PositionMessage;
import org.ka2ddo.ax25.AX25Message;
import org.ka2ddo.yaac.ax25.Age;
import org.ka2ddo.yaac.ax25.StationState;
import org.ka2ddo.yaac.ax25.StationTracker;
import org.ka2ddo.yaac.ax25.TrackerListener;
import org.ka2ddo.yaac.gui.GeographicalMap;
import org.ka2ddo.yaac.gui.GeoMapGuiIfc2;
import org.ka2ddo.yaac.gui.StationRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Map layer that draws colored pushpin markers with callsign labels
 * at each tracked station position, plus BluMap-style breadcrumb trails
 * showing the last 3 historical positions with graduated styling.
 * <p>
 * Z-order 55: above StationRenderer (50), below NWSZone (60).
 * Toggled via "Show Pushpin Markers" in the Map Sources dialog.
 * <p>
 * Trail rendering (BluMap style):
 * <ul>
 *   <li>Only last 3 historical positions are shown (plus current pushpin)</li>
 *   <li>Newest segment: solid line, filled breadcrumb</li>
 *   <li>Middle segment: dashed line, outline-only breadcrumb (alpha 0.6)</li>
 *   <li>Oldest segment: dashed line, outline-only breadcrumb (alpha 0.35)</li>
 * </ul>
 * <p>
 * Click on any pushpin or breadcrumb to see station info popup (callsign,
 * time received, UTM position).
 * <p>
 * When enabled, hides StationRenderer via setVisible(false) to suppress
 * APRS symbols, and delegates mouse events through StationRenderer's
 * listener for map panning and station click handling.
 */
public class StationPushpinLayer extends Layer
        implements TrackerListener, MapMouseListener {

    // --- Pushpin colors ---
    private static final Color PUSHPIN_COLOR = new Color(220, 40, 40);
    private static final Color PUSHPIN_BORDER = Color.DARK_GRAY;
    private static final Color PUSHPIN_HIGHLIGHT = Color.WHITE;
    private static final Color TEXT_COLOR = Color.BLACK;
    private static final Color TEXT_BG = new Color(255, 255, 255, 200);

    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Stroke STEM_STROKE = new BasicStroke(2.0f);

    // --- Trail strokes ---
    private static final Stroke TRAIL_STROKE = new BasicStroke(4.5f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke TRAIL_STROKE_DASHED = new BasicStroke(4.5f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
            new float[]{12.0f, 8.0f}, 0.0f);
    private static final Stroke CRUMB_STROKE_DASHED = new BasicStroke(1.5f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
            new float[]{6.0f, 4.0f}, 0.0f);

    // --- Pushpin dimensions ---
    private static final int PIN_HEIGHT = 24;
    private static final int PIN_RADIUS = 6;
    private static final int HIGHLIGHT_RADIUS = 2;
    private static final int LABEL_PAD_X = 3;
    private static final int LABEL_PAD_Y = 2;
    private static final int LABEL_OFFSET_X = 8;
    private static final int LABEL_OFFSET_Y = -4;
    private static final int LABEL_ARC = 6;

    // --- Breadcrumb triangle dimensions ---
    private static final int CRUMB_SIZE = 14;
    private static final int[] TRI_X = {0, -CRUMB_SIZE, CRUMB_SIZE};
    private static final int[] TRI_Y = {-CRUMB_SIZE, CRUMB_SIZE / 2, CRUMB_SIZE / 2};

    /** Max historical trail points to display (BluMap style) */
    private static final int MAX_TRAIL_POINTS = 3;

    /** Fallback max age for breadcrumb trail points (24 hours) */
    private static final long DEFAULT_TRAIL_AGE_MS = 86400000L;

    /** Hit radius for click detection on pushpins/breadcrumbs */
    private static final int HIT_RADIUS = 12;

    private static final SimpleDateFormat UTC_FORMAT;
    static {
        UTC_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final MapBean mapBean;
    private final GeoMapGuiIfc2 guiIfc;
    private boolean enabled = true;
    private StationColorManager colorManager;

    /** Stations hidden via the visibility dialog */
    private final Set<String> hiddenStations = new HashSet<>();

    /** Screen-space points from last paint, for click hit-testing */
    private final List<RenderedPoint> renderedPoints = new ArrayList<>();

    public StationPushpinLayer(MapBean mapBean, GeoMapGuiIfc2 guiIfc) {
        this.mapBean = mapBean;
        this.guiIfc = guiIfc;
        setName("Station Pushpins");
        StationTracker.getInstance().addTrackerListener(this);
        syncStationRendererVisibility();
    }

    public void setColorManager(StationColorManager mgr) {
        this.colorManager = mgr;
    }

    public StationColorManager getColorManager() {
        return colorManager;
    }

    // --- Station visibility ---

    public boolean isStationVisible(String callsign) {
        return !hiddenStations.contains(callsign.toUpperCase().trim());
    }

    public void setStationVisible(String callsign, boolean visible) {
        String key = callsign.toUpperCase().trim();
        if (visible) {
            hiddenStations.remove(key);
        } else {
            hiddenStations.add(key);
        }
        repaint();
    }

    // --- MapMouseListener ---

    @Override
    public MapMouseListener getMapMouseListener() {
        return this;
    }

    @Override
    public String[] getMouseModeServiceList() {
        return new String[]{"Gestures"};
    }

    @Override
    public boolean mouseClicked(MouseEvent e) {
        if (enabled && e.getButton() == MouseEvent.BUTTON1
                && e.getClickCount() == 1) {
            RenderedPoint hit = findHitPoint(e.getX(), e.getY());
            if (hit != null) {
                showPointPopup(e.getComponent(), e.getX(), e.getY(), hit);
                return true;
            }
        }
        MapMouseListener delegate = getStationRendererListener();
        if (delegate != null) {
            return delegate.mouseClicked(e);
        }
        return false;
    }

    @Override
    public boolean mousePressed(MouseEvent e) {
        MapMouseListener delegate = getStationRendererListener();
        return delegate != null && delegate.mousePressed(e);
    }

    @Override
    public boolean mouseReleased(MouseEvent e) {
        MapMouseListener delegate = getStationRendererListener();
        return delegate != null && delegate.mouseReleased(e);
    }

    @Override
    public boolean mouseEntered(MouseEvent e) {
        MapMouseListener delegate = getStationRendererListener();
        return delegate != null && delegate.mouseEntered(e);
    }

    @Override
    public boolean mouseExited(MouseEvent e) {
        MapMouseListener delegate = getStationRendererListener();
        return delegate != null && delegate.mouseExited(e);
    }

    @Override
    public boolean mouseDragged(MouseEvent e) {
        MapMouseListener delegate = getStationRendererListener();
        return delegate != null && delegate.mouseDragged(e);
    }

    @Override
    public boolean mouseMoved(MouseEvent e) {
        MapMouseListener delegate = getStationRendererListener();
        return delegate != null && delegate.mouseMoved(e);
    }

    @Override
    public void mouseMoved() {
        MapMouseListener delegate = getStationRendererListener();
        if (delegate != null) {
            delegate.mouseMoved();
        }
    }

    private MapMouseListener getStationRendererListener() {
        if (!(guiIfc instanceof GeographicalMap)) return null;
        StationRenderer sr = ((GeographicalMap) guiIfc).getStationRenderer();
        if (sr != null) {
            return sr.getMapMouseListener();
        }
        return null;
    }

    // --- Painting ---

    @Override
    public void paint(Graphics g) {
        if (!enabled) return;

        Projection proj = getProjection();
        if (proj == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int viewWidth = mapBean.getWidth();
        int viewHeight = mapBean.getHeight();

        renderedPoints.clear();

        StationState[] stations = StationTracker.getInstance()
                .getCurrentTrackedObjectArray();
        if (stations == null) return;

        for (StationState ss : stations) {
            if (ss == null) continue;

            if (hiddenStations.contains(
                    ss.getIdentifier().toUpperCase().trim())) {
                continue;
            }

            double lat = ss.getLatitude();
            double lon = ss.getLongitude();
            if (lat == 0.0 && lon == 0.0) continue;

            Color stationColor = colorManager != null
                    ? colorManager.getColor(ss.getIdentifier())
                    : PUSHPIN_COLOR;

            List<double[]> trail = buildTrail(ss);

            if (trail.size() > 1) {
                renderTrail(g2, proj, trail, viewWidth, viewHeight,
                        stationColor, ss.getIdentifier());
            }

            Point2D screenPt = proj.forward(lat, lon);
            float px = (float) screenPt.getX();
            float py = (float) screenPt.getY();

            if (px >= -100 && px <= viewWidth + 100 &&
                py >= -100 && py <= viewHeight + 100) {
                renderPushpin(g2, px, py, ss.getIdentifier(), stationColor);
                renderedPoints.add(new RenderedPoint(
                        px, py, ss.getIdentifier(), lat, lon, 0L, true));
            }
        }
    }

    /**
     * Build a trail of positions from the station's message history.
     * Returns list ordered oldest-first, with current position as last entry.
     * Capped to last {@link #MAX_TRAIL_POINTS} historical + 1 current.
     * Each entry is {lat, lon, rcptTime}.
     */
    private List<double[]> buildTrail(StationState ss) {
        List<double[]> trail = new ArrayList<>();
        long maxAge = DEFAULT_TRAIL_AGE_MS;
        if (guiIfc instanceof GeographicalMap) {
            maxAge = ((GeographicalMap) guiIfc).getMaxTrackDuration();
        }
        long cutoff = System.currentTimeMillis() - maxAge;
        String ident = ss.getIdentifier();

        for (int i = ss.size() - 1; i >= 1; i--) {
            AX25Message msg = ss.get(i);
            if (msg == null) continue;
            if (msg.rcptTime < cutoff) continue;

            if (msg instanceof PositionMessage) {
                if (msg instanceof ObjectReport) {
                    String objName = ((ObjectReport) msg).objectName;
                    if (objName != null && !objName.equals(ident)) {
                        continue;
                    }
                }
                PositionMessage pm = (PositionMessage) msg;
                double lat = pm.decodeLatitude();
                double lon = pm.decodeLongitude();
                if (lat != 0.0 || lon != 0.0) {
                    trail.add(new double[]{lat, lon, (double) msg.rcptTime});
                }
            }
        }

        // BluMap style: keep only the last MAX_TRAIL_POINTS historical positions
        if (trail.size() > MAX_TRAIL_POINTS) {
            trail = new ArrayList<>(trail.subList(
                    trail.size() - MAX_TRAIL_POINTS, trail.size()));
        }

        // Current position as last entry (rcptTime=0 sentinel)
        trail.add(new double[]{ss.getLatitude(), ss.getLongitude(), 0.0});
        return trail;
    }

    /**
     * Render BluMap-style breadcrumb trail with graduated styling.
     * <ul>
     *   <li>Newest segment (to current): solid line, filled breadcrumb</li>
     *   <li>Middle segment: dashed line, outline-only breadcrumb, alpha 0.6</li>
     *   <li>Oldest segment: dashed line, outline-only breadcrumb, alpha 0.35</li>
     * </ul>
     */
    private void renderTrail(Graphics2D g2, Projection proj,
                              List<double[]> trail, int viewWidth, int viewHeight,
                              Color pinColor, String callsign) {
        Stroke origStroke = g2.getStroke();
        int segCount = trail.size() - 1;

        float[] sx = new float[trail.size()];
        float[] sy = new float[trail.size()];
        for (int i = 0; i < trail.size(); i++) {
            double[] pt = trail.get(i);
            Point2D sp = proj.forward(pt[0], pt[1]);
            sx[i] = (float) sp.getX();
            sy[i] = (float) sp.getY();
        }

        for (int i = 0; i < segCount; i++) {
            int ageFromNewest = segCount - 1 - i;

            float alpha;
            Stroke lineStroke;
            Stroke crumbStroke;
            if (ageFromNewest == 0) {
                alpha = 1.0f;
                lineStroke = TRAIL_STROKE;
                crumbStroke = null;
            } else if (ageFromNewest == 1) {
                alpha = 0.6f;
                lineStroke = TRAIL_STROKE_DASHED;
                crumbStroke = CRUMB_STROKE_DASHED;
            } else {
                alpha = 0.35f;
                lineStroke = TRAIL_STROKE_DASHED;
                crumbStroke = CRUMB_STROKE_DASHED;
            }

            Color trailColor = new Color(pinColor.getRed(), pinColor.getGreen(),
                    pinColor.getBlue(), (int) (100 * alpha));
            Color crumbColor = new Color(pinColor.getRed(), pinColor.getGreen(),
                    pinColor.getBlue(), (int) (160 * alpha));

            g2.setColor(trailColor);
            g2.setStroke(lineStroke);
            g2.drawLine((int) sx[i], (int) sy[i],
                        (int) sx[i + 1], (int) sy[i + 1]);

            if (sx[i] >= -50 && sx[i] <= viewWidth + 50 &&
                sy[i] >= -50 && sy[i] <= viewHeight + 50) {

                double angle = Math.atan2(sy[i + 1] - sy[i],
                                           sx[i + 1] - sx[i]);
                double rotation = angle + Math.PI / 2.0;
                renderBreadcrumb(g2, sx[i], sy[i], rotation,
                        crumbColor, crumbStroke);

                double[] pt = trail.get(i);
                renderedPoints.add(new RenderedPoint(
                        sx[i], sy[i], callsign,
                        pt[0], pt[1], (long) pt[2], false));
            }
        }

        g2.setStroke(origStroke);
    }

    /**
     * Render a breadcrumb triangle marker.
     * @param outlineStroke if null, draw solid filled triangle (newest);
     *                      if non-null, draw dashed outline only (older)
     */
    private void renderBreadcrumb(Graphics2D g2, float px, float py,
                                   double rotation, Color crumbColor,
                                   Stroke outlineStroke) {
        AffineTransform origTransform = g2.getTransform();
        Stroke origStroke = g2.getStroke();
        g2.translate(px, py);
        g2.rotate(rotation);

        if (outlineStroke == null) {
            g2.setColor(crumbColor);
            g2.fillPolygon(TRI_X, TRI_Y, 3);
            g2.setColor(new Color(100, 100, 100, 180));
            g2.drawPolygon(TRI_X, TRI_Y, 3);
        } else {
            g2.setColor(crumbColor);
            g2.setStroke(outlineStroke);
            g2.drawPolygon(TRI_X, TRI_Y, 3);
        }

        g2.setStroke(origStroke);
        g2.setTransform(origTransform);
    }

    private void renderPushpin(Graphics2D g2, float px, float py,
                                String callsign, Color pinColor) {
        Stroke origStroke = g2.getStroke();

        g2.setColor(PUSHPIN_BORDER);
        g2.setStroke(STEM_STROKE);
        g2.drawLine((int) px, (int) py,
                    (int) px, (int) (py - PIN_HEIGHT));

        int headX = (int) (px - PIN_RADIUS);
        int headY = (int) (py - PIN_HEIGHT - PIN_RADIUS);
        int diameter = PIN_RADIUS * 2;
        g2.setColor(pinColor);
        g2.fillOval(headX, headY, diameter, diameter);

        g2.setColor(PUSHPIN_BORDER);
        g2.setStroke(origStroke);
        g2.drawOval(headX, headY, diameter, diameter);

        g2.setColor(PUSHPIN_HIGHLIGHT);
        int hlX = (int) (px - HIGHLIGHT_RADIUS);
        int hlY = (int) (py - PIN_HEIGHT - HIGHLIGHT_RADIUS - 1);
        g2.fillOval(hlX, hlY, HIGHLIGHT_RADIUS * 2, HIGHLIGHT_RADIUS * 2);

        if (callsign == null || callsign.isEmpty()) return;

        g2.setFont(LABEL_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(callsign);
        int textHeight = fm.getHeight();

        int labelX = (int) (px + LABEL_OFFSET_X);
        int labelY = (int) (py - PIN_HEIGHT + LABEL_OFFSET_Y - textHeight);

        g2.setColor(TEXT_BG);
        g2.fillRoundRect(labelX - LABEL_PAD_X,
                          labelY - LABEL_PAD_Y,
                          textWidth + LABEL_PAD_X * 2,
                          textHeight + LABEL_PAD_Y * 2,
                          LABEL_ARC, LABEL_ARC);

        g2.setColor(PUSHPIN_BORDER);
        g2.drawRoundRect(labelX - LABEL_PAD_X,
                          labelY - LABEL_PAD_Y,
                          textWidth + LABEL_PAD_X * 2,
                          textHeight + LABEL_PAD_Y * 2,
                          LABEL_ARC, LABEL_ARC);

        g2.setColor(TEXT_COLOR);
        g2.drawString(callsign, labelX, labelY + fm.getAscent());
    }

    // --- Click hit-testing and popup ---

    private RenderedPoint findHitPoint(int mx, int my) {
        for (int i = renderedPoints.size() - 1; i >= 0; i--) {
            RenderedPoint rp = renderedPoints.get(i);
            float tx = rp.screenX;
            float ty = rp.screenY;
            if (rp.isCurrent) {
                ty -= PIN_HEIGHT;
            }
            float dx = mx - tx;
            float dy = my - ty;
            if (dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS) {
                return rp;
            }
        }
        return null;
    }

    private void showPointPopup(Component invoker, int x, int y,
                                 RenderedPoint rp) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        panel.setBackground(Color.WHITE);

        JLabel callLabel = new JLabel(rp.callsign);
        callLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        callLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(callLabel);
        panel.add(Box.createVerticalStrut(4));

        if (rp.rcptTime > 0) {
            String timeStr;
            synchronized (UTC_FORMAT) {
                timeStr = UTC_FORMAT.format(new Date(rp.rcptTime));
            }
            String ageStr = new Age(
                    System.currentTimeMillis() - rp.rcptTime).toString();
            JLabel timeLabel = new JLabel(
                    "Time: " + timeStr + "  (" + ageStr + ")");
            timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(timeLabel);
        } else {
            JLabel timeLabel = new JLabel("Current position");
            timeLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(timeLabel);
        }

        panel.add(Box.createVerticalStrut(2));

        UTMPoint utm = new UTMPoint(
                new LatLonPoint.Double(rp.lat, rp.lon));
        String utmStr = utm.zone_number + "" + utm.zone_letter + " "
                + Math.round(utm.easting) + "E "
                + Math.round(utm.northing) + "N";
        JLabel utmLabel = new JLabel("UTM: " + utmStr);
        utmLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        utmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(utmLabel);

        popup.add(panel, BorderLayout.CENTER);
        popup.show(invoker, x, y);
    }

    // --- TrackerListener ---

    @Override
    public void stationAdded(StationState ss, int index) {
        repaint();
    }

    @Override
    public void stationUpdated(StationState ss) {
        repaint();
    }

    @Override
    public void stationDeleted(StationState ss, int index) {
        repaint();
    }

    @Override
    public void messageAdded(StationState ss, int index, AX25Message msg) {
        // no-op
    }

    @Override
    public void messageDeleted(StationState ss, int index, AX25Message msg) {
        // no-op
    }

    // --- Projection changes ---

    @Override
    public void projectionChanged(ProjectionEvent e) {
        setProjection(e);
        repaint();
    }

    // --- Enable/disable ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            syncStationRendererVisibility();
            repaint();
        }
    }

    private void syncStationRendererVisibility() {
        if (!(guiIfc instanceof GeographicalMap)) return;
        StationRenderer sr = ((GeographicalMap) guiIfc).getStationRenderer();
        if (sr != null) {
            sr.setVisible(!enabled);
        }
    }

    // --- RenderedPoint for hit-testing ---

    private static class RenderedPoint {
        final float screenX, screenY;
        final String callsign;
        final double lat, lon;
        final long rcptTime;
        final boolean isCurrent;

        RenderedPoint(float screenX, float screenY, String callsign,
                      double lat, double lon, long rcptTime,
                      boolean isCurrent) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.callsign = callsign;
            this.lat = lat;
            this.lon = lon;
            this.rcptTime = rcptTime;
            this.isCurrent = isCurrent;
        }
    }
}
