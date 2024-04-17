package eu.ows.owler.bolt;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HITSBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(HITSBolt.class);

    public Map<String, Set<String>> links = new HashMap<>();
    public Map<String, Double> authorities = new HashMap<>();
    public Map<String, Double> hubs = new HashMap<>();
    private int triggerCounter = 0;
    private final int computationTriggerThreshold = 100;
    private int callCounter = 0;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        String url = input.getStringByField("url");

        this.collector.emit(input, new Values(url, content, metadata, text));
        collector.ack(input);
    }
}
