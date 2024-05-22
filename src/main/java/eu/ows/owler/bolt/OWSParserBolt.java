package eu.ows.owler.bolt;

import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.filtering.URLFilters;
import com.digitalpebble.stormcrawler.parse.DocumentFragmentBuilder;
import com.digitalpebble.stormcrawler.parse.Outlink;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseFilters;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.protocol.HttpHeaders;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.MetadataTransfer;
import com.digitalpebble.stormcrawler.util.RefreshTag;
import com.digitalpebble.stormcrawler.util.URLUtil;
import eu.ows.owler.util.RobotsTags;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DocumentFragment;
import eu.ows.owler.util.URLCache;

public class OWSParserBolt extends BaseRichBolt {

    private static final Logger LOG = LoggerFactory.getLogger(OWSParserBolt.class);

    protected OutputCollector collector;
    private MetadataTransfer metadataTransfer;
    private URLFilters urlFilters;
    private ParseFilter parseFilters = null;
    private boolean emitOutlinks = true;
    private int maxOutlinksPerPage = -1;
    private String protocolMDprefix;
    private URLCache urlCache;
    private static final String AS_IS_NEXTFETCHDATE_METADATA = "status.store.as.is.with.nextfetchdate";


    @Override
    public void prepare(
            Map<String, Object> conf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        metadataTransfer = MetadataTransfer.getInstance(conf);
        urlFilters = URLFilters.fromConf(conf);
        parseFilters = ParseFilters.fromConf(conf);
        emitOutlinks = ConfUtils.getBoolean(conf, "parser.emitOutlinks", true);
        maxOutlinksPerPage = ConfUtils.getInt(conf, "parser.emitOutlinks.max.per.page", -1);
        protocolMDprefix = ConfUtils.getString(conf, ProtocolResponse.PROTOCOL_MD_PREFIX_PARAM, "");
        String redisHost = ConfUtils.getString(conf, "redis.host", "frue_ra_redis");
        int redisPort = ConfUtils.getInt(conf, "redis.port", 6379);
        urlCache = new URLCache(redisHost, redisPort);
    }

    @Override
    public void execute(Tuple tuple) {
        final String urlString = tuple.getStringByField("url");
        final Metadata metadata = (Metadata) tuple.getValueByField("metadata");
        final byte[] content = tuple.getBinaryByField("content");
        if (!metadata.containsKey("maxLinkDepth"))
        {
            metadata.setValue("maxLinkDepth", "1");
        }

        if (urlCache.isUrlCrawled(urlString))
        {
            LOG.info("URL {} already processed", urlString);
            Instant timeNow = Instant.now();
            Instant nextFetchTime = timeNow.plus(365, ChronoUnit.DAYS);
            String nextFetchDate = DateTimeFormatter.ISO_INSTANT.format(nextFetchTime);
            metadata.setValue(AS_IS_NEXTFETCHDATE_METADATA, nextFetchDate);
            collector.emit(StatusStreamName, tuple, new Values(tuple, metadata, Status.FETCHED)); 
            collector.ack(tuple);        
            return;
        }


        LOG.info("Parsing started for {}", urlString);

        final URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            final String errorMessage = "Exception while parsing " + urlString + ": " + e;
            handleException(urlString, e, metadata, tuple, "content parsing", errorMessage);
            return;
        }

        String mimeType = metadata.getFirstValue(HttpHeaders.CONTENT_TYPE, this.protocolMDprefix);
        if (StringUtils.isNotBlank(mimeType)) {
            if (mimeType.toLowerCase().contains("html")) {
                metadata.setValue("isHTML", "true");
            } else {
                if (mimeType.toLowerCase().contains("xml")) {
                    metadata.setValue("isXML", "true");
                } else if (mimeType.toLowerCase().contains("json")) {
                    metadata.setValue("isJSON", "true");
                }

                LOG.info("URL {} has unsupported mimetype {}", url, mimeType);
                collector.emit(tuple, new Values(urlString, content, metadata, ""));
                collector.ack(tuple);
            }
        }

        final long start = System.currentTimeMillis();
        final String charset = CharsetIdentification.getCharset(metadata, content, -1);
        final String html = Charset.forName(charset).decode(ByteBuffer.wrap(content)).toString();
        final Document jsoupDoc = Parser.htmlParser().parseInput(html, urlString);

        final RobotsTags robotsTags = new RobotsTags();
        // Get the robots tags from the fetch metadata
        robotsTags.extractHTTPHeaders(metadata, protocolMDprefix);
        // Extracts the robots directives from the meta tags
        final Element robotsElement = jsoupDoc.selectFirst("meta[name~=(?i)robots][content]");
        if (robotsElement != null) {
            robotsTags.extractMetaTags(robotsElement.attr("content"));
        }
        robotsTags.normaliseToMetadata(metadata);

        final Set<String> outlinks = new HashSet<>();
        if (!robotsTags.isNoFollow()) {
            final Elements aElements = jsoupDoc.select("a[href]");

            for (final Element aElement : aElements) {
                boolean noFollow = "nofollow".equalsIgnoreCase(aElement.attr("rel"));
                if (noFollow) {
                    continue;
                }
                try {
                    String targetURL = URLUtil.resolveURL(url, aElement.attr("href")).toString();
                    outlinks.add(targetURL);
                } catch (MalformedURLException e) {
                }
            }
        }

