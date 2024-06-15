package eu.ows.owler.bolt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import com.digitalpebble.stormcrawler.parse.Outlink;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.ows.owler.util.PageData;

public class HITSBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(HITSBolt.class);

    public Map<String, Set<String>> webgraph = new HashMap<>();

    public Map<String, Set<String>> links = new HashMap<>();
    public Map<String, Double> authorities = new HashMap<>();
    public Map<String, Double> hubs = new HashMap<>();
    private int triggerCounter = 0;
    private final int computationTriggerThreshold = 100;
    private int callCounter = 0;
    private Boolean createWebGraph = true;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    public void addLinkToGraph(String from, String to) {
        links.putIfAbsent(from, new HashSet<String>());
        links.get(from).add(to);

        webgraph.putIfAbsent(from, new HashSet<String>());
        webgraph.get(from).add(to);

        // init hub and auth scores
        authorities.putIfAbsent(from, 1.0);
        authorities.putIfAbsent(to, 1.0);
        hubs.putIfAbsent(from, 1.0);
        hubs.putIfAbsent(to, 1.0);
    }

    private void writeGraphToFile()
    {
        int numFiles = 0;
        try (Stream<Path> filesStream = Files.list(Paths.get("/outdata/graph"))) {
            numFiles = (int) filesStream.filter(Files::isRegularFile).count();
        } catch (Exception e)
        {
            LOG.info("Failed to get number of files in Folder {}: {}", "/outdata/graph", e);
        }

        File file = new File("/outdata/graph/webgraph"+numFiles+".json");

        // Serialize
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(file, webgraph);
            LOG.info("Serialization successful. Data written to webgraph.json");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readWebGraphFromFile()
    {
        File file = new File("/outdata/graph/webgraph.json");

        // Deserialize
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map<String, Set<String>> webgraph = objectMapper.readValue(file, new TypeReference<Map<String, Set<String>>>() {});
            System.out.println("Deserialization successful. Data read from webgraph.json");
            
            // Print the data to verify
            for (Map.Entry<String, Set<String>> entry : links.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performHITS(int numIterations) {
        for (int i = 0; i < numIterations; i++) {

            Map<String, Double> hubsCopy = new HashMap<>(hubs);
            Map<String, Double> authoritiesCopy = new HashMap<>(authorities);

            double norm = 0;
            // calculate autority values
            for (Map.Entry<String, Double> entry : authorities.entrySet()) {
                String page = entry.getKey();
                double authScore = 0;
                for (Map.Entry<String, Set<String>> inPage : links.entrySet()) {
                    // only consider incoming pages
                    if (inPage.getValue().contains(page)) {
                        authScore += hubsCopy.get(inPage.getKey());
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
                    hubScore += authoritiesCopy.get(outPage);
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
        PageData pageData = (PageData) input.getValueByField("pageData");

        List<String> childUrls = new ArrayList<>();
        for (List<String> block : pageData.blockLinks) {
            childUrls.addAll(block);
        }

        callCounter += 1;
        LOG.info("Called HITS {} times", callCounter);
        for (String child : childUrls) {
            addLinkToGraph(url, child);
        }

        triggerCounter += 1;
        if (triggerCounter < computationTriggerThreshold) {
            return;
        }
        if (createWebGraph)
        {
            writeGraphToFile();
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

    public void performHITSTest(int numIterations) {
        performHITS(numIterations);
    }

    public Map<String, Double> getHubs() {
        return hubs;
    }

    public Map<String, Set<String>> getLinks() {
        return links;
    }

    public Map<String, Double> getAuthorities() {
        return authorities;
    }
}
