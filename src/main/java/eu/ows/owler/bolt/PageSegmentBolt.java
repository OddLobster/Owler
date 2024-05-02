package eu.ows.owler.bolt;

import com.digitalpebble.stormcrawler.parse.Outlink;

import java.util.ArrayList;
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
import org.xml.sax.InputSource;
import org.apache.storm.tuple.Values;

import java.nio.charset.Charset;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.document.TextDocument;
import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.sax.BoilerpipeSAXInput;
import de.l3s.boilerpipe.sax.HTMLDocument;
import de.l3s.boilerpipe.sax.HTMLFetcher;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import java.nio.ByteBuffer;
import java.net.URL;

public class PageSegmentBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(PageSegmentBolt.class);
    private final int NUM_WORDS_BLOCK = 10;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "blocks"));
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    private List<TextBlock> processBlocks(List<TextBlock> blocks)
    {
        List<TextBlock> processedBlocks = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++)
        {
            TextBlock curr = blocks.get(i);
            if (curr.getNumWords() < NUM_WORDS_BLOCK) {continue;}

            processedBlocks.add(curr);
        }

        return processedBlocks;
    }

    @Override
    public void execute(Tuple input) {
        LOG.info("Called PageSegmentBolt");
        String url = input.getStringByField("url");
        // this.collector.emit(input, new Values(url, content, metadata, text));
        LOG.info("Got url {}", url);
        long startTime = System.currentTimeMillis();
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");

        String charset = CharsetIdentification.getCharset(metadata, content, -1);
        String html = Charset.forName(charset).decode(ByteBuffer.wrap(content)).toString();
        String text;
        List<TextBlock> blocks;
        try
        {
            HTMLDocument htmlDoc = new HTMLDocument(html.getBytes(), Charset.forName(charset));
            InputSource is = htmlDoc.toInputSource();
            TextDocument document = new BoilerpipeSAXInput(is).getTextDocument();
            blocks = document.getTextBlocks();


            LOG.info("--------------------------------");
            LOG.info("Number of blocks: {}", blocks.size());
            for (TextBlock block : blocks) {
                if (block.isContent())
                {
                    LOG.info(Boolean.toString(block.isContent()));
                    LOG.info(block.getText());
                    LOG.info(String.valueOf(block.getNumWords()));
                    LOG.info("------");
                }
            }        
            text = document.getText(true, true);
        }
        catch (Exception e)
        {
            LOG.info("Boilerpipe failed to parse file");
            text = "";
            blocks = new ArrayList<>();
        }
        blocks = processBlocks(blocks);

        long endTime = System.currentTimeMillis();
        LOG.info("Time: {}", endTime-startTime);
        collector.emit(input, new Values(url, content, metadata, blocks));

        collector.ack(input);
    }
}
