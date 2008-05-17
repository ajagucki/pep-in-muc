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
        boolean processedSuccessfully = false;
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
        char firstLetter = moodValue.charAt(0);
        if (VOWELS.indexOf(firstLetter) < 0) {
            moodMessageBodyPrefix += "a ";
        }
        else {
            moodMessageBodyPrefix += "an ";
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

    	String body = messagePacket.getBody();
    	if (body.equals("/tune")) {
    	    fuckUp = PEPNodeType.tune.toString();
    	}
    	else if (body.equals("/mood")) {
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
