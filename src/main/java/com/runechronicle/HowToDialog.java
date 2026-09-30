package com.runechronicle;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

final class HowToDialog extends JDialog {
 private static final Color BG=new Color(8,10,13),CARD=new Color(17,20,25),LINE=new Color(47,53,62),GOLD=new Color(242,187,59),WHITE=new Color(242,245,248),MUTED=new Color(151,160,173),GREEN=new Color(83,207,137);
 HowToDialog(Component owner){super(SwingUtilities.getWindowAncestor(owner),"RuneChronicle · How to Use",Dialog.ModalityType.MODELESS);setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(680,720);setMinimumSize(new Dimension(560,500));setLocationRelativeTo(owner);
  JPanel root=new JPanel();root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS));root.setBackground(BG);root.setBorder(new EmptyBorder(22,24,24,24));
  root.add(lab("HOW TO USE RUNECHRONICLE",24,WHITE,Font.BOLD));root.add(lab("A simple guide to what RuneChronicle remembers and when you need to press something.",10,MUTED,Font.PLAIN));root.add(Box.createVerticalStrut(14));
  root.add(card("1  PLAY NORMALLY","Most of RuneChronicle is automatic. XP, levels, attributed monster/boss kills, claimed loot and supported milestones are recorded while you play. You do not need to start Profit Tracker for the normal Chronicle.","● LIVE means the current session baseline is ready."));
  root.add(card("2  SESSION / TODAY / WEEK / MONTH","These tabs only change the time window you are looking at. Session is this login session. Today, Week and Month use history saved on this computer.","Click a metric such as Loot Value or XP Earned for its breakdown."));
  root.add(card("3  OPEN RECAP","Use Open Recap when you want the larger story-style summary. It does not start or stop recording.","Recap includes Session, Today, Week, Month and Custom views."));
  root.add(card("4  PROFIT TRACKER","Press START TRACKING immediately before a boss or monster trip when you want RuneChronicle to calculate Loot − Supplies = Net Profit. Press STOP TRACKING when the trip is finished.","Use it for run-by-run profit analysis; normal Chronicle recording continues independently."));
  root.add(card("5  SUPPLIES","While Profit Tracker is active, RuneChronicle conservatively records recognized consumed food, potion doses, ammo and other supported supplies. Always Tracked Supplies lets you explicitly include an item.","Banking, equipping and ordinary inventory movement should not be treated as consumption."));
  root.add(card("6  PROFIT HISTORY","Completed non-empty tracking runs stay in Profit History. Open a run for loot, supplies, net profit, profit/kill and profit/hour. Monster History combines tracked runs for a searched monster.","Use DELETE RUN inside a run only when you really want that run removed."));
  root.add(card("7  LOOT VALUE FILTER","RuneChronicle normally starts at a 1 gp minimum GE value, which means almost all valued claimed loot can be recorded. If you raise the GE/High Alch minimums in Settings, cheaper drops may intentionally disappear from the normal Chronicle.","This display/recording filter does not reduce Profit Tracker math during an active tracked run."));
  root.add(card("8  LOOT + HIDDEN LOOT","Open Loot Breakdown to see recorded loot. HIDE removes an item only from the current breakdown. ALWAYS HIDE keeps that item out of future breakdown views. Hidden value is still shown and still counts toward the total/profit.","Use MANAGE HIDDEN LOOT to search and unhide permanently hidden items."));
  root.add(card("9  DROP HISTORY","Search the OSRS item catalog to find loot RuneChronicle has actually recorded for your account. An item existing in RuneScape does not mean RuneChronicle will invent old drops.","History begins when RuneChronicle starts tracking."));
  root.add(card("10  MORE THAN ONE COMPUTER","RuneChronicle history is local to each computer right now. Logging into the same RuneScape account on another device does not automatically transfer Chronicle history.","Use Export and Import when you intentionally want to move a Chronicle backup between computers."));
  root.add(card("11  PRIVACY","Your Chronicle is stored locally. RuneChronicle does not upload your journal, RSN, drops or login/session data. NPC names may be sent to the OSRS Wiki only when retrieving monster/boss artwork.","Exported backups contain Chronicle data, so treat them as private files."));
  JScrollPane sp=new JScrollPane(root);sp.setBorder(null);sp.getViewport().setBackground(BG);sp.getVerticalScrollBar().setUnitIncrement(18);setContentPane(sp);
 }
 private static JPanel card(String title,String body,String tip){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBackground(CARD);p.setBorder(new CompoundBorder(new EmptyBorder(0,0,8,0),new CompoundBorder(new LineBorder(LINE),new EmptyBorder(12,13,12,13))));p.setAlignmentX(Component.LEFT_ALIGNMENT);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,150));p.add(lab(title,11,GOLD,Font.BOLD));p.add(Box.createVerticalStrut(5));p.add(html(body,10,WHITE));p.add(Box.createVerticalStrut(5));p.add(html(tip,9,GREEN));return p;}
 private static JLabel lab(String s,int z,Color c,int st){JLabel l=new JLabel(s);l.setForeground(c);l.setFont(new Font("SansSerif",st,z));l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
 private static JLabel html(String s,int z,Color c){JLabel l=new JLabel("<html><body style='width:570px'>"+s+"</body></html>");l.setForeground(c);l.setFont(new Font("SansSerif",Font.PLAIN,z));l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
}
