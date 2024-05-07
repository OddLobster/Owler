package eu.ows.owler.bolt;

import com.digitalpebble.stormcrawler.parse.Outlink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.charset.Charset;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.document.TextDocument;
import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import de.l3s.boilerpipe.sax.BoilerpipeSAXInput;
import de.l3s.boilerpipe.sax.HTMLDocument;
import de.l3s.boilerpipe.sax.HTMLFetcher;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import java.nio.ByteBuffer;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;

public class PageSegmentBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(PageSegmentBolt.class);
    private final int NUM_WORDS_BLOCK = 10;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "blocks", "blockLinks", "wholeText"));
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
        String url = input.getStringByField("url");
        long startTime = System.currentTimeMillis();
        byte[] content = input.getBinaryByField("content");
        final Metadata metadata = (Metadata) input.getValueByField("metadata");
        if (!metadata.containsKey("depth"))
        {
            metadata.addValue("depth", "1");
        }

        String charset = CharsetIdentification.getCharset(metadata, content, -1);
        String html = Charset.forName(charset).decode(ByteBuffer.wrap(content)).toString();
        
        Document jsoupDoc = Jsoup.parse(html);
        Elements hrefs = jsoupDoc.select("a[href]");
        List<String> links = new ArrayList<>();
        List<String> anchorTexts = new ArrayList<>();
        for (Element e : hrefs)
        {
            String link = e.attr("href");
            anchorTexts.add(e.text());

            e.text("|LINK_"+links.size()+"|");
            links.add(link);
        }
        String annotatedHtml = jsoupDoc.toString();

        String text;
        List<TextBlock> blocks;
        try
        {
            HTMLDocument htmlDoc = new HTMLDocument(annotatedHtml.getBytes(), Charset.forName(charset));
            InputSource is = htmlDoc.toInputSource();
            TextDocument document = new BoilerpipeSAXInput(is).getTextDocument();
            Boolean changedDocument = CommonExtractors.DEFAULT_EXTRACTOR.process(document);
            blocks = document.getTextBlocks();
            text = document.getText(true, true);
        }
        catch (Exception e)
        {
            LOG.info("Boilerpipe failed to parse file");
            text = "";
            blocks = new ArrayList<>();
        }
        blocks = processBlocks(blocks);
        LOG.info("Number of blocks: {} in url: {}", blocks.size(), url);
        Pattern pattern = Pattern.compile("\\|LINK_(\\d+)\\|");   
        String contentText = "";
        List<TextBlock> contentBlocks = new ArrayList<>();
        List<List<String>> blockLinks = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++)
        {   
            String blockText = blocks.get(i).getText();
            StringBuilder textReplacedLinks = new StringBuilder(blockText);
            
            Matcher matcher = pattern.matcher(blockText);
            List<String> linksInBlock = new ArrayList<>();
            while (matcher.find()) {
                int linkIndex = Integer.parseInt(matcher.group(1));
                linksInBlock.add(links.get(linkIndex));
                String anchorText = anchorTexts.get(linkIndex);
                textReplacedLinks.replace(matcher.start(), matcher.start()+anchorText.length(), anchorText);
            }
            blockLinks.add(linksInBlock);
            
            if (blocks.get(i).isContent())
            {
                contentBlocks.add(blocks.get(i));
                contentText += textReplacedLinks.toString();
            }
        }

        LOG.info("Number of content blocks: {} in url: {}", contentBlocks.size(), url);   

        long endTime = System.currentTimeMillis();
        LOG.info("Time: {}", endTime-startTime);
        collector.emit(input, new Values(url, content, metadata, contentBlocks, blockLinks, contentText));
        collector.ack(input);
    }
}
