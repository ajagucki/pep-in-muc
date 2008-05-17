package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.File;

/**
 * PEPinMUC Plugin
 *
 * @author Armando Jagucki
 */
public class PEPInMUCPlugin implements Plugin, MUCEventListener {

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        MUCEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        MUCEventDispatcher.removeListener(this);
    }

    public void roomCreated(JID roomJID) {
        // Do nothing
    }

    public void roomDestroyed(JID roomJID) {
        // Do nothing
    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {
        // Do nothing
    }

    public void occupantLeft(JID roomJID, JID user) {
        // Do nothing
    }

    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // Do nothing
    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        // TODO: implement
    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        // Do nothing
    }
}
