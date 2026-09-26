package com.runechronicle;

import com.google.inject.Provides;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(name="RuneChronicle",description="Your RuneScape journey, automatically recorded.",tags={"history","session","xp","journal","recap","progress","loot","kills","boss","collection log"})
public class RuneChroniclePlugin extends Plugin {
 @Inject private Client client; @Inject private OkHttpClient httpClient; @Inject private Gson gson; @Inject private ClientToolbar toolbar; @Inject private ClientThread clientThread; @Inject private ItemManager itemManager; @Inject private RuneChronicleConfig config; @Inject private ConfigManager configManager; @Inject private OverlayManager overlayManager; @Inject private RecapOverlay recapOverlay;
 private final ChronicleStore store=new ChronicleStore(); private final Map<Skill,Integer> lastXp=new EnumMap<>(Skill.class),lastLevel=new EnumMap<>(Skill.class); private final Map<Integer,Integer> inventory=new HashMap<>(); private final List<PendingLoot> pendingLoot=new ArrayList<>(); private final Map<String,Integer> sessionBossCounts=new HashMap<>();
 private NpcPortraitService npcPortraits;
 private long sessionStart; private String sessionId,profile="unknown",playerName="RuneScape Adventurer"; private final java.util.List<ItemChoice> itemCatalog=new ArrayList<>(); private RuneChroniclePanel panel; private NavigationButton nav; private boolean recapOpen,trackingActive; private int baselineTicksRemaining; private NPC lastInteractingNpc;
 private static final long CLAIM_WINDOW=35;
 private static final Pattern QUEST=Pattern.compile("(?i)(?:congratulations[,! ]+)?(?:you have completed|you've completed) (?:the )?(.+?)(?: quest)?[!.]?$"), COLLECTION_ITEM=Pattern.compile("(?i)New item added to your collection log: (.*)"), PB=Pattern.compile("(?i).*(?:new personal best|personal best).*"), CA=Pattern.compile("(?i).*(?:combat task|combat achievement).*completed.*"), PET=Pattern.compile("(?i).*funny feeling like you're being followed.*"), CLUE=Pattern.compile("(?i).*(?:treasure trail|clue scroll).*completed.*");
 private static final Set<String> BOSSES=new HashSet<>(Arrays.asList("abyssal sire","alchemical hydra","amoxliatl","araxxor","artio","barrows","bryophyta","callisto","calvar'ion","cerberus","chaos elemental","chaos fanatic","commander zilyana","corporeal beast","crazy archaeologist","dagannoth prime","dagannoth rex","dagannoth supreme","deranged archaeologist","duke sucellus","general graardor","giant mole","grotesque guardians","hespori","kalphite queen","king black dragon","kraken","kreearra","k'ril tsutsaroth","mimic","nex","nightmare","obor","phantom muspah","phosani's nightmare","sarachnis","scorpia","scurrius","skotizo","tempoross","the gauntlet","the corrupted gauntlet","the hueycoatl","the leviathan","the whisperer","thermonuclear smoke devil","vardorvis","venenatis","vet'ion","vorkath","wintertodt","yama","zalcano","zulrah"));
 static final class ItemChoice { final int id; final String name; ItemChoice(int id,String name){this.id=id;this.name=name;} public String toString(){return name;} }
 static final class PendingLoot {int itemId,remaining;String source;long ts;PendingLoot(int i,int q,String s,long t){itemId=i;remaining=q;source=s;ts=t;}}
 static final class Summary {long playSeconds,xp,levels,kills,bossKills,lootValue,lootHaValue,lootItems,deaths;Map<String,Long>skillXp=new HashMap<>(),killCounts=new HashMap<>(),bossCounts=new HashMap<>();List<ChronicleStore.Event>events=new ArrayList<>();}
 @Provides RuneChronicleConfig provideConfig(ConfigManager m){return m.getConfig(RuneChronicleConfig.class);}
 @Override protected void startUp(){store.load();npcPortraits=new NpcPortraitService(httpClient,gson);panel=new RuneChroniclePanel(this,itemManager,npcPortraits);BufferedImage icon=ImageUtil.loadImageResource(getClass(),"runechronicle_icon.png");nav=NavigationButton.builder().tooltip("RuneChronicle").icon(icon).priority(6).panel(panel).build();toolbar.addNavigation(nav);overlayManager.add(recapOverlay);SwingUtilities.invokeLater(this::showWelcomeIfNeeded);if(client.getGameState()==GameState.LOGGED_IN)beginSession();}
 @Override protected void shutDown(){if(nav!=null)toolbar.removeNavigation(nav);overlayManager.remove(recapOverlay);endSession("Plugin stopped");}
 @Subscribe public void onPostMenuSort(PostMenuSort e){
  if(!config.showGameRecapButton()||!trackingActive||recapOpen||client.isMenuOpen())return;
  java.awt.Rectangle b=recapOverlay.getBounds();
  net.runelite.api.Point m=client.getMouseCanvasPosition();
  if(b==null||b.isEmpty()||m==null||!b.contains(m.getX(),m.getY()))return;
  client.createMenuEntry(-1).setOption("Open recap").setTarget("<col=f2bb3b>RuneChronicle</col>").setType(MenuAction.RUNELITE).onClick(x->showSessionRecap());
 }
 @Subscribe public void onGameStateChanged(GameStateChanged e){if(e.getGameState()==GameState.LOGGED_IN&&sessionStart==0)beginSession();if(e.getGameState()==GameState.HOPPING&&sessionStart>0){trackingActive=false;baselineTicksRemaining=2;lastXp.clear();lastLevel.clear();inventory.clear();pendingLoot.clear();lastInteractingNpc=null;refresh();}if(e.getGameState()==GameState.LOGIN_SCREEN&&sessionStart>0)endSession("Logged out");}
 @Subscribe public void onGameTick(GameTick e){if(client.getLocalPlayer()!=null&&client.getLocalPlayer().getInteracting() instanceof NPC)lastInteractingNpc=(NPC)client.getLocalPlayer().getInteracting();long now=ChronicleStore.now();pendingLoot.removeIf(x->now-x.ts>CLAIM_WINDOW||x.remaining<=0);if(sessionStart==0||trackingActive)return;if(baselineTicksRemaining>0){baselineTicksRemaining--;return;}captureBaseline();captureInventory();trackingActive=true;refresh();}
 @Subscribe public void onStatChanged(StatChanged e){if(sessionStart==0||!trackingActive)return;Skill sk=e.getSkill();if(sk==Skill.OVERALL)return;int newXp=e.getXp(),newLv=Experience.getLevelForXp(newXp);Integer oldXp=lastXp.get(sk),oldLv=lastLevel.get(sk);if(oldXp==null||oldLv==null||newXp<oldXp){lastXp.put(sk,newXp);lastLevel.put(sk,newLv);return;}if(newXp>oldXp){long g=(long)newXp-oldXp;store.add(event("XP",sk.getName(),"+"+g+" XP",g));}if(newLv>oldLv)for(int lv=oldLv+1;lv<=newLv;lv++)store.add(event("LEVEL",sk.getName()+" level "+lv,"Reached level "+lv,1));lastXp.put(sk,newXp);lastLevel.put(sk,newLv);refresh();}
 @Subscribe public void onActorDeath(ActorDeath e){if(!trackingActive)return;if(e.getActor()==client.getLocalPlayer()){if(config.logDeaths()){store.add(event("DEATH","You died","Adventure interrupted",1));refresh();}return;}if(!config.logMonsterKills()||!(e.getActor() instanceof NPC))return;NPC n=(NPC)e.getActor();Player me=client.getLocalPlayer();if(n!=lastInteractingNpc&&(me==null||n.getInteracting()!=me))return;String name=n.getName()==null?"Unknown monster":n.getName();boolean boss=isBoss(name);String type=boss&&config.logBossKills()?"BOSS_KILL":"KILL";ChronicleStore.Event ev=event(type,name,boss?"Boss defeated":"Defeated",1);try{ev.npcId=n.getId();}catch(Exception ignored){}ev.boss=boss;if(boss){int kc=sessionBossCounts.merge(name,1,Integer::sum);ev.kc=kc;ev.category="Bosses Defeated";}store.add(ev);if(n==lastInteractingNpc)lastInteractingNpc=null;refresh();}
 @Subscribe public void onNpcLootReceived(NpcLootReceived e){if(!trackingActive||!config.logLoot())return;String source=e.getNpc()!=null&&e.getNpc().getName()!=null?e.getNpc().getName():"Monster loot";long now=ChronicleStore.now();for(ItemStack s:e.getItems()){int id=itemManager.canonicalize(s.getId());pendingLoot.add(new PendingLoot(id,s.getQuantity(),source,now));}}
 @Subscribe public void onLootReceived(LootReceived e){if(!trackingActive||!config.logActivities()||e==null)return;String source=e.getName()==null?"Activity reward":e.getName();String type=e.getType()==null?"":e.getType().toString();if(type.toUpperCase(Locale.ROOT).contains("NPC"))return;ChronicleStore.Event activity=event("ACTIVITY",friendlyActivity(source),activityDetail(source,e.getAmount()),1);activity.source=source;activity.category="Activities";store.add(activity);if(config.logLoot())for(ItemStack s:e.getItems())recordAwardedLoot(source,itemManager.canonicalize(s.getId()),s.getQuantity(),true);refresh();}
 @Subscribe public void onItemContainerChanged(ItemContainerChanged e){if(!trackingActive||!config.logLoot())return;ItemContainer c=e.getItemContainer();ItemContainer inv=client.getItemContainer(InventoryID.INVENTORY);if(c==null||inv==null||e.getContainerId()!=InventoryID.INVENTORY.getId())return;Map<Integer,Integer> now=counts(inv);for(Map.Entry<Integer,Integer>x:now.entrySet()){int old=inventory.getOrDefault(x.getKey(),0),gain=x.getValue()-old;if(gain>0)claim(x.getKey(),gain);}inventory.clear();inventory.putAll(now);}
 private void claim(int itemId,int gained){long now=ChronicleStore.now();for(PendingLoot p:new ArrayList<>(pendingLoot)){if(gained<=0)break;if(p.itemId!=itemId||p.remaining<=0||now-p.ts>CLAIM_WINDOW)continue;int q=Math.min(gained,p.remaining);p.remaining-=q;gained-=q;recordAwardedLoot(p.source,itemId,q,false);}pendingLoot.removeIf(x->x.remaining<=0);}
 private void recordAwardedLoot(String source,int id,int q,boolean special){ItemComposition c=itemManager.getItemComposition(id);String name=c==null?("Item "+id):c.getName();int ge=Math.max(0,itemManager.getItemPrice(id));int ha=c==null?0:Math.max(0,c.getHaPrice());long geTotal=(long)ge*q,haTotal=(long)ha*q;long geCheck=config.useStackValue()?geTotal:ge,haCheck=config.useStackValue()?haTotal:ha;if(!(special&&config.alwaysLogSpecial())&&!qualifies(geCheck,haCheck))return;String detail=(q>1?q+" × ":"")+name+"  •  GE "+gp(geTotal)+"  •  HA "+gp(haTotal);ChronicleStore.Event ev=new ChronicleStore.Event(ChronicleStore.now(),profile,sessionId,"LOOT",name,detail,geTotal,source,geTotal,id,q);ev.haValue=haTotal;ev.category=special?"Reward Loot":"Claimed Loot";store.add(ev);}
 private boolean qualifies(long ge,long ha){int g=config.minimumGeValue(),h=config.minimumHaValue();switch(config.lootValueRule()){case GE_ONLY:return g==0||ge>=g;case HA_ONLY:return h==0||ha>=h;case GE_AND_HA:return (g==0||ge>=g)&&(h==0||ha>=h);default:return (g==0&&h==0)||(g>0&&ge>=g)||(h>0&&ha>=h);}}
 @Subscribe public void onChatMessage(ChatMessage e){if(!trackingActive||!config.logGameMilestones())return;if(e.getType()!=ChatMessageType.GAMEMESSAGE&&e.getType()!=ChatMessageType.SPAM)return;String m=Text.removeTags(e.getMessage()).trim();String type=null,title=null;Matcher q=QUEST.matcher(m),cl=COLLECTION_ITEM.matcher(m);if(q.find()){type="QUEST";title=q.group(1);}else if(cl.find()){title=cl.group(1).trim();type="COLLECTION";}else if(CA.matcher(m).matches()){type="ACHIEVEMENT";title="Combat Achievement";}else if(PB.matcher(m).matches()){type="PB";title="New Personal Best";}else if(PET.matcher(m).matches()){type="PET";title="New Pet!";}else if(CLUE.matcher(m).matches()){type="CLUE";title="Clue Completed";}if(type!=null){ChronicleStore.Event ev=event(type,title,m,1);ev.category=category(type);if("COLLECTION".equals(type)||"PET".equals(type))tryAttachItem(ev,title);store.add(ev);refresh();}}
 private void tryAttachItem(ChronicleStore.Event ev,String query){try{java.util.List<net.runelite.http.api.item.ItemPrice> r=itemManager.search(query);if(r!=null&&!r.isEmpty())ev.itemId=r.get(0).getId();}catch(Exception ignored){}}
 // RuneChronicle intentionally does not intercept RuneScape logout or world-switch controls.
 private ChronicleStore.Event event(String t,String title,String detail,long amount){return new ChronicleStore.Event(ChronicleStore.now(),profile,sessionId,t,title,detail,amount);}
 private void beginSession(){buildItemCatalog();profile=profileId();sessionStart=ChronicleStore.now();sessionId=profile+"-"+sessionStart;if(client.getLocalPlayer()!=null&&client.getLocalPlayer().getName()!=null)playerName=client.getLocalPlayer().getName();lastXp.clear();lastLevel.clear();inventory.clear();pendingLoot.clear();sessionBossCounts.clear();trackingActive=false;baselineTicksRemaining=2;store.add(event("SESSION_START","Session started","Establishing account baseline",0));refresh();}
 private void captureBaseline(){lastXp.clear();lastLevel.clear();for(Skill sk:Skill.values()){if(sk==Skill.OVERALL)continue;int xp=client.getSkillExperience(sk);if(xp<0)continue;lastXp.put(sk,xp);lastLevel.put(sk,Experience.getLevelForXp(xp));}}
 private void captureInventory(){inventory.clear();ItemContainer inv=client.getItemContainer(InventoryID.INVENTORY);if(inv!=null)inventory.putAll(counts(inv));}
 private Map<Integer,Integer> counts(ItemContainer c){Map<Integer,Integer>m=new HashMap<>();for(Item i:c.getItems())if(i!=null&&i.getId()>0&&i.getQuantity()>0)m.merge(itemManager.canonicalize(i.getId()),i.getQuantity(),Integer::sum);return m;}
 private void endSession(String reason){if(sessionStart==0)return;long now=ChronicleStore.now();store.add(new ChronicleStore.Event(now,profile,sessionId,"SESSION_END","Session complete",reason+" · "+format(now-sessionStart),now-sessionStart));sessionStart=0;sessionId=null;lastXp.clear();lastLevel.clear();inventory.clear();pendingLoot.clear();trackingActive=false;baselineTicksRemaining=0;recapOpen=false;lastInteractingNpc=null;refresh();}
 Summary summary(String range){Summary s=new Summary();long now=ChronicleStore.now(),from=0;ZoneId z=ZoneId.systemDefault();ZonedDateTime n=Instant.ofEpochSecond(now).atZone(z);List<ChronicleStore.Event>all;if("SESSION".equals(range))all=sessionId==null?Collections.emptyList():store.session(profile,sessionId);else{if("TODAY".equals(range))from=n.toLocalDate().atStartOfDay(z).toEpochSecond();else if("WEEK".equals(range))from=n.toLocalDate().minusDays(n.getDayOfWeek().getValue()-1).atStartOfDay(z).toEpochSecond();else if("MONTH".equals(range))from=n.toLocalDate().withDayOfMonth(1).atStartOfDay(z).toEpochSecond();all=store.allFor(profile);}long open=0;for(ChronicleStore.Event e:all){if(e.ts<from)continue;s.events.add(e);switch(e.type){case"XP":s.xp+=e.amount;s.skillXp.merge(e.title,e.amount,Long::sum);break;case"LEVEL":s.levels+=e.amount;break;case"KILL":s.kills+=e.amount;s.killCounts.merge(e.title,e.amount,Long::sum);break;case"BOSS_KILL":s.kills+=e.amount;s.bossKills+=e.amount;s.bossCounts.merge(e.title,e.amount,Long::sum);break;case"LOOT":s.lootValue+=e.value;s.lootHaValue+=e.haValue;s.lootItems+=e.quantity;break;case"DEATH":s.deaths+=e.amount;break;}if("SESSION_START".equals(e.type))open=e.ts;else if("SESSION_END".equals(e.type)&&open>0){s.playSeconds+=Math.max(0,e.ts-open);open=0;}}if(open>0)s.playSeconds+=Math.max(0,now-open);return s;}
 Summary summary(long from,long to){Summary s=new Summary();long open=0;for(ChronicleStore.Event e:store.allFor(profile)){if(e.ts<from||e.ts>to)continue;s.events.add(e);switch(e.type){case"XP":s.xp+=e.amount;s.skillXp.merge(e.title,e.amount,Long::sum);break;case"LEVEL":s.levels+=e.amount;break;case"KILL":s.kills+=e.amount;s.killCounts.merge(e.title,e.amount,Long::sum);break;case"BOSS_KILL":s.kills+=e.amount;s.bossKills+=e.amount;s.bossCounts.merge(e.title,e.amount,Long::sum);break;case"LOOT":s.lootValue+=e.value;s.lootHaValue+=e.haValue;s.lootItems+=e.quantity;break;case"DEATH":s.deaths+=e.amount;break;}if("SESSION_START".equals(e.type))open=e.ts;else if("SESSION_END".equals(e.type)&&open>0){s.playSeconds+=Math.max(0,e.ts-open);open=0;}}if(open>0)s.playSeconds+=Math.max(0,Math.min(to,ChronicleStore.now())-open);return s;}

