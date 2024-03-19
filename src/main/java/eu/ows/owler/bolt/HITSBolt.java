package eu.ows.owler.bolt;

import com.digitalpebble.stormcrawler.parse.Outlink;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    private Map<String, Set<String>> links = new HashMap<>();
    private Map<String, Double> authorities = new HashMap<>();
    private Map<String, Double> hubs = new HashMap<>();
    private int triggerCounter = 0;
    private final int computationTriggerThreshold = 100;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    private void addLinkToGraph(String from, String to) {
        links.putIfAbsent(from, new HashSet<String>());
        links.get(from).add(to);

        // init hub and auth scores
        authorities.putIfAbsent(from, 1.0);
        authorities.putIfAbsent(to, 1.0);
        hubs.putIfAbsent(from, 1.0);
        hubs.putIfAbsent(to, 1.0);
    }

    private void performHITS(int numIterations) {
        for (int i = 0; i < numIterations; i++) {
            double norm = 0;
            // calculate autority values
            for (Map.Entry<String, Double> entry : authorities.entrySet()) {
                String page = entry.getKey();
                double authScore = 0;
                for (Map.Entry<String, Set<String>> inPage : links.entrySet()) {
                    // only consider incoming pages
                    if (inPage.getValue().contains(page)) {
                        authScore += hubs.get(inPage.getKey());
                    }
                }
                authorities.put(page, authScore);
                norm += authScore * authScore;
            }

            // normalize authority values
            norm = Math.sqrt(norm);
            for (Map.Entry<String, Double> entry : authorities.entrySet()) {
                authorities.put(entry.getKey(), entry.getValue() / norm);
            }

            // calculate hub values
            norm = 0;
            for (Map.Entry<String, Double> entry : hubs.entrySet()) {
                String page = entry.getKey();
                double hubScore = 0;
                for (String outPage : links.getOrDefault(page, new HashSet<>())) {
                    hubScore += authorities.get(outPage);
                }
                hubs.put(page, hubScore);
                norm += hubScore * hubScore;
            }

            // normalize hub values
            norm = Math.sqrt(norm);
            for (Map.Entry<String, Double> entry : hubs.entrySet()) {
                hubs.put(entry.getKey(), entry.getValue() / norm);
            }
        }
    }

    @Override
    public void execute(Tuple input) {

        String url = input.getStringByField("url");
        List<Outlink> child_urls = (List<Outlink>) input.getValueByField("child_urls");

        LOG.info("HITS BOLT");
        LOG.info("Parent: {}", url);
        for (Outlink child : child_urls) {
            addLinkToGraph(url, child.getTargetURL());
        }

        triggerCounter += 1;
        if (triggerCounter < computationTriggerThreshold) {
            return;
        }
        LOG.info("Performing HITS");
        performHITS(5);

        LOG.info("Num. links in graph: {}", links.size());

        List<Map.Entry<String, Double>> bestHubs =
                hubs.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(10)
                        .collect(Collectors.toList());

        LOG.info("Best 10 hubs");
        bestHubs.forEach(entry -> LOG.info("url: {} - val: {}", entry.getKey(), entry.getValue()));

        List<Map.Entry<String, Double>> bestAuthorities =
                authorities.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(10)
                        .collect(Collectors.toList());

        LOG.info("Best 10 authorities");
        bestAuthorities.forEach(
                entry -> LOG.info("url: {} - val: {}", entry.getKey(), entry.getValue()));

        // reset HITS
        triggerCounter = 0;
        links = new HashMap<>();
        authorities = new HashMap<>();
        hubs = new HashMap<>();

        // this.collector.emit(input, new Values(url, content, metadata, text));

        collector.ack(input);
    }
}
