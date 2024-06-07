package eu.ows.owler.bolt;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;
import com.digitalpebble.stormcrawler.util.URLUtil;

import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.document.TextDocument;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import de.l3s.boilerpipe.sax.BoilerpipeSAXInput;
import de.l3s.boilerpipe.sax.HTMLDocument;
import eu.ows.owler.util.PageData;


public class PageSegmentBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(PageSegmentBolt.class);
    private final int NUM_WORDS_BLOCK = 10;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "pageData"));
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
        PageData pageData = new PageData();


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
        List<String> blockTexts = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++)
        {   
            String blockText = blocks.get(i).getText();
            
            Matcher matcher = pattern.matcher(blockText);
            StringBuilder textReplacedLinks = new StringBuilder(blockText);
            List<String> linksInBlock = new ArrayList<>();
            while (matcher.find()) {
                textReplacedLinks = new StringBuilder(textReplacedLinks);
                int linkIndex = Integer.parseInt(matcher.group(1));
                try{
                    linksInBlock.add(URLUtil.resolveURL(new URL(url), links.get(linkIndex)).toString());
                } catch (MalformedURLException e)
                {
                    LOG.info("MALFORMED URL EXCEPTION IN PAGE SEGMENT: {}", url);
                    LOG.info("EXECPTION: {}", url);
                }
                // linksInBlock.add(links.get(linkIndex));
                String anchorText = anchorTexts.get(linkIndex);
                textReplacedLinks = textReplacedLinks.replace(matcher.start(), matcher.end(), " " + anchorText + " ");
                matcher = pattern.matcher(textReplacedLinks.toString());
            }

            if (blocks.get(i).isContent())
            {
                blockLinks.add(linksInBlock);
                contentBlocks.add(blocks.get(i));
                contentText += textReplacedLinks.toString();
                blockTexts.add(textReplacedLinks.toString());
            }
        }

        LOG.info("Number of content blocks: {} in url: {}", contentBlocks.size(), url);   

        long endTime = System.currentTimeMillis();
        LOG.info("PageSegmentBolt processing took time {} ms", endTime-startTime);

        if (contentBlocks.size() == 0)
        {
            LOG.info("Stop processing tuple. No content on webpage.");
            collector.fail(input);
        }else{
            pageData.contentBlocks = contentBlocks;
            pageData.blockLinks = blockLinks;
            pageData.contentText = contentText;
            pageData.blockTexts = blockTexts;
            pageData.url = url;
            collector.emit(input, new Values(url, content, metadata, pageData));
            collector.ack(input);
        }
    }
}
