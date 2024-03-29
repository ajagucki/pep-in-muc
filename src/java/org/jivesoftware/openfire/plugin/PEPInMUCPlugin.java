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
 * This plugin allows users local to the server to explicitly broadcast their Personal Eventing information
 * to a Multi-User Chat room via a command sent to the MUC room.
 *
 * For example, if local user VinniePaz is in a MUC room and wishes to share what he is listening to with
 * the other occupants in the room, he can send a message to the room with body "/tune" which will cause
 * the server to lookup his User Tune PEP node and share its payload with the other room occupants in
 * an action format.
 * (eg. *VinniePaz is listening to Jedi Mind Tricks - Blood Runs Cold - Violent By Design)
 *
 * The plugin aims to support all known PEP data formats, triggered by their node names in the form of a command.
 * So if the user wishes to broadcast his 'mood' PEP node, he could send a message to the room with body "/mood".
 *
 * @author Armando Jagucki
 * @author Ben Slote
 * pnhfgvd, vfjz, zovfubc; Gur CRCoblf
 */
public class PEPInMUCPlugin implements Plugin, PacketInterceptor {

    private IQPEPHandler iqPEPHandler;
    private PacketRouter packetRouter;

    private final String MOOD_NAMESPACE = "http://jabber.org/protocol/mood";
    private final String TUNE_NAMESPACE = "http://jabber.org/protocol/tune";
    private final String MOOD_COMMAND = "/mood";
    private final String TUNE_COMMAND = "/tune";

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
        boolean processedSuccessfully;
        try {
            processedSuccessfully = processCommand(packet);
        } catch (PEPNodeNotFoundException e) {
            Message noobMessage = createNoobMessage(e.getOffendingPacket());
            packetRouter.route(noobMessage);
            processedSuccessfully = true;
        }

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

    private PEPNodeType getNodeType(Message messagePacket) throws PEPNodeTypeNotFoundException {
        Element body = messagePacket.getElement().element("body");
        if (body == null) {
            throw new PEPNodeTypeNotFoundException();
        }

        String bodyText = body.getTextTrim();

        if (bodyText.equals(TUNE_COMMAND)) {
            return PEPNodeType.tune;
        }
        else if (bodyText.equals(MOOD_COMMAND)) {
            return PEPNodeType.mood;
        }
        else {
            throw new PEPNodeTypeNotFoundException();
        }
    }

    private boolean processCommand(Packet packet) throws PEPNodeNotFoundException {
        Message messagePacket = (Message) packet;
        PEPNodeType nodeType;
        try {
            nodeType = getNodeType(messagePacket);
        } catch (PEPNodeTypeNotFoundException e) {
            return false;
        }

        switch (nodeType) {
            case tune:
                packetRouter.route(createTuneMessage(messagePacket));
                return true;
            case mood:
                packetRouter.route(createMoodMessage(messagePacket));
                return true;
            default:
                return false;
        }
    }

    private Message createTuneMessage(Message messagePacket)  throws PEPNodeNotFoundException {
    	Message message = new Message(messagePacket.getElement());

        Element payload = getPEPPayload(messagePacket, TUNE_NAMESPACE);

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
            throw new PEPNodeNotFoundException(messagePacket);
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

    private Message createMoodMessage(Message messagePacket) throws PEPNodeNotFoundException {
        Message moodMessage = new Message(messagePacket.getElement());

        Element payload = getPEPPayload(messagePacket, MOOD_NAMESPACE);

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

        if (moodValue == null) {
            throw new PEPNodeNotFoundException(messagePacket);
        }

        String moodMessageBodyPrefix = "/me is in ";
        if (startsWithVowel(moodValue)) {
            moodMessageBodyPrefix += "an ";
        }
        else {
            moodMessageBodyPrefix += "a ";
        }

        String moodMessageBodySuffix = "";
        if (moodDescription != null) {
            moodMessageBodySuffix = " (" + moodDescription + ")";
        }

        moodMessage.setBody(moodMessageBodyPrefix + moodValue + " mood."
            + moodMessageBodySuffix);

        return moodMessage;
    }

    private Message createNoobMessage(Message messagePacket) {
    	Message noobMessage = new Message();
    	noobMessage.setTo(messagePacket.getFrom());
    	noobMessage.setFrom(messagePacket.getTo());
    	noobMessage.setType(Message.Type.groupchat);

    	String bodyPrefix = "You didn't publish your ";
    	String bodySuffix = " yet, noob.";
    	String fuckUp = null;

    	String body = messagePacket.getBody().trim();
    	if (body.equals(TUNE_COMMAND)) {
    	    fuckUp = PEPNodeType.tune.toString();
    	}
    	else if (body.equals(MOOD_COMMAND)) {
    	    fuckUp = PEPNodeType.mood.toString();
	}

    	noobMessage.setBody(bodyPrefix + fuckUp + bodySuffix);

	return noobMessage;
    }

    private Element getPEPPayload(Message messagePacket, String pepNamespace) throws PEPNodeNotFoundException {
        PEPService pepService = iqPEPHandler.getPEPService(messagePacket.getFrom().toBareJID());
        if (pepService == null) {
            throw new PEPNodeNotFoundException(messagePacket);
        }

        Element payload = null;
        for (Node node : pepService.getNodes()) {
            if (node.getNodeID().equals(pepNamespace)) {
                for (PublishedItem item : node.getPublishedItems()) {
                    LeafNode leafNode = item.getNode();
                    if (leafNode.getNodeID().equals(pepNamespace)) {
                        payload = item.getPayload();
                        break;
                    }
                }
                break;
            }
        }

        if (payload == null) {
            throw new PEPNodeNotFoundException(messagePacket);
        }

        return payload;
    }

    private boolean startsWithVowel(String string) {
        return "aeiou".indexOf(string.charAt(0)) >= 0;
    }

    private static enum PEPNodeType {
        /**
         * XEP-0118: User Tune
         */
        tune,
        /**
         * XEP-0107: User Mood
         */
        mood
    }

    private class PEPNodeTypeNotFoundException extends Exception {
    }

    private class PEPNodeNotFoundException extends Exception {

        Message offendingPacket;

        public PEPNodeNotFoundException() {
            super();
        }

        public PEPNodeNotFoundException(Message messagePacket) {
            offendingPacket = messagePacket;
        }

        public Message getOffendingPacket() {
            return offendingPacket;
        }
    }
}
