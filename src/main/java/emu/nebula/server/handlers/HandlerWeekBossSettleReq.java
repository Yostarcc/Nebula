package emu.nebula.server.handlers;

import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.WeekBossSettle.WeekBossLevelSettleResp;
import emu.nebula.proto.WeekBossSettle.WeekBossSettleReq;
import emu.nebula.net.HandlerId;
import emu.nebula.data.GameData;
import emu.nebula.game.instance.InstanceSettleData;
import emu.nebula.net.GameSession;
import emu.nebula.proto.Public.WeekBossLevel;

@HandlerId(NetMsgId.week_boss_settle_req)
public class HandlerWeekBossSettleReq extends NetHandler {

    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        // Cache player
        var player = session.getPlayer();
        
        // Get boss level data
        var data = GameData.getWeekBossLevelDataTable().get(player.getInstanceManager().getCurInstanceId());
        if (data == null || !data.hasEnergy(player)) {
            return session.encodeMsg(NetMsgId.week_boss_settle_failed_ack);
        }
        
        // Parse request
        var req = WeekBossSettleReq.parseFrom(message);
        if (req.getResult() && req.getTime() <= 0) {
            return session.encodeMsg(NetMsgId.week_boss_settle_failed_ack);
        }
        
        // Settle instance
        var changes = player.getInstanceManager().settleWeekly(
                data,
                req.getResult()
        );
        
        var settleData = (InstanceSettleData) changes.getExtraData();

        if (req.getResult()) {
            int bestTime = player.getProgress().saveWeekBossBestTime(data.getId(), req.getTime());

            var weekBossLevel = WeekBossLevel.newInstance()
                    .setId(data.getId())
                    .setTime(bestTime)
                    .setFirst(player.getProgress().getWeekBossLog().containsKey(data.getId()));

            changes.add(weekBossLevel);
        }

        // Handle client events for achievements
        session.getPlayer().getAchievementManager().handleClientEvents(req.getEvents());
        
        // Create response
        var rsp = WeekBossLevelSettleResp.newInstance()
                .setFirst(settleData.isFirst())
                .setChange(changes.toProto());
        
        // Add reward items
        if (settleData.getRewards() != null) {
            settleData.getRewards().toItemTemplateStream().forEach(rsp::addAwardItems);
        }
        if (settleData.getFirstRewards() != null) {
            settleData.getFirstRewards().toItemTemplateStream().forEach(rsp::addFirstItems);
        }
        
        // Send response
        return session.encodeMsg(NetMsgId.week_boss_settle_succeed_ack, rsp);
    }

}
