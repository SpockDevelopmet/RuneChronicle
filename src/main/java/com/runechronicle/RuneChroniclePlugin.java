package com.runechronicle;

import com.google.inject.Provides;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
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
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;

@PluginDescriptor(name="RuneChronicle",internalName="runechronicle",description="Your RuneScape journey, automatically recorded.",tags={"history","session","xp","journal","recap","progress","loot","kills","boss","collection","collectionlog"})
public class RuneChroniclePlugin extends Plugin {
 @Inject private Client client; @Inject private OkHttpClient httpClient; @Inject private Gson gson; @Inject private ClientToolbar toolbar; @Inject private ClientThread clientThread; @Inject private ItemManager itemManager; @Inject private RuneChronicleConfig config; @Inject private ConfigManager configManager; @Inject private OverlayManager overlayManager; @Inject private RecapOverlay recapOverlay;
 private ChronicleStore store; private final Map<Skill,Integer> lastXp=new EnumMap<>(Skill.class),lastLevel=new EnumMap<>(Skill.class); private final Map<Integer,Integer> inventory=new HashMap<>(),equipment=new HashMap<>(); private final List<PendingLoot> pendingLoot=new ArrayList<>(); private final Map<String,Integer> sessionBossCounts=new HashMap<>();
 private final Set<Integer> alwaysTrackedSupplies=new LinkedHashSet<>(); private boolean expeditionActive; private String expeditionId; private long expeditionStart; private final Map<Integer,Long> expeditionUsed=new LinkedHashMap<>();
 private NpcPortraitService npcPortraits;
 private long sessionStart; private String sessionId,profile="unknown",playerName="RuneScape Adventurer"; private final java.util.List<ItemChoice> itemCatalog=new ArrayList<>(); private final java.util.List<MonsterChoice> monsterCatalog=new ArrayList<>(); private RuneChroniclePanel panel; private NavigationButton nav; private boolean recapOpen,trackingActive; private int baselineTicksRemaining; private NPC lastInteractingNpc;
 private static final long CLAIM_WINDOW=35;
 private static final Pattern QUEST=Pattern.compile("(?i)(?:congratulations[,! ]+)?(?:you have completed|you've completed) (?:the )?(.+?)(?: quest)?[!.]?$"), COLLECTION_ITEM=Pattern.compile("(?i)New item added to your collection log: (.*)"), PB=Pattern.compile("(?i).*(?:new personal best|personal best).*"), CA=Pattern.compile("(?i).*(?:combat task|combat achievement).*completed.*"), PET=Pattern.compile("(?i).*funny feeling like you're being followed.*"), CLUE=Pattern.compile("(?i).*(?:treasure trail|clue scroll).*completed.*");
 private static final Set<String> BOSSES=new HashSet<>(Arrays.asList("abyssal sire","alchemical hydra","amoxliatl","araxxor","artio","barrows","bryophyta","callisto","calvar'ion","cerberus","chaos elemental","chaos fanatic","commander zilyana","corporeal beast","crazy archaeologist","dagannoth prime","dagannoth rex","dagannoth supreme","deranged archaeologist","duke sucellus","general graardor","giant mole","grotesque guardians","hespori","kalphite queen","king black dragon","kraken","kreearra","k'ril tsutsaroth","mimic","nex","nightmare","obor","phantom muspah","phosani's nightmare","sarachnis","scorpia","scurrius","skotizo","tempoross","the gauntlet","the corrupted gauntlet","the hueycoatl","the leviathan","the whisperer","thermonuclear smoke devil","vardorvis","venenatis","vet'ion","vorkath","wintertodt","yama","zalcano","zulrah"));
 static final class ItemChoice { final int id; final String name; ItemChoice(int id,String name){this.id=id;this.name=name;} public String toString(){return name;} }
 static final class MonsterChoice { final int id; final String name; MonsterChoice(int id,String name){this.id=id;this.name=name;} public String toString(){return name;} }
 static final class PendingLoot {int itemId,remaining;String source;long ts;PendingLoot(int i,int q,String s,long t){itemId=i;remaining=q;source=s;ts=t;}}
 static final class ExpeditionSnapshot {final boolean active;final long seconds,loot,supplies,kills;final Map<Integer,Long> used;ExpeditionSnapshot(boolean a,long sec,long l,long sp,long k,Map<Integer,Long>u){active=a;seconds=sec;loot=l;supplies=sp;kills=k;used=u;}long net(){return loot-supplies;}}
 static final class ProfitRun {String id;long start,end,loot,supplies,kills;final Map<String,Long> monsters=new LinkedHashMap<>();final List<ChronicleStore.Event> events=new ArrayList<>();long net(){return loot-supplies;}long seconds(){return Math.max(0,end-start);}String primaryMonster(){return monsters.size()==1?monsters.keySet().iterator().next():monsters.isEmpty()?"No kills":"Mixed activity";}}
 static final class MonsterProfit {String name;long kills,loot,supplies,seconds;int runs;final List<ProfitRun> history=new ArrayList<>();long net(){return loot-supplies;}}
 static final class Summary {long playSeconds,xp,levels,kills,bossKills,lootValue,lootHaValue,lootItems,deaths,supplyCost;Map<String,Long>skillXp=new HashMap<>(),killCounts=new HashMap<>(),bossCounts=new HashMap<>();List<ChronicleStore.Event>events=new ArrayList<>();}
 @Provides RuneChronicleConfig provideConfig(ConfigManager m){return m.getConfig(RuneChronicleConfig.class);}
 @Override protected void startUp(){try{store=new ChronicleStore(gson,getPluginDirectory());}catch(IOException ex){throw new RuntimeException("Unable to open RuneChronicle data directory",ex);}store.load();loadTrackedSupplies();npcPortraits=new NpcPortraitService(httpClient,gson);panel=new RuneChroniclePanel(this,itemManager,npcPortraits);BufferedImage icon=ImageUtil.loadImageResource(getClass(),"runechronicle_icon.png");nav=NavigationButton.builder().tooltip("RuneChronicle").icon(icon).priority(6).panel(panel).build();toolbar.addNavigation(nav);overlayManager.add(recapOverlay);SwingUtilities.invokeLater(this::showWelcomeIfNeeded);if(client.getGameState()==GameState.LOGGED_IN)beginSession();}
 @Override protected void shutDown(){if(nav!=null)toolbar.removeNavigation(nav);overlayManager.remove(recapOverlay);endSession("Plugin stopped");}
 @Subscribe public void onPostMenuSort(PostMenuSort e){
  if(!config.showGameRecapButton()||!trackingActive||recapOpen||client.isMenuOpen())return;
  java.awt.Rectangle b=recapOverlay.getBounds(); net.runelite.api.Point m=client.getMouseCanvasPosition();
  if(b==null||b.isEmpty()||m==null||!b.contains(m.getX(),m.getY()))return;
  int localX=m.getX()-b.x;
  if(localX>=recapOverlay.trackerStartX()) client.createMenuEntry(-1).setOption(expeditionActive?"Stop profit tracking":"Start profit tracking").setTarget("<col=f2bb3b>RuneChronicle</col>").setType(MenuAction.RUNELITE).onClick(x->{if(expeditionActive)endExpedition();else startExpedition();});
  else client.createMenuEntry(-1).setOption("Open recap").setTarget("<col=f2bb3b>RuneChronicle</col>").setType(MenuAction.RUNELITE).onClick(x->showSessionRecap());
 }
 @Subscribe public void onGameStateChanged(GameStateChanged e){if(e.getGameState()==GameState.LOGGED_IN&&sessionStart==0)beginSession();if(e.getGameState()==GameState.HOPPING&&sessionStart>0){trackingActive=false;baselineTicksRemaining=2;lastXp.clear();lastLevel.clear();inventory.clear();pendingLoot.clear();lastInteractingNpc=null;refresh();}if(e.getGameState()==GameState.LOGIN_SCREEN&&sessionStart>0)endSession("Logged out");}
 @Subscribe public void onGameTick(GameTick e){if(client.getLocalPlayer()!=null&&client.getLocalPlayer().getInteracting() instanceof NPC)lastInteractingNpc=(NPC)client.getLocalPlayer().getInteracting();long now=ChronicleStore.now();pendingLoot.removeIf(x->now-x.ts>CLAIM_WINDOW||x.remaining<=0);if(sessionStart==0||trackingActive)return;if(baselineTicksRemaining>0){baselineTicksRemaining--;return;}captureBaseline();captureInventory();trackingActive=true;refresh();}
 @Subscribe public void onStatChanged(StatChanged e){if(sessionStart==0||!trackingActive)return;Skill sk=e.getSkill();if(sk==Skill.OVERALL)return;int newXp=e.getXp(),newLv=Experience.getLevelForXp(newXp);Integer oldXp=lastXp.get(sk),oldLv=lastLevel.get(sk);if(oldXp==null||oldLv==null||newXp<oldXp){lastXp.put(sk,newXp);lastLevel.put(sk,newLv);return;}if(newXp>oldXp){long g=(long)newXp-oldXp;store.add(event("XP",sk.getName(),"+"+g+" XP",g));}if(newLv>oldLv)for(int lv=oldLv+1;lv<=newLv;lv++)store.add(event("LEVEL",sk.getName()+" level "+lv,"Reached level "+lv,1));lastXp.put(sk,newXp);lastLevel.put(sk,newLv);refresh();}
 @Subscribe public void onActorDeath(ActorDeath e){if(!trackingActive)return;if(e.getActor()==client.getLocalPlayer()){if(config.logDeaths()){store.add(event("DEATH","You died","Adventure interrupted",1));refresh();}return;}if(!config.logMonsterKills()||!(e.getActor() instanceof NPC))return;NPC n=(NPC)e.getActor();Player me=client.getLocalPlayer();if(n!=lastInteractingNpc&&(me==null||n.getInteracting()!=me))return;String name=n.getName()==null?"Unknown monster":n.getName();boolean boss=isBoss(name);String type=boss&&config.logBossKills()?"BOSS_KILL":"KILL";ChronicleStore.Event ev=event(type,name,boss?"Boss defeated":"Defeated",1);try{ev.npcId=n.getId();}catch(Exception ignored){}ev.boss=boss;if(boss){int kc=sessionBossCounts.merge(name,1,Integer::sum);ev.kc=kc;ev.category="Bosses Defeated";}store.add(ev);if(n==lastInteractingNpc)lastInteractingNpc=null;refresh();}
 @Subscribe public void onNpcLootReceived(NpcLootReceived e){if(!trackingActive||!config.logLoot())return;String source=e.getNpc()!=null&&e.getNpc().getName()!=null?e.getNpc().getName():"Monster loot";long now=ChronicleStore.now();for(ItemStack s:e.getItems()){int id=itemManager.canonicalize(s.getId());pendingLoot.add(new PendingLoot(id,s.getQuantity(),source,now));}}
 @Subscribe public void onLootReceived(LootReceived e){if(!trackingActive||!config.logActivities()||e==null)return;String source=e.getName()==null?"Activity reward":e.getName();String type=e.getType()==null?"":e.getType().toString();if(type.toUpperCase(Locale.ROOT).contains("NPC"))return;ChronicleStore.Event activity=event("ACTIVITY",friendlyActivity(source),activityDetail(source,e.getAmount()),1);activity.source=source;activity.category="Activities";store.add(activity);if(config.logLoot())for(ItemStack s:e.getItems())recordAwardedLoot(source,itemManager.canonicalize(s.getId()),s.getQuantity(),true);refresh();}
 @Subscribe public void onItemContainerChanged(ItemContainerChanged e){if(!trackingActive)return;ItemContainer c=e.getItemContainer();if(c==null)return;if(e.getContainerId()==InventoryID.INVENTORY.getId()){Map<Integer,Integer> now=counts(c);Set<Integer> ids=new HashSet<>(inventory.keySet());ids.addAll(now.keySet());for(int id:ids){int old=inventory.getOrDefault(id,0),cur=now.getOrDefault(id,0),delta=cur-old;if(delta>0&&config.logLoot())claim(id,delta);else if(delta<0&&shouldTrackSupply(id)&&!isAmmo(id))recordSupplyUse(id,-delta);}inventory.clear();inventory.putAll(now);}else if(e.getContainerId()==InventoryID.EQUIPMENT.getId()){Map<Integer,Integer> now=counts(c);Set<Integer> ids=new HashSet<>(equipment.keySet());ids.addAll(now.keySet());for(int id:ids){int used=equipment.getOrDefault(id,0)-now.getOrDefault(id,0);if(used>0&&used<=5&&isAmmo(id)&&shouldTrackSupply(id))recordSupplyUse(id,used);}equipment.clear();equipment.putAll(now);}}
 private void claim(int itemId,int gained){long now=ChronicleStore.now();for(PendingLoot p:new ArrayList<>(pendingLoot)){if(gained<=0)break;if(p.itemId!=itemId||p.remaining<=0||now-p.ts>CLAIM_WINDOW)continue;int q=Math.min(gained,p.remaining);p.remaining-=q;gained-=q;recordAwardedLoot(p.source,itemId,q,false);}pendingLoot.removeIf(x->x.remaining<=0);}
 private void recordAwardedLoot(String source,int id,int q,boolean special){ItemComposition c=itemManager.getItemComposition(id);String name=c==null?("Item "+id):c.getName();long ge=Math.max(0L,itemManager.getItemPrice(id));long ha=c==null?0L:Math.max(0,c.getHaPrice());long geTotal=(long)ge*q,haTotal=(long)ha*q;long geCheck=config.useStackValue()?geTotal:ge,haCheck=config.useStackValue()?haTotal:ha;if(!(special&&config.alwaysLogSpecial())&&!qualifies(geCheck,haCheck))return;String detail=(q>1?q+" × ":"")+name+"  •  GE "+gp(geTotal)+"  •  HA "+gp(haTotal);ChronicleStore.Event ev=new ChronicleStore.Event(ChronicleStore.now(),profile,sessionId,"LOOT",name,detail,geTotal,source,geTotal,id,q);ev.haValue=haTotal;ev.category=special?"Reward Loot":"Claimed Loot";if(expeditionActive)ev.expeditionId=expeditionId;store.add(ev);}
 private boolean qualifies(long ge,long ha){int g=config.minimumGeValue(),h=config.minimumHaValue();switch(config.lootValueRule()){case GE_ONLY:return g==0||ge>=g;case HA_ONLY:return h==0||ha>=h;case GE_AND_HA:return (g==0||ge>=g)&&(h==0||ha>=h);default:return (g==0&&h==0)||(g>0&&ge>=g)||(h>0&&ha>=h);}}
 @Subscribe public void onChatMessage(ChatMessage e){if(!trackingActive||!config.logGameMilestones())return;if(e.getType()!=ChatMessageType.GAMEMESSAGE&&e.getType()!=ChatMessageType.SPAM)return;String m=Text.removeTags(e.getMessage()).trim();String type=null,title=null;Matcher q=QUEST.matcher(m),cl=COLLECTION_ITEM.matcher(m);if(q.find()){type="QUEST";title=q.group(1);}else if(cl.find()){title=cl.group(1).trim();type="COLLECTION";}else if(CA.matcher(m).matches()){type="ACHIEVEMENT";title="Combat Achievement";}else if(PB.matcher(m).matches()){type="PB";title="New Personal Best";}else if(PET.matcher(m).matches()){type="PET";title="New Pet!";}else if(CLUE.matcher(m).matches()){type="CLUE";title="Clue Completed";}if(type!=null){ChronicleStore.Event ev=event(type,title,m,1);ev.category=category(type);if("COLLECTION".equals(type)||"PET".equals(type))tryAttachItem(ev,title);store.add(ev);refresh();}}
 private void tryAttachItem(ChronicleStore.Event ev,String query){try{java.util.List<net.runelite.http.api.item.ItemPrice> r=itemManager.search(query);if(r!=null&&!r.isEmpty())ev.itemId=r.get(0).getId();}catch(Exception ignored){}}
 // RuneChronicle intentionally does not intercept RuneScape logout or world-switch controls.
 private ChronicleStore.Event event(String t,String title,String detail,long amount){ChronicleStore.Event e=new ChronicleStore.Event(ChronicleStore.now(),profile,sessionId,t,title,detail,amount);if(expeditionActive)e.expeditionId=expeditionId;return e;}
 private void beginSession(){buildItemCatalog();buildMonsterCatalog();profile=profileId();sessionStart=ChronicleStore.now();sessionId=profile+"-"+sessionStart;if(client.getLocalPlayer()!=null&&client.getLocalPlayer().getName()!=null)playerName=client.getLocalPlayer().getName();lastXp.clear();lastLevel.clear();inventory.clear();equipment.clear();pendingLoot.clear();sessionBossCounts.clear();trackingActive=false;baselineTicksRemaining=2;store.add(event("SESSION_START","Session started","Establishing account baseline",0));refresh();}
 private void captureBaseline(){lastXp.clear();lastLevel.clear();for(Skill sk:Skill.values()){if(sk==Skill.OVERALL)continue;int xp=client.getSkillExperience(sk);if(xp<0)continue;lastXp.put(sk,xp);lastLevel.put(sk,Experience.getLevelForXp(xp));}}
 private void captureInventory(){inventory.clear();equipment.clear();ItemContainer inv=client.getItemContainer(InventoryID.INVENTORY),eq=client.getItemContainer(InventoryID.EQUIPMENT);if(inv!=null)inventory.putAll(counts(inv));if(eq!=null)equipment.putAll(counts(eq));}
 private Map<Integer,Integer> counts(ItemContainer c){Map<Integer,Integer>m=new HashMap<>();for(Item i:c.getItems())if(i!=null&&i.getId()>0&&i.getQuantity()>0)m.merge(itemManager.canonicalize(i.getId()),i.getQuantity(),Integer::sum);return m;}
 private void endSession(String reason){if(sessionStart==0)return;long now=ChronicleStore.now();store.add(new ChronicleStore.Event(now,profile,sessionId,"SESSION_END","Session complete",reason+" · "+format(now-sessionStart),now-sessionStart));sessionStart=0;sessionId=null;lastXp.clear();lastLevel.clear();inventory.clear();equipment.clear();pendingLoot.clear();trackingActive=false;baselineTicksRemaining=0;recapOpen=false;lastInteractingNpc=null;refresh();}
 Summary summary(String range){Summary s=new Summary();long now=ChronicleStore.now(),from=0;ZoneId z=ZoneId.systemDefault();ZonedDateTime n=Instant.ofEpochSecond(now).atZone(z);List<ChronicleStore.Event>all;if("SESSION".equals(range))all=sessionId==null?Collections.emptyList():store.session(profile,sessionId);else{if("TODAY".equals(range))from=n.toLocalDate().atStartOfDay(z).toEpochSecond();else if("WEEK".equals(range))from=n.toLocalDate().minusDays(n.getDayOfWeek().getValue()-1).atStartOfDay(z).toEpochSecond();else if("MONTH".equals(range))from=n.toLocalDate().withDayOfMonth(1).atStartOfDay(z).toEpochSecond();all=store.allFor(profile);}long open=0;for(ChronicleStore.Event e:all){if(e.ts<from)continue;s.events.add(e);switch(e.type){case"XP":s.xp+=e.amount;s.skillXp.merge(e.title,e.amount,Long::sum);break;case"LEVEL":s.levels+=e.amount;break;case"KILL":s.kills+=e.amount;s.killCounts.merge(e.title,e.amount,Long::sum);break;case"BOSS_KILL":s.kills+=e.amount;s.bossKills+=e.amount;s.bossCounts.merge(e.title,e.amount,Long::sum);break;case"LOOT":s.lootValue+=e.value;s.lootHaValue+=e.haValue;s.lootItems+=e.quantity;break;case"SUPPLY":s.supplyCost+=e.value;break;case"DEATH":s.deaths+=e.amount;break;}if("SESSION_START".equals(e.type))open=e.ts;else if("SESSION_END".equals(e.type)&&open>0){s.playSeconds+=Math.max(0,e.ts-open);open=0;}}if(open>0)s.playSeconds+=Math.max(0,now-open);return s;}
 Summary summary(long from,long to){Summary s=new Summary();long open=0;for(ChronicleStore.Event e:store.allFor(profile)){if(e.ts<from||e.ts>to)continue;s.events.add(e);switch(e.type){case"XP":s.xp+=e.amount;s.skillXp.merge(e.title,e.amount,Long::sum);break;case"LEVEL":s.levels+=e.amount;break;case"KILL":s.kills+=e.amount;s.killCounts.merge(e.title,e.amount,Long::sum);break;case"BOSS_KILL":s.kills+=e.amount;s.bossKills+=e.amount;s.bossCounts.merge(e.title,e.amount,Long::sum);break;case"LOOT":s.lootValue+=e.value;s.lootHaValue+=e.haValue;s.lootItems+=e.quantity;break;case"SUPPLY":s.supplyCost+=e.value;break;case"DEATH":s.deaths+=e.amount;break;}if("SESSION_START".equals(e.type))open=e.ts;else if("SESSION_END".equals(e.type)&&open>0){s.playSeconds+=Math.max(0,e.ts-open);open=0;}}if(open>0)s.playSeconds+=Math.max(0,Math.min(to,ChronicleStore.now())-open);return s;}

