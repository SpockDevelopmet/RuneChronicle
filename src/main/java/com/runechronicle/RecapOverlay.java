package com.runechronicle;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;

final class RecapOverlay extends Overlay {
 private final RuneChroniclePlugin plugin;
 private final Client client;
 @Inject RecapOverlay(RuneChroniclePlugin plugin, Client client){this.plugin=plugin;this.client=client;setPosition(OverlayPosition.TOP_RIGHT);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(OverlayPriority.LOW);setMovable(true);setResettable(true);}
 @Override public Dimension render(Graphics2D g){if(!plugin.config().showGameRecapButton()||!plugin.trackingActive())return null;String text="RECAP";Font old=g.getFont();g.setFont(new Font("SansSerif",Font.BOLD,11));FontMetrics fm=g.getFontMetrics();int w=fm.stringWidth(text)+28,h=28;java.awt.Rectangle b=getBounds();net.runelite.api.Point mp=client.getMouseCanvasPosition();boolean hover=b!=null&&mp!=null&&b.contains(mp.getX(),mp.getY());g.setColor(hover?new Color(28,24,16,240):new Color(9,12,16,225));g.fillRoundRect(0,0,w,h,7,7);g.setColor(hover?new Color(242,187,59):new Color(119,91,34));g.drawRoundRect(0,0,w-1,h-1,7,7);g.setColor(new Color(242,187,59));g.drawString("◆",8,18);g.setColor(new Color(244,246,249));g.drawString(text,20,18);g.setFont(old);return new Dimension(w,h);}
}