        // Redirection
        String redirection = RefreshTag.extractRefreshURL(jsoupDoc);

        // Extract the canonical URL
        Element linkElement = jsoupDoc.selectFirst("link[rel=canonical]");
        if (linkElement != null) {
            String canonical = linkElement.attr("href");
            if (StringUtils.isNotBlank(canonical)) {
                try {
                    URL canonicalURL = URLUtil.resolveURL(url, canonical);
                    redirection = canonicalURL.toString();
                } catch (MalformedURLException e) {
                }
            }
        }

        if (StringUtils.isNotBlank(redirection) && !redirection.equals(urlString)) {
            LOG.info("Found redirection in {} to {}", urlString, redirection);
            Outlink outlink = filterOutlink(url, redirection, metadata);
            if (outlink != null) {
                collector.emit(
                        StatusStreamName,
                        tuple,
                        new Values(
                                outlink.getTargetURL(), outlink.getMetadata(), Status.DISCOVERED));
            }

            metadata.setValue("_redirTo", redirection);
            collector.emit(
                    StatusStreamName, tuple, new Values(urlString, metadata, Status.REDIRECTION));

            collector.ack(tuple);
            return;
        }
        

        ParseResult parse = new ParseResult(toOutlinks(url, metadata, outlinks));

        // Parse data of the parent URL
        ParseData parseData = parse.get(urlString);
        parseData.setMetadata(metadata);
        parseData.setText("");
        parseData.setContent(content);

        try {
            DocumentFragment fragment = null;
            if (parseFilters.needsDOM()) {
                fragment = DocumentFragmentBuilder.fromJsoup(jsoupDoc);
            }
            parseFilters.filter(urlString, content, fragment, parse);
        } catch (RuntimeException e) {
            final String errorMessage =
                    "Exception while running parse filters on " + urlString + ": " + e;
            handleException(urlString, e, metadata, tuple, "content filtering", errorMessage);
            return;
        }

        // Get list of outlinks
        final List<String> outlinksList = new ArrayList<>();
        if (emitOutlinks) {
            for (Outlink outlink : parse.getOutlinks()) {
                LOG.info("@@@OUTLINK METADATA URL: {} \n DATA:\n {}", outlink.getTargetURL(), outlink.getMetadata().toString());
                outlinksList.add(outlink.getTargetURL());
            }
            metadata.setValues("outlinks", outlinksList.toArray(new String[outlinksList.size()]));
        }


        for (final Entry<String, ParseData> doc : parse) {
            final ParseData parseDoc = doc.getValue();
            LOG.info("OWSPARSER parsedoc stuff: url:{} | ", doc.getKey());
            collector.emit(
                    tuple,
                    new Values(
                            doc.getKey(),
                            parseDoc.getContent(),
                            parseDoc.getMetadata(),
                            parseDoc.getText()));
        }

        collector.emit("segment", new Values(urlString, content, metadata));

        LOG.info("OWSParserBolt processing took time: {} ms", System.currentTimeMillis() - start);
        collector.ack(tuple);
    }

    private void handleException(
            String url,
            Throwable e,
            Metadata metadata,
            Tuple tuple,
            String errorSource,
            String errorMessage) {
        LOG.error(errorMessage);
        metadata.setValue(Constants.STATUS_ERROR_SOURCE, errorSource);
        metadata.setValue(Constants.STATUS_ERROR_MESSAGE, errorMessage);
        collector.emit(StatusStreamName, tuple, new Values(url, metadata, Status.ERROR));
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream("segment", new Fields("url", "content", "metadata"));
        declarer.declareStream(StatusStreamName, new Fields("url", "metadata", "status"));
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    private List<Outlink> toOutlinks(URL url, Metadata metadata, Set<String> links) {

        if (links.size() == 0) {
            return new LinkedList<>();
        }

        final Map<String, Outlink> outlinks = new HashMap<>();
        for (String link : links) {
            if (maxOutlinksPerPage >= 0 && outlinks.size() >= maxOutlinksPerPage) {
                LOG.info(
                        "Found {} unique links for {} trimming to {}",
                        outlinks.size(),
                        url,
                        maxOutlinksPerPage);
                break;
            }

            Outlink outlink = filterOutlink(url, link, metadata);
            if (outlink != null) {
                outlinks.put(outlink.getTargetURL(), outlink);
            }
        }

        return new LinkedList<>(outlinks.values());
    }

    private Outlink filterOutlink(URL sourceUrl, String targetUrl, Metadata metadata) {
        // build an absolute URL
        try {
            targetUrl = URLUtil.resolveURL(sourceUrl, targetUrl).toExternalForm();
        } catch (MalformedURLException e) {
            return null;
        }

        targetUrl = urlFilters.filter(sourceUrl, metadata, targetUrl);
        if (targetUrl == null) {
            return null;
        }

        Metadata newMetadata =
                metadataTransfer.getMetaForOutlink(targetUrl, sourceUrl.toExternalForm(), metadata);

        Outlink outlink = new Outlink(targetUrl);
        outlink.setMetadata(newMetadata);
        return outlink;
    }
}
