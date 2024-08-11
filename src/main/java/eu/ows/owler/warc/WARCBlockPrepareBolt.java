package eu.ows.owler.warc;

import java.util.Map;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.Metadata;

import eu.ows.owler.util.PageData;

public class WARCBlockPrepareBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(WARCBlockPrepareBolt.class);


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        
    }

    @Override
    public void execute(Tuple input) {
        String url = input.getStringByField("url");
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");
        PageData pageData = (PageData) input.getValueByField("pageData");
        LOG.info("URL: {}", url);

        for(String text : pageData.blockTexts)
        {
            LOG.info("Sending text to WARCBolt: ", text);
            byte[] textBytes = text.getBytes();
            collector.emit(input, new Values(url, textBytes, metadata));
        }

        LOG.info("Preprocessing Content for WARCBolt");
      
        
        collector.ack(input);
    }
}
