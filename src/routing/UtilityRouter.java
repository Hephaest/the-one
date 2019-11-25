/**
 * This class inherits from the ActiveRouter class.
 * This class uses a hybrid routing protocol to define the utility of mobile node as a metric for next-hop selection.
 * This protocol improves performance by considering the following factors:<BR>
 * <UL>
 * <LI/> Energy.
 * <LI/> Mobility.
 * <LI/> Subnet (Location).
 * <LI/> Queue.
 * </UL>
 * This protocol combines both Probabilistic routing and Spray and Wait routing protocol. Given to simulation results,
 * it shows a higher delivery rate with lower overhead compared with the above protocols.
 * In the perspective of queueing management, the message with the lowest possibility for delivery will be discarded. You can easily
 * find the corresponding algorithm to achieve intelligent drop. The core of this algorithm is to check the following factors:
 * <UL>
 * <LI/> The relationship between TTL and the destination of the message. E.g. if the message leaves with a small TTL
 * but it still has a long way to transfer, then it should be discarded.
 * <LI/> The message size. E.g. if the message is too large then it has a lower possibility to be transferred successfully due to DTN.
 * </UL>
 * Given to simulation results, with decreasing buffer size, it shows higher robustness in delivery possibility and
 * overhead compared with the above protocols.
 * @author Miao Cai
 * @since 11/18/2019 10:08 PM
 */
package routing;

import core.*;
import util.Tuple;
import java.util.*;

/**
 * Implementation of Utility router by extending the ActiveRouter class.
 *
 */
public class UtilityRouter extends ActiveRouter {
    /** Optimal router's setting namespace ({@value}). */
    private static final String OPTIMAL_NS = "UtilityRouter";

    /** Identifier for the initial number of copies setting ({@value}). */
    private static final String NROF_COPIES = "nrofCopies";

    /** Message property key. */
    private static final String MSG_COUNT_PROPERTY = OPTIMAL_NS + "." + "copies";
    /** Max delivery predictability initialization constant. */
    private static final double PEncMax = 0.75;
    /** Delivery predictability transitivity scaling constant default value. */
    private static final double DEFAULT_BETA = 0.25;
    /** Delivery predictability aging constant. */
    private static final double DEFAULT_GAMMA = 0.98;
    /** Typical interconnection time in seconds. */
    private static final double I_TYP = 1800;
    /** Threshold for next hop preliminary selection. */
    private static final double FILITER_THRESHOLD = 0.67;
    private static final double MAX_DROP = 0.7;

    /**
     * Number of seconds in time unit -setting id ({@value}).
     * How many seconds one time unit is when calculating aging of
     * delivery predictions. Should be tweaked for the scenario.
     */
    private static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";

    /**
     * Transitivity scaling constant (beta) -setting id ({@value}).
     * Default value for setting is {@link #DEFAULT_BETA}.
     */
    private static final String BETA_S = "beta";

    /**
     * Predictability aging constant (gamma) -setting id ({@value}).
     * Default value for setting is {@link #DEFAULT_GAMMA}.
     */
    private static final String GAMMA_S = "gamma";

    /** The value of nrof seconds in time unit -setting. */
    private int secondsInTimeUnit;
    /** Value of beta setting. */
    private double beta;
    /** Value of gamma setting. */
    private double gamma;
    /** Delivery predictability initialization constant. */
    private double pinit;
    /** Initial number of copies. */
    private int initialNrofCopies;
    /** Delivery predictabilities. */
    private Map<DTNHost, Double> preds;
    /** Last encouter timestamp (sim)time. */
    private Map<DTNHost, Double> lastEncouterTime;
    /** Overall delivery predictabilities. */
    private Map<DTNHost, Float> predsForAllConditions;
    /** Last delivery predictability update (sim)time. */
    private double lastAgeUpdate;

