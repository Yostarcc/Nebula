package emu.nebula.server.handlers;

import emu.nebula.Nebula;
import emu.nebula.game.player.PlayerErrorCode;
import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.net.PacketHelper;
import emu.nebula.proto.PlayerLogin.LoginReq;
import emu.nebula.proto.PlayerLogin.LoginResp;
import emu.nebula.proto.Public.Error;

@HandlerId(NetMsgId.player_login_req)
public class HandlerPlayerLoginReq extends NetHandler {

    public boolean requirePlayer() {
        return false;
    }

    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        // Parse request
        var req = LoginReq.parseFrom(message);

        // OS
        String loginToken = req.getOfficialOverseas().getToken();

        if (loginToken.isEmpty()) {
            // CN
            loginToken = req.getOfficial().getToken();
        }

        var banModule = Nebula.getGameContext().getBanModule();

        // Check IP ban
        if (banModule.isIpBanned(session.getIpAddress())) {
            var banInfo = banModule.getIpBanInfo(session.getIpAddress());
            return session.encodeMsg(NetMsgId.player_login_failed_ack, banInfo.toProto());
        }

        // Login
        boolean result = session.login(loginToken);
        if (!result) {
            Error errorCause = Error.newInstance().setCode(PlayerErrorCode.ErrLogin.getValue());
            return session.encodeMsg(NetMsgId.player_login_failed_ack, errorCause);
        }

        // Check player ban
        if (session.getPlayer() != null && banModule.isPlayerBanned(session.getPlayer().getUid())) {
            var banInfo = banModule.getPlayerBanInfo(session.getPlayer().getUid());
            return session.encodeMsg(NetMsgId.player_login_failed_ack, banInfo.toProto());
        }
        
        // Set platform
        session.setPlatform(req.getPlatformValue());

        // Regenerate session token because we are switching encryption method
        Nebula.getGameContext().generateSessionToken(session);

        // Create rsp
        var rsp = LoginResp.newInstance()
                .setToken(session.getToken());

        // Do not use GameSession.encodeMsg here: it would attach NextPackage to login ack.
        // The client handles activity/quest notifications before requesting player_data_req.
        // And because playerData caches are still missing and notification refresh code will hit nil data.
        return PacketHelper.encodeMsg(NetMsgId.player_login_succeed_ack, rsp);
    }

}
