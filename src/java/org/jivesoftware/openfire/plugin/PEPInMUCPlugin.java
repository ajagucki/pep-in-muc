package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.io.File;

/**
 * PEPinMUC Plugin
 *
 * @author Armando Jagucki
 */
public class PEPInMUCPlugin implements Plugin, PacketInterceptor {

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        InterceptorManager.getInstance().addInterceptor(this);
    }

    public void destroyPlugin() {
        InterceptorManager.getInstance().removeInterceptor(this);
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException {

        if (!isValidTargetPacket(packet, incoming, processed)) {
            return;
        }

        // TODO: implement command parsing/logic

        // Do not send the command to the MUC occupants
        throw new PacketRejectedException();
    }

    private boolean isValidTargetPacket(Packet packet, boolean incoming, boolean processed) {
        if (!(packet instanceof Message) || !incoming || processed) {
            return false;
        }

        Message messagePacket = (Message) packet;
        return messagePacket.getType() == Message.Type.groupchat;
    }
}
