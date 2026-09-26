package com.runechronicle;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
public class RuneChroniclePluginTest {
 public static void main(String[] args) throws Exception { ExternalPluginManager.loadBuiltin(RuneChroniclePlugin.class); RuneLite.main(args); }
}
