package com.runechronicle;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

final class ExpeditionDialog extends JDialog {
 private final RuneChroniclePlugin plugin; private final ItemManager items; private final JPanel results=new JPanel(),tracked=new JPanel(),summary=new JPanel(),history=new JPanel(); private final JTextField search=new JTextField(); private final JButton toggle=new JButton(); private final javax.swing.Timer refreshTimer;
 ExpeditionDialog(RuneChroniclePlugin p,ItemManager im){plugin=p;items=im;setTitle("RuneChronicle · Boss / Monster Profit");setModal(false);setSize(500,740);setLocationRelativeTo(null);setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  JPanel root=new JPanel();root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS));root.setBackground(RuneChroniclePanel.BG);root.setBorder(new EmptyBorder(18,20,20,20));
  root.add(text("BOSS / MONSTER PROFIT",20,RuneChroniclePanel.WHITE,Font.BOLD));root.add(text("LOOT − SUPPLIES = ACTUAL NET PROFIT",9,RuneChroniclePanel.GOLD,Font.BOLD));root.add(Box.createVerticalStrut(12));
  summary.setLayout(new BoxLayout(summary,BoxLayout.Y_AXIS));summary.setBackground(RuneChroniclePanel.SURFACE);summary.setBorder(new CompoundBorder(new LineBorder(RuneChroniclePanel.LINE),new EmptyBorder(12,12,12,12)));summary.setAlignmentX(Component.LEFT_ALIGNMENT);root.add(summary);root.add(Box.createVerticalStrut(8));
  toggle.setAlignmentX(Component.LEFT_ALIGNMENT);toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));toggle.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));toggle.addActionListener(e->{if(plugin.expeditionActive())plugin.endExpedition();else plugin.startExpedition();refresh();});root.add(toggle);
  root.add(section("PROFIT HISTORY"));history.setLayout(new BoxLayout(history,BoxLayout.Y_AXIS));history.setOpaque(false);history.setAlignmentX(Component.LEFT_ALIGNMENT);root.add(history);
  root.add(section("ALWAYS TRACKED SUPPLIES"));root.add(text("Search for an item to always include its usage in profit calculations, even when Profit Tracker is not running.",9,RuneChroniclePanel.MUTED,Font.PLAIN));root.add(Box.createVerticalStrut(7));
  JPanel searchLine=new JPanel(new BorderLayout(8,0));searchLine.setOpaque(false);searchLine.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));JLabel sl=new JLabel("SEARCH ITEMS");sl.setForeground(RuneChroniclePanel.GOLD);sl.setFont(new Font(Font.SANS_SERIF,Font.BOLD,9));search.setToolTipText("Search every OSRS item");searchLine.add(sl,BorderLayout.WEST);searchLine.add(search,BorderLayout.CENTER);root.add(searchLine);
  results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));results.setOpaque(false);results.setAlignmentX(Component.LEFT_ALIGNMENT);root.add(results);root.add(Box.createVerticalStrut(4));tracked.setLayout(new BoxLayout(tracked,BoxLayout.Y_AXIS));tracked.setOpaque(false);tracked.setAlignmentX(Component.LEFT_ALIGNMENT);root.add(tracked);
  JLabel note=text("Profit Tracker is conservative: banking, equipping and ordinary inventory movement should not be charged as supply use.",8,RuneChroniclePanel.MUTED,Font.PLAIN);note.setBorder(new EmptyBorder(12,0,0,0));root.add(note);
  JScrollPane sp=new JScrollPane(root);sp.setBorder(null);sp.getViewport().setBackground(RuneChroniclePanel.BG);sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);setContentPane(sp);
  search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){find();}public void removeUpdate(DocumentEvent e){find();}public void changedUpdate(DocumentEvent e){find();}});refreshTimer=new javax.swing.Timer(1000,e->refreshLive());refreshTimer.start();refresh(); }
 @Override public void dispose(){if(refreshTimer!=null)refreshTimer.stop();plugin.profitDialogClosed(this);super.dispose();}
 private void refresh(){refreshLive();renderTracked();renderHistory();}
 private void refreshLive(){summary.removeAll();RuneChroniclePlugin.ExpeditionSnapshot s=plugin.expeditionSnapshot();if(s.active){summary.add(text("● PROFIT TRACKER ACTIVE",10,RuneChroniclePanel.GREEN,Font.BOLD));summary.add(text(RuneChroniclePlugin.format(s.seconds)+" · "+s.kills+" kills",10,RuneChroniclePanel.MUTED,Font.PLAIN));summary.add(text("Loot  "+RuneChroniclePlugin.gp(s.loot)+" gp",13,RuneChroniclePanel.WHITE,Font.BOLD));summary.add(text("Supplies used  −"+RuneChroniclePlugin.gp(s.supplies)+" gp",13,RuneChroniclePanel.RED,Font.BOLD));if(s.recovered>0)summary.add(text("Recovered  +"+RuneChroniclePlugin.gp(s.recovered)+" gp",12,RuneChroniclePanel.GREEN,Font.BOLD));summary.add(text("Net supplies  −"+RuneChroniclePlugin.gp(s.netSupplies())+" gp",11,RuneChroniclePanel.MUTED,Font.BOLD));summary.add(text("Net  "+(s.net()<0?"−":"")+RuneChroniclePlugin.gp(Math.abs(s.net()))+" gp",16,s.net()>=0?RuneChroniclePanel.GREEN:RuneChroniclePanel.RED,Font.BOLD));toggle.setText("STOP TRACKING");toggle.setBackground(new Color(65,30,32));toggle.setForeground(RuneChroniclePanel.RED);}else{summary.add(text("READY TO TRACK",10,RuneChroniclePanel.MUTED,Font.BOLD));summary.add(text("Start before a boss or monster run. RuneChronicle will combine kills, claimed loot and consumed supplies into actual net profit.",9,RuneChroniclePanel.MUTED,Font.PLAIN));toggle.setText("START TRACKING");toggle.setBackground(new Color(52,42,22));toggle.setForeground(RuneChroniclePanel.GOLD);}summary.revalidate();summary.repaint();}
 private void renderHistory(){history.removeAll();java.util.List<RuneChroniclePlugin.ProfitRun> runs=plugin.profitRuns();java.util.List<RuneChroniclePlugin.MonsterProfit> monsters=plugin.monsterProfitHistory();JButton mh=new JButton("SEARCH MONSTER HISTORY →  "+monsters.size()+" tracked");styleButton(mh);mh.addActionListener(e->showMonsterHistory());history.add(mh);history.add(Box.createVerticalStrut(5));if(runs.isEmpty())history.add(text("No completed profit-tracking runs yet.",9,RuneChroniclePanel.MUTED,Font.PLAIN));else{for(RuneChroniclePlugin.ProfitRun r:runs.subList(0,Math.min(8,runs.size()))){JButton b=new JButton(runLabel(r));styleButton(b);b.addActionListener(e->showRun(r));history.add(b);history.add(Box.createVerticalStrut(4));}if(runs.size()>8){JButton all=new JButton("VIEW ALL "+runs.size()+" RUNS →");styleButton(all);all.addActionListener(e->showAllRuns());history.add(all);}}history.revalidate();history.repaint();}
 private String runLabel(RuneChroniclePlugin.ProfitRun r){String d=Instant.ofEpochSecond(r.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · h:mm a"));return d+"   "+r.primaryMonster()+" · "+r.kills+" kills   "+(r.net()>=0?"+":"−")+RuneChroniclePlugin.gp(Math.abs(r.net()));}
 private void showAllRuns(){JPanel p=vertical();for(RuneChroniclePlugin.ProfitRun r:plugin.profitRuns()){JButton b=new JButton(runLabel(r));styleButton(b);b.addActionListener(e->showRun(r));p.add(b);p.add(Box.createVerticalStrut(4));}showScroll("Profit History",p,560,620);}
 private void showRun(RuneChroniclePlugin.ProfitRun r){JPanel p=vertical();p.add(text(r.primaryMonster().toUpperCase(Locale.ROOT),18,RuneChroniclePanel.WHITE,Font.BOLD));p.add(text(RuneChroniclePlugin.format(r.seconds())+" · "+r.kills+" kills",10,RuneChroniclePanel.MUTED,Font.PLAIN));p.add(text("Loot  "+RuneChroniclePlugin.gp(r.loot)+" gp",13,RuneChroniclePanel.WHITE,Font.BOLD));p.add(text("Supplies used  −"+RuneChroniclePlugin.gp(r.supplies)+" gp",13,RuneChroniclePanel.RED,Font.BOLD));if(r.recovered>0)p.add(text("Recovered  +"+RuneChroniclePlugin.gp(r.recovered)+" gp",12,RuneChroniclePanel.GREEN,Font.BOLD));p.add(text("Net supplies  −"+RuneChroniclePlugin.gp(r.netSupplies())+" gp",11,RuneChroniclePanel.MUTED,Font.BOLD));p.add(text("Net  "+(r.net()>=0?"+":"−")+RuneChroniclePlugin.gp(Math.abs(r.net()))+" gp",16,r.net()>=0?RuneChroniclePanel.GREEN:RuneChroniclePanel.RED,Font.BOLD));if(r.kills>0)p.add(text("Profit / kill  "+RuneChroniclePlugin.gp(r.net()/r.kills)+" gp",10,RuneChroniclePanel.MUTED,Font.PLAIN));if(r.seconds()>0)p.add(text("Profit / hour  "+RuneChroniclePlugin.gp(r.net()*3600/r.seconds())+" gp",10,RuneChroniclePanel.MUTED,Font.PLAIN));p.add(section("KILLS"));for(Map.Entry<String,Long>x:r.monsters.entrySet())p.add(text(x.getKey()+" ×"+x.getValue(),10,RuneChroniclePanel.WHITE,Font.BOLD));p.add(section("LOOT"));for(AggLine a:aggregate(r.events,"LOOT",false))p.add(aggLine(a,false));p.add(section("SUPPLIES USED"));for(AggLine a:aggregate(r.events,"SUPPLY",true))p.add(aggLine(a,true));if(r.recovered>0){p.add(section("RECOVERED SUPPLIES"));for(AggLine a:aggregate(r.events,"RECOVERED_SUPPLY",false))p.add(aggLine(a,false));}JButton del=new JButton("DELETE RUN");styleButton(del);del.setForeground(RuneChroniclePanel.RED);del.addActionListener(e->{int ok=JOptionPane.showConfirmDialog(this,"Delete this profit-tracking run? This removes the run and its run-linked events from RuneChronicle history.","Delete Profit Run",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);if(ok==JOptionPane.YES_OPTION){plugin.deleteProfitRun(r.id);refresh();Window w=SwingUtilities.getWindowAncestor(del);if(w!=null)w.dispose();}});p.add(section("RUN ACTIONS"));p.add(del);showScroll("Profit Run",p,540,680);}
 private static final class AggLine{String name;long qty,value;AggLine(String n){name=n;}}
 private java.util.List<AggLine> aggregate(java.util.List<ChronicleStore.Event> events,String type,boolean supplies){Map<String,AggLine> m=new LinkedHashMap<>();for(ChronicleStore.Event e:events)if(type.equals(e.type)){String n=supplies?plugin.supplyDisplayName(e):e.title;AggLine a=m.computeIfAbsent(n,k->new AggLine(k));a.qty+=Math.max(1,e.quantity);a.value+=e.value;}return new ArrayList<>(m.values());}
 private JLabel aggLine(AggLine a,boolean cost){return text((cost?"− ":"+ ")+a.name+(a.qty>1?" ×"+a.qty:"")+" · "+RuneChroniclePlugin.gp(a.value)+" gp",10,cost?RuneChroniclePanel.RED:RuneChroniclePanel.WHITE,Font.PLAIN);}
 private JLabel eventLine(ChronicleStore.Event e,boolean cost){return text((cost?"− ":"+ ")+e.title+(e.quantity>1?" ×"+e.quantity:"")+" · "+RuneChroniclePlugin.gp(e.value)+" gp",10,cost?RuneChroniclePanel.RED:RuneChroniclePanel.WHITE,Font.PLAIN);}
 private void showMonsterHistory(){
  final JDialog win=new JDialog(this,"Monster Profit History",false);
  final JPanel shell=vertical();
  shell.add(text("MONSTER PROFIT HISTORY",18,RuneChroniclePanel.WHITE,Font.BOLD));
  shell.add(text("Search the OSRS monster catalog. Green means RuneChronicle has history; red means no history yet.",9,RuneChroniclePanel.MUTED,Font.PLAIN));
  shell.add(Box.createVerticalStrut(10));
  final JTextField monsterSearch=new JTextField();
  monsterSearch.setToolTipText("Search all OSRS monsters");
  monsterSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
  monsterSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
  shell.add(monsterSearch);
  shell.add(Box.createVerticalStrut(10));
  final JPanel monsterResults=new JPanel();
  monsterResults.setLayout(new BoxLayout(monsterResults,BoxLayout.Y_AXIS));
  monsterResults.setOpaque(false);
  monsterResults.setAlignmentX(Component.LEFT_ALIGNMENT);
  shell.add(monsterResults);
  Runnable render=()->{
   monsterResults.removeAll();
   String q=monsterSearch.getText().trim();
   if(q.isEmpty()){
    monsterResults.add(text("Start typing a monster name — for example: vork, zul, hig.",9,RuneChroniclePanel.MUTED,Font.PLAIN));
   }else{
    java.util.List<RuneChroniclePlugin.MonsterChoice> matches=plugin.searchMonsters(q,100);
    if(matches.isEmpty())monsterResults.add(text("No OSRS monster matches “"+q+"”.",9,RuneChroniclePanel.MUTED,Font.PLAIN));
    for(RuneChroniclePlugin.MonsterChoice choice:matches){
     RuneChroniclePlugin.MonsterProfit history=plugin.monsterProfit(choice.name);
     boolean has=history!=null&&history.kills>0;
     String label=has
      ? "●  "+choice.name+"  ·  "+history.kills+" kills  ·  "+history.runs+" runs  ·  "+(history.net()>=0?"+":"−")+RuneChroniclePlugin.gp(Math.abs(history.net()))+" gp"
      : "●  "+choice.name+"  ·  No RuneChronicle history yet";
     JButton b=new JButton(label);
     styleButton(b);
     b.setForeground(has?RuneChroniclePanel.GREEN:RuneChroniclePanel.RED);
     b.addActionListener(e->{if(has)showMonster(history);else showEmptyMonster(choice.name);});
     monsterResults.add(b);monsterResults.add(Box.createVerticalStrut(5));
    }
   }
   monsterResults.revalidate();monsterResults.repaint();
  };
  monsterSearch.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){render.run();}public void removeUpdate(DocumentEvent e){render.run();}public void changedUpdate(DocumentEvent e){render.run();}});
  render.run();
  JScrollPane sp=new JScrollPane(shell);sp.setBorder(null);sp.getViewport().setBackground(RuneChroniclePanel.BG);sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
  win.setContentPane(sp);win.setSize(620,650);win.setLocationRelativeTo(this);win.setVisible(true);
 }
 private void showEmptyMonster(String name){
  JPanel p=vertical();
  p.add(text(name.toUpperCase(Locale.ROOT),18,RuneChroniclePanel.WHITE,Font.BOLD));
  p.add(text("NO TRACKED HISTORY",10,RuneChroniclePanel.RED,Font.BOLD));
  p.add(Box.createVerticalStrut(8));
  p.add(text("<html>RuneChronicle hasn't recorded any kills or Profit Tracker runs for this monster yet.<br><br>Once RuneChronicle observes activity with this monster, this result will turn green and its history will become available here.</html>",9,RuneChroniclePanel.MUTED,Font.PLAIN));
  showScroll(name+" History",p,520,360);
 }
 private void showMonster(RuneChroniclePlugin.MonsterProfit m){JPanel p=vertical();p.add(text(m.name.toUpperCase(Locale.ROOT),18,RuneChroniclePanel.WHITE,Font.BOLD));p.add(text(m.kills+" tracked kills · "+m.runs+" runs",10,RuneChroniclePanel.MUTED,Font.PLAIN));p.add(text("Attributed loot  "+RuneChroniclePlugin.gp(m.loot)+" gp",13,RuneChroniclePanel.WHITE,Font.BOLD));p.add(text("Attributed supplies  −"+RuneChroniclePlugin.gp(m.supplies)+" gp",13,RuneChroniclePanel.RED,Font.BOLD));if(m.recovered>0)p.add(text("Recovered  +"+RuneChroniclePlugin.gp(m.recovered)+" gp",12,RuneChroniclePanel.GREEN,Font.BOLD));p.add(text("Tracked net  "+(m.net()>=0?"+":"−")+RuneChroniclePlugin.gp(Math.abs(m.net()))+" gp",16,m.net()>=0?RuneChroniclePanel.GREEN:RuneChroniclePanel.RED,Font.BOLD));if(m.kills>0)p.add(text("Average / kill  "+RuneChroniclePlugin.gp(m.net()/m.kills)+" gp",10,RuneChroniclePanel.MUTED,Font.PLAIN));p.add(text("Supply cost is attributed only when a run contains one monster type. Mixed runs keep uncertain supply cost at the run level.",8,RuneChroniclePanel.MUTED,Font.PLAIN));p.add(section("RUN HISTORY"));for(RuneChroniclePlugin.ProfitRun r:m.history){JButton b=new JButton(runLabel(r));styleButton(b);b.addActionListener(e->showRun(r));p.add(b);p.add(Box.createVerticalStrut(4));}showScroll(m.name+" History",p,540,640);}
 private void showScroll(String title,JPanel p,int w,int h){JDialog d=new JDialog(this,title,false);JScrollPane sp=new JScrollPane(p);sp.setBorder(null);sp.getViewport().setBackground(RuneChroniclePanel.BG);sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);d.setContentPane(sp);d.setSize(w,h);d.setLocationRelativeTo(this);d.setVisible(true);}
 private JPanel vertical(){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBackground(RuneChroniclePanel.BG);p.setBorder(new EmptyBorder(16,16,16,16));return p;}
 private void styleButton(JButton b){b.setFocusable(false);b.setHorizontalAlignment(SwingConstants.LEFT);b.setForeground(RuneChroniclePanel.WHITE);b.setBackground(RuneChroniclePanel.SURFACE2);b.setBorder(new CompoundBorder(new LineBorder(RuneChroniclePanel.LINE),new EmptyBorder(8,9,8,9)));b.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));b.setAlignmentX(Component.LEFT_ALIGNMENT);}
 private void find(){results.removeAll();String q=search.getText().trim();if(q.length()>=2){java.util.List<RuneChroniclePlugin.ItemChoice> found=plugin.searchItems(q);for(RuneChroniclePlugin.ItemChoice x:found.subList(0,Math.min(6,found.size())))results.add(itemRow(x,true));}results.revalidate();results.repaint();}
 private void renderTracked(){tracked.removeAll();Set<Integer> ids=plugin.trackedSupplyIds();if(ids.isEmpty())tracked.add(text("No permanent supplies added yet.",9,RuneChroniclePanel.MUTED,Font.PLAIN));else for(int id:ids){try{String n=items.getItemComposition(id).getName();tracked.add(itemRow(new RuneChroniclePlugin.ItemChoice(id,n),false));}catch(Exception ignored){}}tracked.revalidate();tracked.repaint();}
 private JPanel itemRow(RuneChroniclePlugin.ItemChoice x,boolean add){JPanel r=new JPanel(new BorderLayout(7,0));r.setBackground(RuneChroniclePanel.SURFACE);r.setBorder(new EmptyBorder(5,6,5,6));r.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));JLabel icon=new JLabel();icon.setPreferredSize(new Dimension(30,30));try{AsyncBufferedImage img=items.getImage(x.id,1,false);img.addTo(icon);}catch(Exception ignored){}r.add(icon,BorderLayout.WEST);r.add(text(x.name,10,RuneChroniclePanel.WHITE,Font.BOLD),BorderLayout.CENTER);JButton b=new JButton(add?"ADD":"×");b.setFocusable(false);b.setForeground(add?RuneChroniclePanel.GOLD:RuneChroniclePanel.RED);b.setBackground(RuneChroniclePanel.SURFACE2);b.addActionListener(e->{if(add)plugin.addTrackedSupply(x.id);else plugin.removeTrackedSupply(x.id);search.setText("");refresh();});r.add(b,BorderLayout.EAST);return r;}
 private JLabel section(String s){JLabel l=text(s,9,RuneChroniclePanel.GOLD,Font.BOLD);l.setBorder(new EmptyBorder(18,0,7,0));return l;}
 private JLabel text(String s,int z,Color c,int style){JLabel l=new JLabel("<html><body style='width:410px'>"+s+"</body></html>");l.setFont(new Font(Font.SANS_SERIF,style,z));l.setForeground(c);l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
}
