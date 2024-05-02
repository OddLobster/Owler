package eu.ows.owler.log;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.bolt.StatusEmitterBolt;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectionBolt extends StatusEmitterBolt {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectionBolt.class);

    private boolean statusRedirection = true;

    private OutputCollector collector;

    @Override
    public void prepare(
            Map<String, Object> topoConf, TopologyContext context, OutputCollector collector) {
        statusRedirection = ConfUtils.getBoolean(topoConf, "status.redirection", true);
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String url = tuple.getStringByField("url");
        Metadata metadata = (Metadata) tuple.getValueByField("metadata");
        Status status = (Status) tuple.getValueByField("status");

        Values values = new Values(url, metadata, status);

        if (status == Status.DISCOVERED && statusRedirection) {
            collector.emit("logfile_status", tuple, values);
        } else {
            collector.emit("status", tuple, values);
        }

        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream("logfile_status", new Fields("url", "metadata", "status"));
    }
}