 private void buildItemCatalog(){
  if(!itemCatalog.isEmpty())return;
  Map<String,ItemChoice> unique=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  int count=client.getItemCount();
  for(int id=0;id<count;id++){
   try{ItemComposition c=client.getItemDefinition(id);if(c==null)continue;String n=c.getName();if(n==null||n.trim().isEmpty()||"null".equalsIgnoreCase(n))continue;if(c.getNote()!=-1||c.getPlaceholderTemplateId()!=-1)continue;unique.putIfAbsent(n,new ItemChoice(id,n));}catch(Exception ignored){}
  }
  itemCatalog.addAll(unique.values());
 }
 java.util.List<ItemChoice> searchItems(String query){
  String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
  if(q.isEmpty())return Collections.emptyList();
  java.util.List<ItemChoice> out=new ArrayList<>();
  for(ItemChoice x:itemCatalog)if(x.name.toLowerCase(Locale.ROOT).contains(q))out.add(x);
  out.sort((a,b)->{boolean as=a.name.toLowerCase(Locale.ROOT).startsWith(q),bs=b.name.toLowerCase(Locale.ROOT).startsWith(q);if(as!=bs)return as?-1:1;return a.name.compareToIgnoreCase(b.name);});
  return out.size()>100?new ArrayList<>(out.subList(0,100)):out;
 }
 java.util.List<ChronicleStore.Event> lootHistory(int itemId,String itemName){
  java.util.List<ChronicleStore.Event> out=new ArrayList<>();
  for(ChronicleStore.Event e:store.allFor(profile))if("LOOT".equals(e.type)&&e.itemId>0&&(e.itemId==itemId||(e.title!=null&&e.title.equalsIgnoreCase(itemName))))out.add(e);
  return out;
 }
 java.util.List<ChronicleStore.Event> lootHistory(){java.util.List<ChronicleStore.Event> out=new ArrayList<>();for(ChronicleStore.Event e:store.allFor(profile))if("LOOT".equals(e.type)&&e.itemId>0)out.add(e);return out;}
 String playerName(){return playerName;}
 void showSessionRecap(){if(sessionStart==0||recapOpen)return;recapOpen=true;SwingUtilities.invokeLater(()->new SessionRecapDialog(this,playerName,false,()->recapOpen=false).showDialog());}