    /**
     * Constructor. Creates a new message router based on the settings in
     * the given Settings object.
     * @param s The settings object.
     */
    public UtilityRouter(Settings s) {
        super(s);
        Settings optimalSettings = new Settings(OPTIMAL_NS);
        secondsInTimeUnit = optimalSettings.getInt(SECONDS_IN_UNIT_S);
        initialNrofCopies = optimalSettings.getInt(NROF_COPIES);
        if (optimalSettings.contains(BETA_S)) beta = optimalSettings.getDouble(BETA_S);
        else beta = DEFAULT_BETA;
        if (optimalSettings.contains(GAMMA_S)) gamma = optimalSettings.getDouble(GAMMA_S);
        else gamma = DEFAULT_GAMMA;
        pinit = PEncMax;

        initPreds();
        initExtraPreds();
        initEncTimes();
    }

    /**
     * Copy constructor.
     * @param r The router prototype where setting values are copied from.
     */
    protected UtilityRouter(UtilityRouter r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        this.gamma = r.gamma;
        this.initialNrofCopies = r.initialNrofCopies;
        initPreds();
        initExtraPreds();
        initEncTimes();
    }

    /**
     * Initializes delivery predictability hash.
     */
    private void initPreds() {
        this.preds = new HashMap<DTNHost, Double>();
    }

    /**
     * Initializes overall delivery predictability hash.
     */
    private void initExtraPreds() {
        this.predsForAllConditions = new HashMap<DTNHost, Float>();
    }
    /**
     * Initializes last encounter Time hash.
     */
    private void initEncTimes() {
        this.lastEncouterTime = new HashMap<DTNHost, Double>();
    }

