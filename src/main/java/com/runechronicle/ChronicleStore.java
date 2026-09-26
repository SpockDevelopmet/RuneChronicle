package com.runechronicle;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class ChronicleStore {
 static final int SCHEMA_VERSION=7;
 static final class Event {
  long ts; String profileId,sessionId,type,title,detail,source,category; long amount,value,haValue; int itemId,quantity,npcId,kc; boolean boss;
  Event(long ts,String profileId,String sessionId,String type,String title,String detail,long amount){this(ts,profileId,sessionId,type,title,detail,amount,"",0,0,0);}
  Event(long ts,String profileId,String sessionId,String type,String title,String detail,long amount,String source,long value,int itemId,int quantity){this.ts=ts;this.profileId=profileId;this.sessionId=sessionId;this.type=type;this.title=title;this.detail=detail;this.amount=amount;this.source=source;this.value=value;this.itemId=itemId;this.quantity=quantity;}
 }
 private static final Type LIST_TYPE=new TypeToken<List<Event>>(){}.getType();
 private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
 private final Path dir=Paths.get(System.getProperty("user.home"),".runechronicle"),file=dir.resolve("events.json"),versionFile=dir.resolve("schema-version.txt");
 private final List<Event> events=new ArrayList<>();
 synchronized void load(){try{Files.createDirectories(dir);if(Files.exists(file)){try(Reader r=Files.newBufferedReader(file)){List<Event>x=gson.fromJson(r,LIST_TYPE);if(x!=null)events.addAll(x);}}Files.write(versionFile,Integer.toString(SCHEMA_VERSION).getBytes());}catch(Exception ignored){}}
 synchronized void add(Event e){events.add(e);save();}
 synchronized List<Event> allFor(String p){List<Event>o=new ArrayList<>();for(Event e:events)if(p.equals(e.profileId))o.add(e);o.sort(Comparator.comparingLong(a->a.ts));return o;}
 synchronized List<Event> session(String p,String s){List<Event>o=new ArrayList<>();for(Event e:events)if(p.equals(e.profileId)&&s!=null&&s.equals(e.sessionId))o.add(e);o.sort(Comparator.comparingLong(a->a.ts));return o;}
 synchronized void save(){try{Files.createDirectories(dir);Path t=dir.resolve("events.json.tmp");try(Writer w=Files.newBufferedWriter(t)){gson.toJson(events,LIST_TYPE,w);}try{Files.move(t,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception ex){Files.move(t,file,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception ignored){}}
 synchronized Path exportBackup(Path destination) throws IOException {
  save(); Files.createDirectories(destination.getParent()); Files.copy(file,destination,StandardCopyOption.REPLACE_EXISTING); return destination;
 }
 synchronized int importBackup(Path source) throws IOException {
  try(Reader r=Files.newBufferedReader(source)){List<Event>x=gson.fromJson(r,LIST_TYPE);if(x==null)throw new IOException("Backup contains no Chronicle events");events.clear();events.addAll(x);save();return events.size();}
  catch(JsonParseException ex){throw new IOException("That file is not a valid RuneChronicle backup",ex);}
 }
 synchronized void reset(){events.clear();save();}
 Path dataFile(){return file;}
 static long now(){return Instant.now().getEpochSecond();}
}
