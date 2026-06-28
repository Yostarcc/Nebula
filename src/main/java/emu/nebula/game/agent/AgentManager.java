package emu.nebula.game.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.nebula.data.GameData;
import emu.nebula.database.GameDatabaseObject;
import emu.nebula.game.achievement.AchievementCondition;
import emu.nebula.game.character.GameCharacter;
import emu.nebula.game.player.Player;
import emu.nebula.game.player.PlayerChangeInfo;
import emu.nebula.game.player.PlayerManager;
import emu.nebula.game.quest.QuestCondition;
import lombok.Getter;
import us.hebi.quickbuf.RepeatedInt;

@Getter
@Entity(value = "agents", useDiscriminator = false)
public class AgentManager extends PlayerManager implements GameDatabaseObject {
    @Id
    private int uid;
    
    private Map<Integer, Agent> agents;
    
    @Deprecated // Morphia only
    public AgentManager() {
        
    }
    
    public AgentManager(Player player) {
        super(player);
        this.uid = player.getUid();
        this.agents = new HashMap<>();
        
        this.save();
    }
    
    public int getMax() {
        return 4;
    }

    public Agent apply(int id, int processTime, RepeatedInt charIds) {
        // Check if we already have the maximum amount of commissions
        if (this.getAgents().size() >= this.getMax()) {
            return null;
        }
        
        // Get agent data
        var data = GameData.getAgentDataTable().get(id);
        if (data == null) {
            return null;
        }
        
        // Sanity check char ids
        if (charIds.length() <= 0 || charIds.length() > data.getMemberLimit()) {
            return null;
        }
        
        // Make sure we own the characters
        var characters = new ArrayList<GameCharacter>();
        
        for (int charId : charIds) {
            var character = getPlayer().getCharacters().getCharacterById(charId);
            
            // Also check if character fits the commission level requirement
            if (character == null || character.getLevel() < data.getLevel()) {
                return null;
            }
            
            characters.add(character);
        }
        
        // Check char tags
        if (!data.hasTags(characters)) {
            return null;
        }
        
        // Create agent
        var agent = new Agent(data, processTime, charIds.toArray());
        
        // Add
        this.getAgents().put(agent.getId(), agent);
        
        // Quest
        this.getPlayer().trigger(QuestCondition.AgentApplyTotal, 1);
        
        // Success
        return agent;
    }

    public Agent giveUp(int id) {
        var agent = this.getAgents().remove(id);
        
        if (agent != null) {
            this.save();
        }
        
        return agent;
    }

    public PlayerChangeInfo claim(int id) {
        // Create list of claimed agents
        var list = new ArrayList<Agent>();
        
        if (id > 0) {
            var agent = this.getAgents().get(id);
            
            if (agent != null && agent.isCompleted()) {
                list.add(agent);
            }
        } else {
            for (var agent : this.getAgents().values()) {
                if (agent != null && agent.isCompleted()) {
                    list.add(agent);
                }
            }
        }
        
        // Sanity check
        if (list.isEmpty()) {
            return null;
        }
        
        // Create results
        var change = new PlayerChangeInfo();
        var results = new ArrayList<AgentResult>();
        
        // Claim
        for (var agent : list) {
            // Remove agent
            this.getAgents().remove(agent.getId());
            
            // Get result
            var result = new AgentResult(agent);
            
            // Add to result list
            results.add(result);
            
            // Get agent data
            var data = GameData.getAgentDataTable().get(agent.getId());
            if (data == null) {
                continue;
            }
            
            // Calculate rewards
            var duration = data.getDurations().get(agent.getDuration());
            if (duration == null) {
                continue;
            }
            
            // Check if we had extra tags
            var characters = new ArrayList<GameCharacter>();
            
            for (int charId : agent.getCharIds()) {
                var character = getPlayer().getCharacters().getCharacterById(charId);
                if (character == null) continue;
                
                characters.add(character);
            }
            
            // Create rewards
            result.setRewards(duration.getRewards().generate());
            
            // Add rewards to inventory
            this.getPlayer().getInventory().addItems(result.getRewards(), change);
            
            // Add bonus rewards if we meet the requirements
            if (data.hasExtraTags(characters)) {
                // Get bonus rewards
                result.setBonus(duration.getBonus().generate());
                
                // Add rewards to inventory
                this.getPlayer().getInventory().addItems(result.getBonus(), change);
            }
        }
        
        // Set results in change info
        change.setExtraData(results);
        
        // Save to database
        this.save();
        
        // Quest + Achievements
        getPlayer().trigger(QuestCondition.AgentFinishTotal, list.size());
        getPlayer().trigger(AchievementCondition.AgentWithSpecificFinishTotal, list.size());
        
        // Success
        return change.setSuccess(true);
    }
}
