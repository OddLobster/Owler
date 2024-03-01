package eu.ows.owler.log;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.AbstractStatusUpdaterBolt;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.URLPartitioner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusLoggerBolt extends AbstractStatusUpdaterBolt {

    private static final Logger LOG = LoggerFactory.getLogger(StatusLoggerBolt.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private static DateFormat iso8601_dateformat =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private URLPartitioner partitioner;

    private ObjectMapper mapper;

    @Override
    public void prepare(
            java.util.Map<String, Object> stormConf,
            TopologyContext context,
            OutputCollector collector) {
        super.prepare(stormConf, context, collector);

        partitioner = new URLPartitioner();
        partitioner.configure(stormConf);

        mapper = new ObjectMapper();
    }

    @Override
    protected void store(
            @NotNull String url,
            @NotNull Status status,
            @NotNull Metadata metadata,
            @NotNull Optional<Date> nextFetch,
            @NotNull Tuple t) {
        String timestamp = iso8601_dateformat.format(new Date());
        String crawlId = "DEFAULT";
        String metadataJsonString = "-";

        Map<String, String[]> mdMap = metadata.asMap();
        if (mdMap.size() > 0) {
            try {
                metadataJsonString = mapper.writeValueAsString(mdMap);
            } catch (Exception e) {
                LOG.error("Error while serializing metadata to JSON", e);
            }
        }

        String nextFetchString = "-";
        if (nextFetch.isPresent()) {
            nextFetchString = iso8601_dateformat.format(nextFetch.get());
        }

        String partitionKey = partitioner.getPartition(url, metadata);
        if (partitionKey == null) {
            partitionKey = "_DEFAULT_";
        }

        String message =
                String.format(
                        "%28s %12s %29s %8s %s %s %s",
                        timestamp,
                        status,
                        nextFetchString,
                        crawlId,
                        url,
                        partitionKey,
                        metadataJsonString);
        STATUS_LOG.trace(message);
        LOG.debug(message);
        super.ack(t, url);
    }
}
