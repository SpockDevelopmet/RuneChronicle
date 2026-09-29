package com.runechronicle;

import com.google.gson.*;
import java.io.*;
import java.time.Instant;
import net.runelite.client.util.Filepath;
import java.util.*;

final class ChronicleStore {
 static final int SCHEMA_VERSION=8;
 static final class Event {
  long ts; String profileId,sessionId,type,title,detail,source,category,expeditionId; long amount,value,haValue; int itemId,quantity,npcId,kc; boolean boss;
  Event(long ts,String profileId,String sessionId,String type,String title,String detail,long amount){this(ts,profileId,sessionId,type,title,detail,amount,"",0,0,0);}
  Event(long ts,String profileId,String sessionId,String type,String title,String detail,long amount,String source,long value,int itemId,int quantity){this.ts=ts;this.profileId=profileId;this.sessionId=sessionId;this.type=type;this.title=title;this.detail=detail;this.amount=amount;this.source=source;this.value=value;this.itemId=itemId;this.quantity=quantity;}
 }
 private final Gson gson;
 ChronicleStore(Gson gson,Filepath dir){this.gson=gson.newBuilder().setPrettyPrinting().create();this.dir=dir;this.file=dir.joinSegment("events.json");this.versionFile=dir.joinSegment("schema-version.txt");}
 private final Filepath dir,file,versionFile;
 private final List<Event> events=new ArrayList<>();
 synchronized void load(){try{dir.createDirectories();if(file.exists()){try(Reader r=file.openBufferedReader()){Event[] x=gson.fromJson(r,Event[].class);if(x!=null)events.addAll(Arrays.asList(x));}}versionFile.write(Integer.toString(SCHEMA_VERSION));}catch(Exception ignored){}}
 synchronized void add(Event e){events.add(e);save();}
 synchronized void removeExpedition(String expeditionId){if(expeditionId==null||expeditionId.isEmpty())return;events.removeIf(e->expeditionId.equals(e.expeditionId));save();}
 synchronized List<Event> allFor(String p){List<Event>o=new ArrayList<>();for(Event e:events)if(p.equals(e.profileId))o.add(e);o.sort(Comparator.comparingLong(a->a.ts));return o;}
 synchronized List<Event> session(String p,String s){List<Event>o=new ArrayList<>();for(Event e:events)if(p.equals(e.profileId)&&s!=null&&s.equals(e.sessionId))o.add(e);o.sort(Comparator.comparingLong(a->a.ts));return o;}
 synchronized void save(){try{dir.createDirectories();Filepath t=dir.joinSegment("events.json.tmp");try(Writer w=t.openBufferedWriter()){gson.toJson(events,w);}t.moveTo(file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}}
 synchronized Filepath exportBackup(Filepath destination) throws IOException {
  save(); file.copyTo(destination,java.nio.file.StandardCopyOption.REPLACE_EXISTING); return destination;
 }
 synchronized int importBackup(Filepath source) throws IOException {
  try(Reader r=source.openBufferedReader()){Event[] x=gson.fromJson(r,Event[].class);if(x==null)throw new IOException("Backup contains no Chronicle events");events.clear();events.addAll(Arrays.asList(x));save();return events.size();}
  catch(JsonParseException ex){throw new IOException("That file is not a valid RuneChronicle backup",ex);}
 }
 synchronized void reset(){events.clear();save();}
 Filepath dataFile(){return file;}
 static long now(){return Instant.now().getEpochSecond();}
}
