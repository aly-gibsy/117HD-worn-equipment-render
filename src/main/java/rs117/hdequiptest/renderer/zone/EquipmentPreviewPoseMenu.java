package rs117.hdequiptest.renderer.zone;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import rs117.hdequiptest.HdPluginConfig;
import rs117.hdequiptest.config.EquipmentPreviewPose;

/**
 * A dropdown at the top of the equipment preview for picking what the character does.
 * <p>
 * The button sits in a strip above the model, so the model never covers it. The open list overlaps the model,
 * so the renderer masks the model out of {@link #listBounds()} while it's open.
 * Mouse events arrive on the AWT thread and drawing happens on the client thread, so shared state is volatile.
 */
@Singleton
public class EquipmentPreviewPoseMenu extends Overlay implements MouseWheelListener {
	static final int BUTTON_HEIGHT = 16;
	static final int ROW_HEIGHT = 14;
	static final int INSET = 3;
	/** Height taken from the top of the preview area for the button */
	static final int STRIP_HEIGHT = BUTTON_HEIGHT + 2 * INSET;
	private static final EquipmentPreviewPose[] POSES = EquipmentPreviewPose.values();

	private static final Color BACKGROUND = new Color(0x3E, 0x35, 0x29, 235);
	private static final Color BACKGROUND_HOVER = new Color(0x6A, 0x5A, 0x44, 245);
	private static final Color BORDER = new Color(0x0E, 0x0E, 0x0C);
	private static final Color TEXT = new Color(0xFF, 0x98, 0x1F);
	private static final Color TEXT_SELECTED = Color.WHITE;

	@Inject
	private HdPluginConfig config;

	/** The whole preview area in canvas coordinates, or null when the interface isn't open */
	@Nullable
	private volatile Rectangle area;
	private volatile boolean open;
	private volatile int scroll;
	private volatile int hoveredRow = -1;
	private boolean pressConsumed;

