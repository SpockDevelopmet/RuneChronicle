package com.runechronicle;

import com.google.gson.*;
import java.io.*;
import java.time.Instant;
import net.runelite.client.util.Filepath;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ChronicleStore {
 static final int SCHEMA_VERSION=8;
 static final class Event {
  long ts; String profileId,sessionId,type,title,detail,source,category,expeditionId; long amount,value,haValue; int itemId,quantity,npcId,kc; boolean boss;
  Event(long ts,String profileId,String sessionId,String type,String title,String detail,long amount){this(ts,profileId,sessionId,type,title,detail,amount,"",0,0,0);}
  Event(long ts,String profileId,String sessionId,String type,String title,String detail,long amount,String source,long value,int itemId,int quantity){this.ts=ts;this.profileId=profileId;this.sessionId=sessionId;this.type=type;this.title=title;this.detail=detail;this.amount=amount;this.source=source;this.value=value;this.itemId=itemId;this.quantity=quantity;}
 }
 private static final Logger LOG=Logger.getLogger(ChronicleStore.class.getName());
 private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"RuneChronicle-persistence");t.setDaemon(true);return t;});
 private ScheduledFuture<?> pending;
 private long revision;
 private boolean dirty,closed;
 private final Gson gson;
 ChronicleStore(Gson gson,Filepath dir){this.gson=gson.newBuilder().setPrettyPrinting().create();this.dir=dir;this.file=dir.joinSegment("events.json");this.versionFile=dir.joinSegment("schema-version.txt");}
 private final Filepath dir,file,versionFile;
 private final List<Event> events=new ArrayList<>();
 synchronized void load(){try{dir.createDirectories();if(file.exists()){try(Reader r=file.openBufferedReader()){Event[] x=gson.fromJson(r,Event[].class);if(x!=null)events.addAll(Arrays.asList(x));}}versionFile.write(Integer.toString(SCHEMA_VERSION));}catch(Exception ignored){}}
 synchronized void add(Event e){if(closed)throw new IllegalStateException("Chronicle store is closed");events.add(e);markDirty();}
 synchronized void removeExpedition(String expeditionId){if(expeditionId==null||expeditionId.isEmpty())return;if(events.removeIf(e->expeditionId.equals(e.expeditionId)))markDirty();}
 synchronized List<Event> allFor(String p){List<Event>o=new ArrayList<>();for(Event e:events)if(p.equals(e.profileId))o.add(e);o.sort(Comparator.comparingLong(a->a.ts));return o;}
 synchronized List<Event> session(String p,String s){List<Event>o=new ArrayList<>();for(Event e:events)if(p.equals(e.profileId)&&s!=null&&s.equals(e.sessionId))o.add(e);o.sort(Comparator.comparingLong(a->a.ts));return o;}
 private void markDirty(){revision++;dirty=true;scheduleSave();}
 // Called under the state lock. Keep one pending write so sustained activity cannot starve persistence.
 private void scheduleSave(){if(!closed&&(pending==null||pending.isDone()))pending=writer.schedule(this::backgroundSave,1000,TimeUnit.MILLISECONDS);}
 private void backgroundSave(){
  synchronized(this){pending=null;}
  try{writeSnapshot();}catch(Exception ex){LOG.log(Level.WARNING,"Unable to persist Chronicle events",ex);}
  finally{synchronized(this){if(dirty)scheduleSave();}}
 }
 // Every disk operation runs on the same writer; the state lock covers only snapshot/revision work.
 private void writeSnapshot() throws IOException {
  List<Event> snapshot;long captured;
  synchronized(this){if(!dirty&&file.exists())return;snapshot=new ArrayList<>(events);captured=revision;}
  dir.createDirectories();Filepath temporary=dir.joinSegment("events.json.tmp");
  try(Writer w=temporary.openBufferedWriter()){gson.toJson(snapshot,w);}
  temporary.moveTo(file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  synchronized(this){if(revision==captured)dirty=false;}
 }
 private <T> T awaitWrite(Callable<T> action) throws IOException {
  try{return CompletableFuture.supplyAsync(()->{try{return action.call();}catch(Exception ex){throw new CompletionException(ex);}},writer).join();}
  catch(CompletionException ex){throw new IOException("Chronicle persistence failed",ex.getCause());}
  catch(RejectedExecutionException ex){throw new IOException("Chronicle store is closed",ex);}
 }
 void save(){try{awaitWrite(()->{writeSnapshot();return null;});}catch(IOException ex){LOG.log(Level.WARNING,"Unable to persist Chronicle events",ex);}}
 Filepath exportBackup(Filepath destination) throws IOException {
  return awaitWrite(()->{writeSnapshot();file.copyTo(destination,java.nio.file.StandardCopyOption.REPLACE_EXISTING);return destination;});
 }
 int importBackup(Filepath source) throws IOException {
  Event[] imported;
  try(Reader r=source.openBufferedReader()){imported=gson.fromJson(r,Event[].class);if(imported==null)throw new IOException("Backup contains no Chronicle events");}
  catch(JsonParseException ex){throw new IOException("That file is not a valid RuneChronicle backup",ex);}
  synchronized(this){if(closed)throw new IOException("Chronicle store is closed");events.clear();events.addAll(Arrays.asList(imported));markDirty();}
  awaitWrite(()->{writeSnapshot();return null;});return imported.length;
 }
 void reset(){synchronized(this){if(closed)throw new IllegalStateException("Chronicle store is closed");events.clear();markDirty();}save();}
 void close(){
  synchronized(this){if(closed)return;closed=true;if(pending!=null)pending.cancel(false);}
  try{awaitWrite(()->{writeSnapshot();return null;});}
  catch(IOException ex){LOG.log(Level.WARNING,"Unable to flush Chronicle events on shutdown",ex);}
  finally{writer.shutdown();}
 }
 Filepath dataFile(){return file;}
 static long now(){return Instant.now().getEpochSecond();}
}
