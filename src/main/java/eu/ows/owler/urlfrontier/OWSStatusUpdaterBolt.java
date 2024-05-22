package eu.ows.owler.urlfrontier;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.MetadataTransfer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import eu.ows.urlfrontier.URLFrontierGrpc;
import eu.ows.urlfrontier.URLFrontierGrpc.URLFrontierStub;
import eu.ows.urlfrontier.URLFrontierProto.AckMessage;
import eu.ows.urlfrontier.URLFrontierProto.GrpcURLItem;
import eu.ows.urlfrontier.URLFrontierProto.GrpcURLItemWithOutlinks;
import eu.ows.urlfrontier.URLFrontierProto.StringList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OWSStatusUpdaterBolt extends BaseRichBolt {

    private static final Logger LOG = LoggerFactory.getLogger(OWSStatusUpdaterBolt.class);

    private static final String URLFRONTIER_HOST_KEY = "urlfrontier.host";
    private static final String URLFRONTIER_PORT_KEY = "urlfrontier.port";
    private static final String URLFRONTIER_DEFAULT_HOST = "localhost";
    private static final int URLFRONTIER_DEFAULT_PORT = 7071;

    private static final int MAX_FETCH_ERRORS = 3;
    private static final int MAX_MESSAGES_IN_FLIGHT = 1_000;
    private static final int MAX_QUEUE_SIZE = 1_000;
    private static final int THROTTLE_SENDING_MS = 5;
    private static final int UPLOAD_THREADS = 1;
    private static final int MAX_OUTLINKS = 100;

    private MetadataTransfer mdTransfer;
    private ManagedChannel channel;
    private StreamObserver<GrpcURLItemWithOutlinks> requestObserver;

    private Queue<Tuple> queue = new ConcurrentLinkedQueue<>();
    private AtomicInteger messagesInFlight = new AtomicInteger(0);
    private ExecutorService executor;

    private boolean isClosed = false;

    @Override
    public void prepare(
            Map<String, Object> stormConf, TopologyContext context, OutputCollector collector) {
        mdTransfer = MetadataTransfer.getInstance(stormConf);

        String host =
                ConfUtils.getString(stormConf, URLFRONTIER_HOST_KEY, URLFRONTIER_DEFAULT_HOST);
        int port = ConfUtils.getInt(stormConf, URLFRONTIER_PORT_KEY, URLFRONTIER_DEFAULT_PORT);

        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        URLFrontierStub frontier = URLFrontierGrpc.newStub(channel).withWaitForReady();

        executor = Executors.newFixedThreadPool(UPLOAD_THREADS);

        for (int i = 0; i < UPLOAD_THREADS; i++) {
            executor.submit(
                    () -> {
                        runUploadThread(collector, frontier);
                    });
        }
    }

    private void runUploadThread(OutputCollector collector, URLFrontierStub frontier) {
        Cache<String, Tuple> waitAck =
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .removalListener(
                                new RemovalListener<String, Tuple>() {
                                    @Override
                                    public void onRemoval(
                                            String id, Tuple tuple, RemovalCause cause) {
                                        messagesInFlight.decrementAndGet();
                                        if (cause.equals(RemovalCause.EXPIRED)) {
                                            collector.fail(tuple);
                                        }
                                    }
                                })
                        .build();

        Cache<String, String> alreadySentOutlinks =
                Caffeine.newBuilder()
                        .maximumSize(250_000)
                        .expireAfterAccess(4, TimeUnit.HOURS)
                        .build();

        StreamObserver<AckMessage> responseObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(AckMessage ackMessage) {
                        Tuple tuple = waitAck.getIfPresent(ackMessage.getID());
                        waitAck.invalidate(ackMessage.getID());

                        if (tuple != null) {
                            if (AckMessage.Status.FAIL.equals(ackMessage.getStatus())) {
                                queue.add(tuple);
                            } else {
                                collector.ack(tuple);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.error("Error received", t);
                    }

                    @Override
                    public void onCompleted() {
                        LOG.info("Completed");
                    }
                };

        requestObserver = frontier.putURLs(responseObserver);

        while (!isClosed) {
            Tuple tuple = queue.poll();
            if (tuple != null) {
                try {
                    update(tuple, waitAck, alreadySentOutlinks);
                } catch (Exception e) {
                    LOG.error("Exception caught when updating", e);
                    collector.fail(tuple);
                }
            }

            do {
                try {
                    Thread.sleep(THROTTLE_SENDING_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                waitAck.cleanUp();
            } while ((messagesInFlight.get() >= MAX_MESSAGES_IN_FLIGHT || queue.isEmpty())
                    && !isClosed);
        }

        requestObserver.onCompleted();
    }

    @Override
    public void execute(Tuple tuple) {
        while (queue.size() >= MAX_QUEUE_SIZE) {
            try {
                Thread.sleep(THROTTLE_SENDING_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        queue.add(tuple);
    }

    private void update(
            Tuple tuple, Cache<String, Tuple> waitAck, Cache<String, String> alreadySentOutlinks)
            throws Exception {
        String url = tuple.getStringByField("url");
        Status status = (Status) tuple.getValueByField("status");
        Metadata metadata = (Metadata) tuple.getValueByField("metadata");

        metadata = mdTransfer.filter(metadata);

        // Store last processed or discovery date in UTC
        final String nowAsString = Instant.now().toString();
        if (status.equals(Status.DISCOVERED)) {
            metadata.setValue("discoveryDate", nowAsString);
        } else {
            metadata.setValue("lastProcessedDate", nowAsString);
        }

        // Too many fetch errors
        if (status.equals(Status.FETCH_ERROR)) {
            String errorCount = metadata.getFirstValue(Constants.fetchErrorCountParamName);
            int count = 0;
            try {
                count = Integer.parseInt(errorCount);
            } catch (NumberFormatException e) {
            }
            count++;
            if (count >= MAX_FETCH_ERRORS) {
                status = Status.ERROR;
                metadata.setValue(Constants.STATUS_ERROR_CAUSE, "maxFetchErrors");
            } else {
                metadata.setValue(Constants.fetchErrorCountParamName, Integer.toString(count));
            }
        } else {
            metadata.remove(Constants.fetchErrorCountParamName);
            metadata.remove(Constants.STATUS_ERROR_CAUSE);
            metadata.remove(Constants.STATUS_ERROR_MESSAGE);
            metadata.remove(Constants.STATUS_ERROR_SOURCE);
        }

        final Map<String, StringList> mdCopy = new HashMap<>(metadata.size());
        for (String key : metadata.keySet()) {
            String[] values = metadata.getValues(key);
            if (values != null) {
                StringList.Builder builder = StringList.newBuilder();
                for (String value : values) {
                    builder.addValues(value);
                }
                mdCopy.put(key, builder.build());
            }
        }
        mdCopy.remove("outlinks");

        final List<String> outlinks = new ArrayList<>();
        if (metadata.containsKey("outlinks")) {
            for (String outlink : metadata.getValues("outlinks")) {
                if (outlink != null
                        && alreadySentOutlinks.getIfPresent(outlink) == null
                        && outlinks.size() < MAX_OUTLINKS) {
                    outlinks.add(outlink);
                    alreadySentOutlinks.put(outlink, outlink);
                }
            }
        }

        GrpcURLItem item = GrpcURLItem.newBuilder().setUrl(url).putAllMetadata(mdCopy).build();

        String id = UUID.randomUUID().toString();
        waitAck.put(id, tuple);
        messagesInFlight.incrementAndGet();

        GrpcURLItemWithOutlinks itemWithOutlinks =
                GrpcURLItemWithOutlinks.newBuilder()
                        .setID(id)
                        .setItem(item)
                        .addAllOutlinks(outlinks)
                        .build();

        requestObserver.onNext(itemWithOutlinks);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {}

    @Override
    public void cleanup() {
        isClosed = true;
        channel.shutdown();
        executor.shutdown();
    }
}