	public EquipmentPreviewPoseMenu() {
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	void setArea(@Nullable Rectangle area) {
		this.area = area;
		if (area == null)
			open = false;
	}

	/**
	 * @return where the model goes: the preview area below the button strip, or null when there's no room
	 */
	@Nullable
	Rectangle modelBounds() {
		Rectangle area = this.area;
		return area == null ? null : modelBounds(area);
	}

	/**
	 * @return the open list's bounds in canvas coordinates, or null when it's closed
	 */
	@Nullable
	Rectangle listBounds() {
		Rectangle area = this.area;
		return area == null || !open ? null : listBounds(area, POSES.length);
	}

	@Nullable
	static Rectangle modelBounds(Rectangle area) {
		int height = area.height - STRIP_HEIGHT;
		return height <= 0 ? null : new Rectangle(area.x, area.y + STRIP_HEIGHT, area.width, height);
	}

	static Rectangle buttonBounds(Rectangle area) {
		return new Rectangle(area.x + INSET, area.y + INSET, Math.max(0, area.width - 2 * INSET), BUTTON_HEIGHT);
	}

	/**
	 * The list hangs below the button and shows as many rows as fit in the preview area, scrolling the rest.
	 */
	static Rectangle listBounds(Rectangle area, int rowCount) {
		Rectangle button = buttonBounds(area);
		int top = button.y + button.height;
		int rows = visibleRows(area, rowCount);
		return new Rectangle(button.x, top, button.width, rows * ROW_HEIGHT);
	}

	static int visibleRows(Rectangle area, int rowCount) {
		Rectangle button = buttonBounds(area);
		int available = area.y + area.height - INSET - (button.y + button.height);
		return Math.max(1, Math.min(rowCount, available / ROW_HEIGHT));
	}

	static int clampScroll(int scroll, int rowCount, int visibleRows) {
		return Math.max(0, Math.min(scroll, rowCount - visibleRows));
	}

	/**
	 * @return the index of the row under the point, or -1 if it's outside the list
	 */
	static int rowAt(Rectangle area, int rowCount, int scroll, int x, int y) {
		Rectangle list = listBounds(area, rowCount);
		if (!list.contains(x, y))
			return -1;
		int row = scroll + (y - list.y) / ROW_HEIGHT;
		return row < rowCount ? row : -1;
	}

	/**
	 * Handles a press before anything else sees it.
	 *
	 * @return true if the press was for the dropdown, and has been consumed
	 */
	boolean mousePressed(MouseEvent e) {
		pressConsumed = false;
		Rectangle area = this.area;
		if (area == null)
			return false;

		if (buttonBounds(area).contains(e.getPoint())) {
			if (!open)
				scrollToSelection(area);
			open = !open;
			return consumePress(e);
		}

		if (!open)
			return false;

		int row = rowAt(area, POSES.length, scroll, e.getX(), e.getY());
		open = false;
		if (row >= 0) {
			config.setEquipmentPreviewPose(POSES[row]);
			return consumePress(e);
		}
		// Clicking anywhere else closes the list. Inside the preview that's all it does
		return area.contains(e.getPoint()) && consumePress(e);
	}

	private boolean consumePress(MouseEvent e) {
		pressConsumed = true;
		e.consume();
		return true;
	}

	/**
	 * @return true if this is the release of a press the dropdown consumed
	 */
	boolean mouseReleased(MouseEvent e) {
		if (!pressConsumed)
			return false;
		pressConsumed = false;
		e.consume();
		return true;
	}

	void mouseMoved(MouseEvent e) {
		Rectangle area = this.area;
		hoveredRow = area == null || !open ? -1 : rowAt(area, POSES.length, scroll, e.getX(), e.getY());
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e) {
		Rectangle area = this.area;
		if (area == null || !open || !listBounds(area, POSES.length).contains(e.getPoint()))
			return e;
		scroll = clampScroll(scroll + e.getWheelRotation(), POSES.length, visibleRows(area, POSES.length));
		hoveredRow = rowAt(area, POSES.length, scroll, e.getX(), e.getY());
		e.consume();
		return e;
	}

	private void scrollToSelection(Rectangle area) {
		int rows = visibleRows(area, POSES.length);
		int selected = config.equipmentPreviewPose().ordinal();
		scroll = clampScroll(selected - rows / 2, POSES.length, rows);
	}

	@Override
	public Dimension render(Graphics2D g) {
		Rectangle area = this.area;
		if (area == null)
			return null;

		g.setFont(FontManager.getRunescapeSmallFont());
		g.setStroke(new BasicStroke(1));
		EquipmentPreviewPose selected = config.equipmentPreviewPose();

		Rectangle button = buttonBounds(area);
		drawBox(g, button, open ? BACKGROUND_HOVER : BACKGROUND);
		drawText(g, selected.toString(), button, TEXT_SELECTED, button.width - 14);
		drawArrow(g, button);

		if (open) {
			Rectangle list = listBounds(area, POSES.length);
			int rows = visibleRows(area, POSES.length);
			int first = clampScroll(scroll, POSES.length, rows);
			drawBox(g, list, BACKGROUND);
			for (int i = 0; i < rows; i++) {
				int index = first + i;
				Rectangle row = new Rectangle(list.x, list.y + i * ROW_HEIGHT, list.width, ROW_HEIGHT);
				if (index == hoveredRow) {
					g.setColor(BACKGROUND_HOVER);
					g.fillRect(row.x + 1, row.y, row.width - 1, row.height);
				}
				EquipmentPreviewPose pose = POSES[index];
				drawText(g, pose.toString(), row, pose == selected ? TEXT_SELECTED : TEXT, row.width - 6);
			}
			if (rows < POSES.length)
				drawScrollbar(g, list, first, rows);
		}
		return null;
	}

	private static void drawBox(Graphics2D g, Rectangle r, Color background) {
		g.setColor(background);
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(BORDER);
		g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
	}

	private static void drawText(Graphics2D g, String text, Rectangle r, Color color, int maxWidth) {
		FontMetrics metrics = g.getFontMetrics();
		while (text.length() > 1 && metrics.stringWidth(text) > maxWidth)
			text = text.substring(0, text.length() - 1);
		int baseline = r.y + (r.height + metrics.getAscent() - metrics.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, r.x + 5, baseline + 1);
		g.setColor(color);
		g.drawString(text, r.x + 4, baseline);
	}

	private void drawArrow(Graphics2D g, Rectangle button) {
		int cx = button.x + button.width - 8;
		int cy = button.y + button.height / 2;
		Polygon arrow = open ?
			new Polygon(new int[] { cx - 3, cx + 4, cx }, new int[] { cy + 2, cy + 2, cy - 2 }, 3) :
			new Polygon(new int[] { cx - 3, cx + 4, cx }, new int[] { cy - 2, cy - 2, cy + 2 }, 3);
		g.setColor(TEXT_SELECTED);
		g.fillPolygon(arrow);
	}

	private static void drawScrollbar(Graphics2D g, Rectangle list, int first, int rows) {
		int trackHeight = list.height - 2;
		int thumbHeight = Math.max(6, trackHeight * rows / POSES.length);
		int thumbY = list.y + 1 + (trackHeight - thumbHeight) * first / Math.max(1, POSES.length - rows);
		g.setColor(BACKGROUND_HOVER);
		g.fillRect(list.x + list.width - 4, thumbY, 3, thumbHeight);
	}
}