 void exportChronicle(java.awt.Component parent){try{javax.swing.JFileChooser fc=new javax.swing.JFileChooser();fc.setDialogTitle("Export RuneChronicle backup");fc.setSelectedFile(new java.io.File("RuneChronicle-backup-"+java.time.LocalDate.now()+".json"));if(fc.showSaveDialog(parent)==javax.swing.JFileChooser.APPROVE_OPTION){store.exportBackup(fc.getSelectedFile().toPath());javax.swing.JOptionPane.showMessageDialog(parent,"Chronicle backup saved successfully.","RuneChronicle",javax.swing.JOptionPane.INFORMATION_MESSAGE);}}catch(Exception ex){javax.swing.JOptionPane.showMessageDialog(parent,"Backup failed: "+ex.getMessage(),"RuneChronicle",javax.swing.JOptionPane.ERROR_MESSAGE);}}
 void importChronicle(java.awt.Component parent){javax.swing.JFileChooser fc=new javax.swing.JFileChooser();fc.setDialogTitle("Import RuneChronicle backup");if(fc.showOpenDialog(parent)!=javax.swing.JFileChooser.APPROVE_OPTION)return;int ok=javax.swing.JOptionPane.showConfirmDialog(parent,"Importing replaces the Chronicle stored on this computer. Continue?","Import RuneChronicle",javax.swing.JOptionPane.YES_NO_OPTION);if(ok!=javax.swing.JOptionPane.YES_OPTION)return;try{int count=store.importBackup(fc.getSelectedFile().toPath());refresh();javax.swing.JOptionPane.showMessageDialog(parent,"Imported "+count+" Chronicle events.","RuneChronicle",javax.swing.JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){javax.swing.JOptionPane.showMessageDialog(parent,"Import failed: "+ex.getMessage(),"RuneChronicle",javax.swing.JOptionPane.ERROR_MESSAGE);}}
 void resetChronicle(java.awt.Component parent){int ok=javax.swing.JOptionPane.showConfirmDialog(parent,"Permanently erase all RuneChronicle history stored on this computer?\nThis cannot be undone unless you exported a backup.","Reset RuneChronicle",javax.swing.JOptionPane.YES_NO_OPTION,javax.swing.JOptionPane.WARNING_MESSAGE);if(ok==javax.swing.JOptionPane.YES_OPTION){store.reset();refresh();}}
 private void showWelcomeIfNeeded(){if(configManager.getConfiguration("runechronicle","welcomeSeen")!=null)return;javax.swing.JPanel p=new javax.swing.JPanel();p.setLayout(new javax.swing.BoxLayout(p,javax.swing.BoxLayout.Y_AXIS));p.add(new javax.swing.JLabel("RuneChronicle is ready to remember your adventure."));p.add(javax.swing.Box.createVerticalStrut(8));p.add(new javax.swing.JLabel("Tracking begins from installation — no pre-install history is invented."));p.add(new javax.swing.JLabel("Your Chronicle stays local on this computer and can be exported from Settings."));javax.swing.JOptionPane.showMessageDialog(null,p,"Welcome to RuneChronicle",javax.swing.JOptionPane.INFORMATION_MESSAGE);configManager.setConfiguration("runechronicle","welcomeSeen",true);}
  boolean trackingActive(){return trackingActive;} ItemManager itemManager(){return itemManager;} NpcPortraitService npcPortraits(){return npcPortraits;} RuneChronicleConfig config(){return config;} ConfigManager configManager(){return configManager;} void refresh(){if(panel!=null)panel.refresh();}
 private boolean isBoss(String n){return n!=null&&BOSSES.contains(n.toLowerCase(Locale.ROOT));}
 private static String friendlyActivity(String s){if(s==null)return"Activity Reward";if(s.equalsIgnoreCase("Barrows Chest")||s.equalsIgnoreCase("Barrows"))return"Barrows Chest Opened";return s+" Completed";}
 private static String activityDetail(String s,int amount){String x=s==null?"Activity":s;return amount>1?x+" · #"+amount:x+" · Loot obtained";}
 private static String category(String t){if("PET".equals(t))return"Pets";if("COLLECTION".equals(t))return"Collection Log";if("QUEST".equals(t))return"Quests";if("ACHIEVEMENT".equals(t))return"Combat Achievements";if("PB".equals(t))return"Personal Bests";if("CLUE".equals(t))return"Clues";return"Milestones";}
 private String profileId(){try{byte[]h=MessageDigest.getInstance("SHA-256").digest(Long.toString(client.getAccountHash()).getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(String.format("%02x",h[i]));return b.toString();}catch(Exception e){return"unknown";}}
 static String format(long s){long h=s/3600,m=(s%3600)/60;return h>0?h+"h "+m+"m":m+"m";}static String gp(long v){if(v>=1000000000)return String.format(Locale.US,"%.2fB",v/1000000000d);if(v>=1000000)return String.format(Locale.US,"%.2fM",v/1000000d);if(v>=1000)return String.format(Locale.US,"%.1fK",v/1000d);return String.format(Locale.US,"%,d",v);}
}
