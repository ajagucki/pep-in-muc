package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.openfire.pubsub.LeafNode;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.IQPEPHandler;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.dom4j.Element;

import java.io.File;
import java.util.Iterator;

/**
 * PEPinMUC Plugin
 *
 * @author Armando Jagucki
 * @author Ben Slote
 * pnhfgvd, vfjz, zovfubc; Gur CRCoblf
 */
public class PEPInMUCPlugin implements Plugin, PacketInterceptor {

    private IQPEPHandler iqPEPHandler;
    private PacketRouter packetRouter;

    // Constants
    private static final String MOOD_NAMESPACE = "http://jabber.org/protocol/mood";
    private static final String TUNE_NAMESPACE = "http://jabber.org/protocol/tune";

    // TODO: fuck this nastiness, fix it
    private static final String VOWELS = "aeiou";

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        InterceptorManager.getInstance().addInterceptor(this);
        iqPEPHandler = XMPPServer.getInstance().getIQPEPHandler();
        packetRouter = XMPPServer.getInstance().getPacketRouter();
    }

    public void destroyPlugin() {
        InterceptorManager.getInstance().removeInterceptor(this);
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException {

        if (!isValidTargetPacket(packet, incoming, processed)) {
            return;
        }

        // Process a command from the group chat message.
        boolean processedSuccessfully = processCommand(packet);

        // Do not send the command text to the MUC occupants
        if (processedSuccessfully) {
            throw new PacketRejectedException();
        }
    }

    private boolean isValidTargetPacket(Packet packet, boolean incoming, boolean processed) {
        if (!(packet instanceof Message) || !incoming || processed) {
            return false;
        }

        Message messagePacket = (Message) packet;
        return messagePacket.getType() == Message.Type.groupchat;
    }

    private boolean processCommand(Packet packet) {
        Message messagePacket = (Message) packet;
        PEPNodeType nodeType;
        try {
            nodeType = getNodeType(messagePacket);
        } catch (PEPNodeTypeNotFoundException e) {
            return false;
        }

        switch (nodeType) {
            case tune:
                Message tuneMessage = createTuneMessage(packet);
                if (tuneMessage == null) {
                    return false;
                }

                packetRouter.route(tuneMessage);
                return true;
            case mood:
                Message moodMessage = createMoodMessage(packet);
                if (moodMessage == null) {
                    return false;
                }

                packetRouter.route(moodMessage);
                return true;
            default:
                return false;
        }
    }

    private PEPNodeType getNodeType(Packet packet) throws PEPNodeTypeNotFoundException {
        Message messagePacket = (Message) packet;

        Element body = messagePacket.getElement().element("body");
        if (body == null) {
            throw new PEPNodeTypeNotFoundException();
        }

        String bodyText = body.getTextTrim();

        if (bodyText.equals("/tune")) {
            return PEPNodeType.tune;
        }
        else if (bodyText.equals("/mood")) {
            return PEPNodeType.mood;
        }
        else {
            throw new PEPNodeTypeNotFoundException();
        }
    }

    private Message createTuneMessage(Packet packet) {
    	Message message = new Message(packet.getElement());

    	PEPService pepService = iqPEPHandler.getPEPService(packet.getFrom().toBareJID());
        if (pepService == null) {
            return null;
        }

     	Element payload = null;
     	for (Node node : pepService.getNodes()) {
     	    if (node.getNodeID().equals(TUNE_NAMESPACE)) {
                for (PublishedItem item : node.getPublishedItems()) {
                    LeafNode leafNode = item.getNode();
                    if (leafNode.getNodeID().equals(TUNE_NAMESPACE)) {
                        payload = item.getPayload();
                        break;
                    }
                }
                break; // dance
            }
        }

        if (payload == null) {
                return null;
        }

        String artist = null;
        String title  = null;
        String source = null;
        Iterator payloadIter = payload.elementIterator();
        while (payloadIter.hasNext()) {
            Element element = (Element) payloadIter.next();
            String elementName = element.getName();
            if (elementName.equals("artist")) {
                artist = element.getText();
            }
            else if (elementName.equals("title")) {
                title = element.getText();
            }
            else if (elementName.equals("source")) {
                source = element.getText();
            }
        }

        // At the minimum we are going to require a title
        if (title == null) {
            return null;
        }

        String tuneMessageBodyPrefix = "/me is listening to ";
        if (artist != null) {
            tuneMessageBodyPrefix += (artist + " - ");
        }
        String tuneMessageBodySuffix = "";
        if (source != null) {
            tuneMessageBodySuffix = " - " + source;
        }

        message.setBody(tuneMessageBodyPrefix + title + tuneMessageBodySuffix);

        return message;
    }

    private Message createMoodMessage(Packet packet) {
        Message moodMessage = new Message(packet.getElement());

        PEPService pepService = iqPEPHandler.getPEPService(packet.getFrom().toBareJID());
        if (pepService == null) {
            return null;
        }

        Element payload = null;
        for (Node node : pepService.getNodes()) {
            if (node.getNodeID().equals(MOOD_NAMESPACE)) {
                for (PublishedItem item : node.getPublishedItems()) {
                    LeafNode leafNode = item.getNode();
                    if (leafNode.getNodeID().equals(MOOD_NAMESPACE)) {
                        payload = item.getPayload();
                        break;
                    }
                }
                break;
            }
        }

        if (payload == null) {
            return null;
        }

        String moodValue = null;
        String moodDescription = null;
        Iterator payloadIter = payload.elementIterator();
        while (payloadIter.hasNext()) {
            Element element = (Element) payloadIter.next();
            if (element.getName().equals("text")) {
                moodDescription = element.getText();
            }
            else {
                moodValue = element.getName();
            }
        }

        String moodMessageBodyPrefix = "/me is in ";
        if (moodValue != null) {
            char firstLetter = moodValue.charAt(0);
            if (VOWELS.indexOf(firstLetter) < 0) {
                moodMessageBodyPrefix += "a ";
            }
            else {
                moodMessageBodyPrefix += "an ";
            }
        }
        else {
            return null;
        }

        String moodMessageBodySuffix = "";
        if (moodDescription != null) {
            moodMessageBodySuffix = " (" + moodDescription + ")";
        }

        moodMessage.setBody(moodMessageBodyPrefix + moodValue + " mood."
            + moodMessageBodySuffix);

        return moodMessage;
    }

    public enum PEPNodeType {
        /**
         * XEP-0118: User Tune
         */
        tune,
        /**
         * XEP-0107: User Mood
         */
        mood
    }

    private class PEPNodeTypeNotFoundException extends Throwable {
    }
}
