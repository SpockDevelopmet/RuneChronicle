package com.runechronicle;

import net.runelite.client.config.*;

@ConfigGroup("runechronicle")
public interface RuneChronicleConfig extends Config {
 @ConfigSection(name="Journal", description="Choose what RuneChronicle records", position=0) String journal="journal";
 @ConfigSection(name="Loot", description="Control claimed-loot logging. The value filter can intentionally exclude lower-value drops from the Chronicle.", position=1) String loot="loot";
 @ConfigSection(name="Recap", description="Control the Session Recap experience", position=2) String recap="recap";
 @ConfigItem(keyName="logMonsterKills", name="Monster kills", description="Record attributed monster kills", position=0, section=journal) default boolean logMonsterKills(){return true;}
 @ConfigItem(keyName="logBossKills", name="Bosses defeated", description="Promote recognized boss kills into their own Bosses Defeated section", position=1, section=journal) default boolean logBossKills(){return true;}
 @ConfigItem(keyName="logActivities", name="Activities & reward chests", description="Record supported raids, minigames, clues and reward chests", position=2, section=journal) default boolean logActivities(){return true;}
 @ConfigItem(keyName="logGameMilestones", name="Milestones", description="Record quests, collection log, combat achievements, PBs, pets and clues when observable", position=3, section=journal) default boolean logGameMilestones(){return true;}
 @ConfigItem(keyName="logDeaths", name="Deaths", description="Record player deaths in the Chronicle", position=4, section=journal) default boolean logDeaths(){return true;}
 @ConfigItem(keyName="logLoot", name="Claimed loot", description="Record loot after RuneChronicle observes it enter your inventory or a supported reward interface awards it", position=0, section=loot) default boolean logLoot(){return true;}
 @Range(min=0,max=2000000000) @ConfigItem(keyName="minimumGeValue", name="Minimum GE value", description="Loot below this GE value can be excluded from the normal Chronicle. Default: 1 gp. Set 0 to disable the GE threshold.", position=1, section=loot) default int minimumGeValue(){return 1;}
 @Range(min=0,max=2000000000) @ConfigItem(keyName="minimumHaValue", name="Minimum high alch value", description="0 disables the high-alch threshold", position=2, section=loot) default int minimumHaValue(){return 0;}
 @ConfigItem(keyName="lootValueRule", name="Value rule", description="How GE and high-alch thresholds qualify loot", position=3, section=loot) default LootValueRule lootValueRule(){return LootValueRule.GE_OR_HA;}
 @ConfigItem(keyName="useStackValue", name="Use whole stack value", description="Apply thresholds to unit value × quantity", position=4, section=loot) default boolean useStackValue(){return true;}
 @ConfigItem(keyName="alwaysLogSpecial", name="Always log special rewards", description="Always retain pets, collection log unlocks, uniques and special reward events", position=5, section=loot) default boolean alwaysLogSpecial(){return true;}
 @ConfigItem(keyName="showGameRecapButton", name="In-game recap button", description="Show a small movable RECAP button in the game view for quick access.", position=0, section=recap) default boolean showGameRecapButton(){return true;}
 @ConfigItem(keyName="showRecapEntry", name="Sidebar recap button", description="Show View Session Recap in the RuneChronicle sidebar.", position=1, section=recap) default boolean showRecapEntry(){return true;}
 enum LootValueRule { GE_OR_HA("GE or High Alch"), GE_AND_HA("GE and High Alch"), GE_ONLY("GE only"), HA_ONLY("High Alch only"); private final String label; LootValueRule(String s){label=s;} public String toString(){return label;} }
}