    /**
     * This method is used to inform the router about change in connections state.
     * @param con The connection that changed.
     */
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            // Make sure update following predictabilities.
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);
        }
    }

    /**
     * This method is used to calculate the overall delivery predictability of the given router.
     * In utility routing, the overlap is the worst-case that we need to avoid it. Besides, the mobility is used to
     * predict the degree of delivery possibility while energy indicates how much we can trust this router.
     * @param otherRouter The given router.
     * @param m The message which is ready for transfer.
     * @return The overall delivery predictability.
     */
    private float predForDeliverHost(UtilityRouter otherRouter, Message m) {
        double creditForMobility = otherRouter.getPredFor(m.getTo());
        double creditForEnergy;
        double creditForLocation;
        /*
         * If another router has more energy than the current router which means it has a higher possibility of delivery.
         * However, this requirement is not compulsory so if another router has less energy than the current router, it
         * will still get half of the extra points.
         */
        creditForEnergy = otherRouter.getEnergy() >= getEnergy()? 1.0 : 0.5;
        /*
         * Reduce overlap between 2 routers so that we can avoid the worst case of Spray and Wait.
         */
        creditForLocation = 1 - checkOverlap(otherRouter);
        predsForAllConditions.put(otherRouter.getHost(), (float)(0.2 * creditForEnergy + 0.55 * creditForLocation + 0.25 * creditForMobility));
        return predsForAllConditions.get(otherRouter.getHost());
    }

    /**
     * This method is used to check the degree of connection overlaps between 2 routers.
     * @param otherRouter The given router.
     * @return The overlap degree.
     */
    private double checkOverlap(UtilityRouter otherRouter){
        int count = 0;
        int overlap = 0;
        for (Connection con : this.getConnections()){
            for(Connection othConn : otherRouter.getConnections())
                overlap += isEqualHost(con.getOtherNode(getHost()).toString(), othConn.getOtherNode(otherRouter.getHost()).toString()) ? 1 : 0;
            count ++;
        }
        return (double)overlap/count;
    }

    /**
     * This method is used to check whether the given hosts are the same.
     * @param host1 The first host.
     * @param host2 The another host.
     * @return The result of the comparison.
     */
    private boolean isEqualHost(String host1, String host2){
        return host1.equals(host2);
    }

    /**
     * This method is used to update delivery predictions for a host.
     * <CODE>
     *     P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT;
     *     P_INIT =
     *          P_max * (intvl / I_typ) for 0<= intvl <= I_typ;
     *          P_max for intvl > I_typ.
     * </CODE>
     * @param host The host we just met.
     */
    private void updateDeliveryPredFor(DTNHost host) {
        double simTime = SimClock.getTime();
        double lastEncTime = getEncTimeFor(host);
        if(lastEncTime == 0) pinit = PEncMax;
        else if((simTime - lastEncTime) < I_TYP) pinit = PEncMax * ((simTime - lastEncTime) / I_TYP);
        else pinit = PEncMax;
        double oldValue = getPredFor(host);
        double newValue = oldValue + (1 - oldValue) * pinit;
        preds.put(host, newValue);
        lastEncouterTime.put(host, simTime);
    }

    /**
     * This method is used to return the timestamp of the last encounter of with the host or -1 if
     * entry for the host doesn't exist.
     * @param host The host to look the timestamp for.
     * @return The last timestamp of encounter with the host.
     */
    private double getEncTimeFor(DTNHost host) {
        if (lastEncouterTime.containsKey(host)) return lastEncouterTime.get(host);
        else return 0;
    }

    /**
     * This method is used to return the current prediction (P) value for a host or 0 if entry for
     * the host doesn't exist.
     * @param host The host to look the P for.
     * @return The current P value.
     */
    private double getPredFor(DTNHost host) {
        // make sure predictability is updated before getting.
        ageDeliveryPreds();
        if (preds.containsKey(host)) return preds.get(host);
        else return 0;
    }

    /**
     * This method is used to return the overall delivery prediction value for a host that has already calculated.
     * @param host The host to look the P for.
     * @return The current P value.
     */
    private double getExtraPredFor(DTNHost host) {
        return predsForAllConditions.get(host);
    }

    /**
     * This method is used to update transitive (A->B->C) delivery predictions.
     * <CODE>
     *     P(a,c) = max(P(a,c)_old, P(a,b)_old * P(b,c) * BETA)
     * </CODE>
     * @param host The B host who we just met.
     */
    private void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof UtilityRouter : "UtilityRouter only works " +
                " with other routers of same type";

        double pForHost = getPredFor(host); // P(a,b).
        Map<DTNHost, Double> othersPreds = ((UtilityRouter)otherRouter).getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == getHost()) {
                continue; // don't add yourself.
            }

            double pOld = getPredFor(e.getKey()); // P(a,c)_old.
            double pNew = pForHost * e.getValue() * beta;
            if(pNew>pOld)
                preds.put(e.getKey(), pNew);
        }
    }

    /**
     * This method is used to age all entries in the delivery predictions.
     * <CODE>
     *     P(a,b) = P(a,b)_old * (GAMMA ^ k)
     * </CODE>
     * where k is number of time units that have elapsed since the last time the metric was aged.
     * @see #SECONDS_IN_UNIT_S
     */
    private void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / secondsInTimeUnit;
        if (timeDiff == 0) return;
        double mult = Math.pow(gamma, timeDiff);
        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) e.setValue(e.getValue()*mult);
        this.lastAgeUpdate = SimClock.getTime();
    }

    /**
     * This method is used to get the energy value of the current router.
     * @return The energy value of the current router.
     */
    public double getEnergy(){
        return super.getEnergy();
    }

    /**
     * This method is used to return a map of this router's delivery predictions.
     * @return a map of this router's delivery predictions.
     */
    private Map<DTNHost, Double> getDeliveryPreds() {
        ageDeliveryPreds(); // make sure the aging is done.
        return this.preds;
    }

    /**
     * This method should be called (on the receiving host) after a message
     * was successfully transferred. The transferred message is put to the
     * message buffer unless this host is the final recipient of the message.
     * @param id Id of the transferred message.
     * @param from Host the message was from (previous hop).
     * @return The message that this host received.
     */
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);

        assert nrofCopies != null : "Not a required message: " + msg;

        /* in binary mode the receiving node gets floor(n/2) copies. */
        nrofCopies = (int)Math.floor(nrofCopies/2.0);

        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        return msg;
    }

    /**
     * This method is used to  create a new message to the router.
     * @param msg The message to create.
     * @return True if the creation succeeded, false if not (e.g. the message was too big for the buffer).
     */
    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());
        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
        addToMessages(msg, true);
        return true;
    }

    /**
     * Updates router.
     * This method should be called (at least once) on every simulation interval to update the status of transfer(s).
     */
    @Override
    public void update() {
        super.update();
        this.predsForAllConditions = new HashMap<DTNHost, Float>();

        // nothing to transfer or is currently transferring.
        if (!canStartTransfer() ||isTransferring()) return;

        // Try messages that could be delivered to final recipient.
        if (exchangeDeliverableMessages() != null) return;

        /* Create a list of SAWMessages that have copies left to distribute. */
        @SuppressWarnings(value = "unchecked")
        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

        /* Try to send those messages. */
        if (copiesLeft.size() > 0) tryMessagesToConnections(copiesLeft, getConnections());
    }

    /**
     * This method is used to create and return a list of messages this router is currently
     * carrying and still has copies left to distribute (nrof copies > 1).
     * @return A list of messages that have copies left.
     */
    private List<Message> getMessagesWithCopiesLeft() {
        List<Message> list = new ArrayList<Message>();

        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "Message " + m + " didn't have " + "nrof copies property!";
            if (nrofCopies > 1) list.add(m);
        }

        return list;
    }

    /**
     * This method is used to send all given messages to all given connections. Connections
     * are first iterated in the order they are in the list and for every
     * connection, the messages are tried in the order they are in the list.
     * Once an accepting connection is found, no other connections or messages
     * are tried.
     * @param messages The list of Messages to try
     * @param connections The list of Connections to try
     * @return The connections that started a transfer or null if no connection
     * accepted a message.
     */
    @Override
    protected Connection tryMessagesToConnections(List<Message> messages, List<Connection> connections) {
        List<Tuple<Message, Connection>> messagesList = new ArrayList<Tuple<Message, Connection>>();
        /* For all connected hosts collect all messages that have a higher
		   probability of delivery by the other host. */
        for (Connection con : getConnections()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            UtilityRouter othRouter = (UtilityRouter) otherHost.getRouter();
            // Skip hosts that are transferring.
            if (othRouter.isTransferring()) continue;
            // Skip hosts that are power down.
            if (!othRouter.hasEnergy()) continue;
            // Skip hosts that has too much overlaps.
            if (checkOverlap(othRouter) > 0.7) continue;
            for (Message m : messages) {
                // Skip messages that is in the black list.
                if (othRouter.isBlacklistedMessage(m.getId())) continue;
                // Skip messages that the other one has.
                if (othRouter.hasMessage(m.getId())) continue;
                // Skip messages that the other one is unable to buffer.
                if (m.getSize() > othRouter.getBufferSize()) continue;
                if (predForDeliverHost(othRouter, m) >= FILITER_THRESHOLD)
                    // The other node has higher probability of delivery.
                    messagesList.add(new Tuple<Message, Connection>(m, con));
            }
        }
        if (messagesList.size() == 0) return null;
        // Sort the message-connection tuples.
        messagesList.sort(new TupleComparator());
        tryMessagesForConnected(messagesList);
        return null;
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by
     * their delivery probability by the host on the other side of the
     * connection (GRTRMax).
     */
    private class TupleComparator implements Comparator
            <Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1,
                           Tuple<Message, Connection> tuple2) {
            // Delivery probability of tuple1's message with tuple1's connection.
            DTNHost r1 = tuple1.getValue().getOtherNode(getHost());
            double p1 = getExtraPredFor(r1);
            // -"- tuple2...
            DTNHost r2 = tuple1.getValue().getOtherNode(getHost());
            double p2 = getExtraPredFor(r2);
            /* Bigger probability should come first. */
            // Equal probabilities -> let queue mode decide.
            if (p2-p1 == 0) return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            else if (p2-p1 < 0) return -1;
            else return 1;
        }
    }

    /**
     * This method is used to call just before a transfer is finalized (by
     * {@link ActiveRouter#update()}).
     * Reduces the number of copies we have left for a message.
     * In binary Spray and Wait, sending host is left with floor(n/2) copies,
     * but in standard mode, nrof copies left is reduced by one.
     */
    @Override
    protected void transferDone(Connection con) {
        Integer nrofCopies;
        String msgId = con.getMessage().getId();
        /* Get this router's copy of the message. */
        Message msg = getMessage(msgId);
        /* Message has been dropped from the buffer after start of transfer -> no need to reduce amount of copies. */
        if (msg == null)  return;
        /* Reduce the amount of copies left. */
        nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
        /* In binary mode the sending node keeps ceil(n/2) copies. */
        nrofCopies = (int)Math.ceil(nrofCopies/2.0);
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
    }

    /**
     * This method is used to return the highest drop possibility of message or untrust message
     * (the drop possibility is higher than we expected) in the message buffer (that is not being sent if excludeMsgBeingSent is true).
     * @param excludeMsgBeingSent If true, excludes message(s) that are being sent from the drop message check (i.e. if highest drop possibility of the message is
     * being sent, the second-highest drop possibility of the message is returned).
     * @return The highest drop possibility of message or null if no message could be returned.
     * (no messages in buffer or all messages in the buffer are being sent and
     * exludeMsgBeingSent is true)
     */
    protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
        Collection<Message> messages = this.getMessageCollection();
        Message worst = null;
        float candidateResult;
        float worstResult = 0;
        for (Message m : messages) {
            // skip the message(s) that router is sending.
            if (excludeMsgBeingSent && isSending(m.getId())) continue;
            if (worst == null ) {
                worst = m;
                // Calculate the drop possibility of the current message.
                worstResult = priorityForDiscard(worst);
            } else {
                candidateResult = priorityForDiscard(m);
                // If the current message has too high drop possibility, just discard it!
                if (candidateResult > MAX_DROP) return m;
                else if (candidateResult > worstResult) {
                    /* If the drop possibility of the current message is greater than the worst ones, make it as the worst. */
                    worst = m;
                    worstResult = candidateResult;
                }
            }
        }

        return worst;
    }

    /**
     * This method is used to return the drop possibility of the current message.
     * @param m the current message.
     * @return the drop possibility of the current message.
     */
    private float priorityForDiscard (Message m){
        float penaltyForTTL = 1 - (float)m.getTtl()/m.getInitTtl();
        float penaltyForLocation = Math.min(euclideanDistance(getHost(), m.getTo()) / euclideanDistance(m.getFrom(), m.getTo()), 1);
        float penaltyForSize = (float)m.getSize()/getBufferSize();
        return (float) (Math.min(penaltyForTTL, penaltyForLocation) * 0.65 + penaltyForSize * 0.35);
    }

    /**
     * This method is used to return the distance between 2 hosts.
     * @param senderHost the host who carries the message or the source host.
     * @param receiverHost the destination host.
     * @return the distance between given hosts.
     */
    private float euclideanDistance (DTNHost senderHost, DTNHost receiverHost){
        double deltaX = senderHost.getLocation().getX() - receiverHost.getLocation().getX();
        double deltaY = senderHost.getLocation().getY() - receiverHost.getLocation().getY();
        return (float)Math.sqrt(deltaX*deltaX + deltaY*deltaY);
    }

    /**
     * This method is used to  create a replicate of this router. The replicate has the same
     * settings as this router but empty buffers and routing tables.
     * @return The replicate.
     */
    @Override
    public MessageRouter replicate() {
        return new UtilityRouter(this);
    }
}