 private void buildMonsterCatalog(){
  if(!monsterCatalog.isEmpty())return;
  Map<String,MonsterChoice> unique=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  int[] npcIds=client.getIndexConfig().getFileIds(9);
  if(npcIds==null)return;
  for(int id:npcIds){
   try{
    NPCComposition c=client.getNpcDefinition(id);
    if(c==null)continue;
    String name=c.getName();
    if(name==null||name.trim().isEmpty()||"null".equalsIgnoreCase(name))continue;
    boolean attackable=c.getCombatLevel()>0;
    String[] actions=c.getActions();
    if(actions!=null)for(String a:actions)if(a!=null&&"attack".equalsIgnoreCase(a)){attackable=true;break;}
    if(!attackable||c.isFollower())continue;
    unique.putIfAbsent(name,new MonsterChoice(id,name));
   }catch(Exception ignored){}
  }
  monsterCatalog.addAll(unique.values());
 }
 java.util.List<MonsterChoice> searchMonsters(String query,int limit){
  String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
  if(q.isEmpty())return Collections.emptyList();
  java.util.List<MonsterChoice> out=new ArrayList<>();
  for(MonsterChoice m:monsterCatalog)if(m.name.toLowerCase(Locale.ROOT).contains(q))out.add(m);
  out.sort((a,b)->{boolean as=a.name.toLowerCase(Locale.ROOT).startsWith(q),bs=b.name.toLowerCase(Locale.ROOT).startsWith(q);if(as!=bs)return as?-1:1;return a.name.compareToIgnoreCase(b.name);});
  return out.subList(0,Math.min(limit,out.size()));
 }
 MonsterProfit monsterProfit(String name){
  if(name==null)return null;
  for(MonsterProfit m:monsterProfitHistory())if(m.name.equalsIgnoreCase(name))return m;
  return null;
 }
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
 private boolean shouldTrackSupply(int id){return alwaysTrackedSupplies.contains(id)||(expeditionActive&&isLikelySupply(id));}
 private boolean isAmmo(int id){try{String n=itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT);return n.contains(" bolt")||n.contains(" arrow")||n.contains(" dart")||n.contains(" javelin")||n.contains(" cannonball")||n.contains(" chinchompa");}catch(Exception ex){return false;}}
 private boolean isLikelySupply(int id){try{String n=itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT);return n.matches(".*\\([1-4]\\)$")||n.endsWith(" rune")||n.contains(" bolt")||n.contains(" arrow")||n.contains(" dart")||n.contains(" javelin")||n.contains(" cannonball")||n.contains(" chinchompa")||n.contains(" zulrah's scales")||n.contains(" revenant ether")||n.contains(" teleport")||n.contains(" tablet")||n.contains(" tab")||n.contains(" food")||n.contains(" shark")||n.contains(" karambwan")||n.contains(" anglerfish")||n.contains(" manta ray")||n.contains(" monkfish")||n.contains(" swordfish")||n.contains(" tuna")||n.contains(" lobster")||n.contains(" cake")||n.contains(" pie")||n.contains(" brew")||n.contains(" restore")||n.contains(" potion");}catch(Exception ex){return false;}}
 private void recordSupplyUse(int id,int q){if(q<=0)return;ItemComposition c=itemManager.getItemComposition(id);String name=c==null?("Item "+id):c.getName();long ge=Math.max(0L,itemManager.getItemPrice(id));java.util.regex.Matcher dose=java.util.regex.Pattern.compile(".*\\(([1-4])\\)$").matcher(name);if(dose.matches()){int d=Integer.parseInt(dose.group(1));if(d>0)ge=Math.max(1L,ge/d);}long value=(long)ge*q;ChronicleStore.Event ev=new ChronicleStore.Event(ChronicleStore.now(),profile,sessionId,"SUPPLY",name,"Used ×"+q+" · "+gp(ge)+" gp ea · "+gp(value)+" total",value,"Supplies",value,id,q);ev.category="Supplies Used";ev.expeditionId=expeditionActive?expeditionId:null;store.add(ev);if(expeditionActive)expeditionUsed.merge(id,(long)q,Long::sum);refresh();}
 boolean expeditionActive(){return expeditionActive;}
 void startExpedition(){if(expeditionActive||sessionStart==0)return;expeditionActive=true;expeditionStart=ChronicleStore.now();expeditionId=profile+"-exp-"+expeditionStart;expeditionUsed.clear();ChronicleStore.Event e=event("EXPEDITION_START","Profit tracking started","Boss/monster profit and supply tracking active",0);e.expeditionId=expeditionId;store.add(e);refresh();}
 void endExpedition(){if(!expeditionActive)return;ExpeditionSnapshot snap=expeditionSnapshot();if(snap.kills==0&&snap.loot==0&&snap.supplies==0){store.removeExpedition(expeditionId);}else{ChronicleStore.Event e=event("EXPEDITION_END","Profit tracking complete","Loot "+gp(snap.loot)+" · Supplies "+gp(snap.supplies)+" · Net "+gp(snap.net()),snap.net());e.expeditionId=expeditionId;store.add(e);}expeditionActive=false;expeditionId=null;expeditionStart=0;expeditionUsed.clear();refresh();}
 ExpeditionSnapshot expeditionSnapshot(){if(!expeditionActive)return new ExpeditionSnapshot(false,0,0,0,0,new LinkedHashMap<>());long loot=0,supplies=0,kills=0;for(ChronicleStore.Event e:store.allFor(profile)){if(!Objects.equals(expeditionId,e.expeditionId))continue;if("LOOT".equals(e.type))loot+=e.value;else if("SUPPLY".equals(e.type))supplies+=e.value;else if("KILL".equals(e.type)||"BOSS_KILL".equals(e.type))kills+=e.amount;}return new ExpeditionSnapshot(true,Math.max(0,ChronicleStore.now()-expeditionStart),loot,supplies,kills,new LinkedHashMap<>(expeditionUsed));}
 java.util.List<ProfitRun> profitRuns(){
  Map<String,ProfitRun> map=new LinkedHashMap<>();
  for(ChronicleStore.Event e:store.allFor(profile)){if(e.expeditionId==null||e.expeditionId.isEmpty())continue;ProfitRun r=map.computeIfAbsent(e.expeditionId,k->{ProfitRun x=new ProfitRun();x.id=k;return x;});r.events.add(e);if("EXPEDITION_START".equals(e.type))r.start=e.ts;else if("EXPEDITION_END".equals(e.type))r.end=e.ts;else if("LOOT".equals(e.type))r.loot+=e.value;else if("SUPPLY".equals(e.type))r.supplies+=e.value;else if("KILL".equals(e.type)||"BOSS_KILL".equals(e.type)){r.kills+=e.amount;r.monsters.merge(e.title,e.amount,Long::sum);}}
  java.util.List<ProfitRun> out=new ArrayList<>();for(ProfitRun r:map.values())if(r.start>0&&r.end>0&&(r.kills>0||r.loot>0||r.supplies>0))out.add(r);out.sort((a,b)->Long.compare(b.start,a.start));return out;
 }
 java.util.List<MonsterProfit> monsterProfitHistory(){
  Map<String,MonsterProfit> map=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  for(ProfitRun r:profitRuns())for(Map.Entry<String,Long> me:r.monsters.entrySet()){MonsterProfit m=map.computeIfAbsent(me.getKey(),k->{MonsterProfit x=new MonsterProfit();x.name=k;return x;});m.kills+=me.getValue();m.runs++;m.history.add(r);if(r.monsters.size()==1){m.loot+=r.loot;m.supplies+=r.supplies;m.seconds+=r.seconds();}else{for(ChronicleStore.Event e:r.events)if("LOOT".equals(e.type)&&e.source!=null&&e.source.equalsIgnoreCase(m.name))m.loot+=e.value;}}
  java.util.List<MonsterProfit> out=new ArrayList<>(map.values());out.sort((a,b)->Long.compare(b.kills,a.kills));return out;
 }
 java.util.List<ChronicleStore.Event> eventsForProfitRun(String id){for(ProfitRun r:profitRuns())if(Objects.equals(r.id,id))return new ArrayList<>(r.events);return Collections.emptyList();}
 Set<Integer> trackedSupplyIds(){return new LinkedHashSet<>(alwaysTrackedSupplies);}
 void addTrackedSupply(int id){alwaysTrackedSupplies.add(id);saveTrackedSupplies();refresh();}
 void removeTrackedSupply(int id){alwaysTrackedSupplies.remove(id);saveTrackedSupplies();refresh();}
 private void loadTrackedSupplies(){alwaysTrackedSupplies.clear();String raw=configManager.getConfiguration("runechronicle","trackedSupplies");if(raw==null||raw.trim().isEmpty())return;for(String x:raw.split(","))try{alwaysTrackedSupplies.add(Integer.parseInt(x.trim()));}catch(Exception ignored){}}
 private void saveTrackedSupplies(){StringBuilder b=new StringBuilder();for(int id:alwaysTrackedSupplies){if(b.length()>0)b.append(',');b.append(id);}configManager.setConfiguration("runechronicle","trackedSupplies",b.toString());}
 String playerName(){return playerName;}
 void showSessionRecap(){if(sessionStart==0||recapOpen)return;recapOpen=true;SwingUtilities.invokeLater(()->new SessionRecapDialog(this,playerName,false,()->recapOpen=false).showDialog());}

 void exportChronicle(java.awt.Component parent){try{Filepath.Chooser chooser=new Filepath.Chooser().setIsSave().setAcceptsFiles().setDialogTitle("Export RuneChronicle backup").setFileName("RuneChronicle-backup-"+java.time.LocalDate.now()+".json").setDefaultExtension("json").addExtensionFilter("JSON files","json");java.util.List<Filepath> selected=chooser.showDialog(parent);if(selected==null||selected.isEmpty())return;store.exportBackup(selected.get(0));javax.swing.JOptionPane.showMessageDialog(parent,"Chronicle backup saved successfully.","RuneChronicle",javax.swing.JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){javax.swing.JOptionPane.showMessageDialog(parent,"Backup failed: "+ex.getMessage(),"RuneChronicle",javax.swing.JOptionPane.ERROR_MESSAGE);}}
 void importChronicle(java.awt.Component parent){try{Filepath.Chooser chooser=new Filepath.Chooser().setIsOpen().setAcceptsFiles().setDialogTitle("Import RuneChronicle backup").addExtensionFilter("JSON files","json");java.util.List<Filepath> selected=chooser.showDialog(parent);if(selected==null||selected.isEmpty())return;int ok=javax.swing.JOptionPane.showConfirmDialog(parent,"Importing replaces the Chronicle stored on this computer. Continue?","Import RuneChronicle",javax.swing.JOptionPane.YES_NO_OPTION);if(ok!=javax.swing.JOptionPane.YES_OPTION)return;int count=store.importBackup(selected.get(0));refresh();javax.swing.JOptionPane.showMessageDialog(parent,"Imported "+count+" Chronicle events.","RuneChronicle",javax.swing.JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){javax.swing.JOptionPane.showMessageDialog(parent,"Import failed: "+ex.getMessage(),"RuneChronicle",javax.swing.JOptionPane.ERROR_MESSAGE);}}
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
