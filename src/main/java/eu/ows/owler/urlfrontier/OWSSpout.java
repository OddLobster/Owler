package eu.ows.owler.urlfrontier;

import static com.digitalpebble.stormcrawler.urlfrontier.Constants.*;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.AbstractQueryingSpout;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import eu.ows.urlfrontier.URLFrontierGrpc;
import eu.ows.urlfrontier.URLFrontierGrpc.URLFrontierStub;
import eu.ows.urlfrontier.URLFrontierProto.Empty;
import eu.ows.urlfrontier.URLFrontierProto.GrpcURLItem;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OWSSpout extends AbstractQueryingSpout {

    private static final Logger LOG = LoggerFactory.getLogger(OWSSpout.class);

    private ManagedChannel channel;
    private URLFrontierStub frontier;

    @Override
    public void open(
            Map<String, Object> stormConf,
            TopologyContext context,
            SpoutOutputCollector collector) {
        super.open(stormConf, context, collector);

        String host =
                ConfUtils.getString(stormConf, URLFRONTIER_HOST_KEY, URLFRONTIER_DEFAULT_HOST);
        int port = ConfUtils.getInt(stormConf, URLFRONTIER_PORT_KEY, URLFRONTIER_DEFAULT_PORT);
        String address = host + ":" + port;

        channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();

        frontier = URLFrontierGrpc.newStub(channel).withWaitForReady();
        LOG.debug("State of Channel: {}", channel.getState(false));
    }

    @Override
    protected void populateBuffer() {
        LOG.debug("Populating buffer");

        Empty request = Empty.newBuilder().build();

        StreamObserver<GrpcURLItem> responseObserver =
                new StreamObserver<>() {

                    @Override
                    public void onNext(GrpcURLItem item) {
                        Metadata metadata = new Metadata();
                        item.getMetadataMap()
                                .forEach(
                                        (k, v) -> {
                                            for (int index = 0;
                                                    index < v.getValuesCount();
                                                    index++) {
                                                metadata.addValue(k, v.getValues(index));
                                            }
                                        });
                        buffer.add(item.getUrl(), metadata);
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (t instanceof StatusRuntimeException) {
                            StatusRuntimeException e = (StatusRuntimeException) t;
                            StringBuilder sb =
                                    new StringBuilder("StatusRuntimeException: ")
                                            .append(e.getStatus().toString());
                            if (e.getTrailers() != null) {
                                sb.append(" ").append(e.getTrailers());
                            }
                            LOG.error(sb.toString(), e);
                        } else {
                            LOG.error("Exception caught: ", t);
                        }

                        markQueryReceivedNow();
                    }

                    @Override
                    public void onCompleted() {
                        markQueryReceivedNow();
                    }
                };

        isInQuery.set(true);

        frontier.getURLs(request, responseObserver);
    }

    @Override
    public void ack(Object msgId) {
        LOG.debug("Ack for {}", msgId);
        super.ack(msgId);
    }

    @Override
    public void fail(Object msgId) {
        LOG.info("Fail for {}", msgId);
        super.fail(msgId);
    }

    @Override
    public void close() {
        super.close();

        if (channel != null && !channel.isShutdown()) {
            LOG.info("Shutting down connection to URLFrontier service.");
            channel.shutdown();
        } else {
            LOG.warn(
                    "Tried to shutdown connection to URLFrontier service that was already shutdown.");
        }
    }
}