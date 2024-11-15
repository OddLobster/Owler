package eu.ows.owler.bolt;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;


public class PageSegmentBolt extends BaseRichBolt {
    private OutputCollector collector;
    private static final Logger LOG = LoggerFactory.getLogger(PageSegmentBolt.class);
    private final int NUM_WORDS_BLOCK = 5;

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
    
    private static String getHtmlForBlock(String originalHtml, String blockText) {
        Document document = Jsoup.parse(originalHtml);

        Element elementContainingText = findElementContainingText(document, blockText);
        if (elementContainingText == null)
        {
            return "coudlnt find element text: " + blockText;
        }
        return elementContainingText.html();
    }

    private static Element findElementContainingText(Element root, String text) {
        Elements elements = root.getAllElements();
        Element innermostElement = null;

        for (Element element : elements) {
            if (element.text().contains(text)) {
                if (innermostElement == null || innermostElement.text().length() >= element.text().length()) {
                    innermostElement = element;
                }
            }
        }
        return innermostElement;
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

        Readability4J readability4J = new Readability4J(url, html);
        try
        {
            Article article = readability4J.parse();
            String extractedContentHtml = article.getContent();
            String extractedContentHtmlWithUtf8Encoding = article.getContentWithUtf8Encoding();
            String extractedContentPlainText = article.getTextContent();
        } catch (Exception e)
        {
            LOG.info("Failed to parse Readability4J");
        }

        //TODO remove
        // try 
        // {
        //     PrintWriter writer = new PrintWriter(new FileWriter("/outdata/documents/"+url.replace("/", "").replace(".", "")+"_readability4j.html"));
        //     writer.println("@@@@@@@@@@ extractedContentHtml");
        //     writer.println(extractedContentHtml);
        //     writer.println("@@@@@@@@@@ extractedContentHtmlWithUtf8Encoding");
        //     writer.println(extractedContentHtmlWithUtf8Encoding);
        //     writer.println("@@@@@@@@@@ extractedContentPlainText");
        //     writer.println(extractedContentPlainText);
        //     writer.close();
        // }
        // catch (Exception e) 
        // {
        //     LOG.info("Failed to write prediction debug info to file {}", e);
        //     e.printStackTrace();
        // }

        // //TODO remove
        // try 
        // {
        //     PrintWriter writer = new PrintWriter(new FileWriter("/outdata/documents/"+url.replace("/", "").replace(".", "")+".html"));
        //     writer.println(html);

        //     writer.close();
        // }
        // catch (Exception e) 
        // {
        //     LOG.info("Failed to write prediction debug info to file {}", e);
        //     e.printStackTrace();
        // }
        
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
            // LOG.info("htmldoc getData() {}",htmlDoc.getData());
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
                String child_url = "";
                try{
                    child_url = URLUtil.resolveURL(new URL(url), links.get(linkIndex)).toString();
                } catch (MalformedURLException e)
                {
                    LOG.debug("MALFORMED URL EXCEPTION IN PAGE SEGMENT: {}", url);
                    LOG.debug("EXECPTION: {}", e);
                }
                try{
                    URI uri = new URI(child_url);
                    URI normalizedUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
                    linksInBlock.add(normalizedUri.toString());
                } catch (URISyntaxException e)
                {
                    LOG.debug("EXECPTION: {}", e);
                }
                // linksInBlock.add(links.get(linkIndex));
                String anchorText = anchorTexts.get(linkIndex);
                textReplacedLinks = textReplacedLinks.replace(matcher.start(), matcher.end(), anchorText);
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

        // //TODO remove
        // try 
        // {
        //     PrintWriter writer = new PrintWriter(new FileWriter("/outdata/documents/"+url.replace("/", "").replace(".", "")+"_textblocks.html"));
        //     for (String blockText : blockTexts) {
        //         writer.println("blockText: " + blockText);
        //     }
        //     writer.close();
        // }
        // catch (Exception e) 
        // {
        //     LOG.info("Failed to write prediction debug info to file {}", e);
        //     e.printStackTrace();
        // }
        


        LOG.info("Number of content blocks: {} in url: {}", contentBlocks.size(), url);   

        long endTime = System.currentTimeMillis();
        LOG.info("PageSegmentBolt processing took time {} ms", endTime-startTime);
        pageData.addBoltProcessingTime("pageSegmentBolt", endTime - startTime);

        if (contentBlocks.isEmpty())
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
