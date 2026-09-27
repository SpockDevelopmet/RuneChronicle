package com.runechronicle;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;

final class RecapOverlay extends Overlay {
 private final RuneChroniclePlugin plugin; private final Client client; private int trackerX=76;
 @Inject RecapOverlay(RuneChroniclePlugin plugin, Client client){this.plugin=plugin;this.client=client;setPosition(OverlayPosition.TOP_CENTER);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(OverlayPriority.LOW);setMovable(true);setResettable(true);}
 int trackerStartX(){return trackerX;}
 @Override public Dimension render(Graphics2D g){if(!plugin.config().showGameRecapButton()||!plugin.trackingActive())return null;Font old=g.getFont();g.setFont(new Font("SansSerif",Font.BOLD,11));FontMetrics fm=g.getFontMetrics();String recap="RECAP",track=plugin.expeditionActive()?"STOP TRACKING":"START TRACKING";int h=28,rw=fm.stringWidth(recap)+31,tw=fm.stringWidth(track)+20;trackerX=rw+5;int w=trackerX+tw;java.awt.Rectangle b=getBounds();net.runelite.api.Point mp=client.getMouseCanvasPosition();int lx=(b!=null&&mp!=null)?mp.getX()-b.x:-1,ly=(b!=null&&mp!=null)?mp.getY()-b.y:-1;boolean rh=lx>=0&&lx<rw&&ly>=0&&ly<h,th=lx>=trackerX&&lx<w&&ly>=0&&ly<h;
  draw(g,0,rw,recap,rh,true);draw(g,trackerX,tw,track,th,false);g.setFont(old);return new Dimension(w,h);}
 private void draw(Graphics2D g,int x,int w,String text,boolean hover,boolean recap){g.setColor(hover?new Color(28,24,16,240):new Color(9,12,16,225));g.fillRoundRect(x,0,w,28,7,7);g.setColor(hover?new Color(242,187,59):new Color(119,91,34));g.drawRoundRect(x,0,w-1,27,7,7);if(recap){g.setColor(new Color(242,187,59));g.drawString("◆",x+8,18);g.setColor(new Color(244,246,249));g.drawString(text,x+20,18);}else{g.setColor(plugin.expeditionActive()?new Color(235,112,112):new Color(242,187,59));g.drawString(text,x+10,18);}}
}
