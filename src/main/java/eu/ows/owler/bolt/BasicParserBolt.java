package eu.ows.owler.bolt;

import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.bolt.StatusEmitterBolt;
import com.digitalpebble.stormcrawler.parse.DocumentFragmentBuilder;
import com.digitalpebble.stormcrawler.parse.Outlink;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseFilters;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.RefreshTag;
import com.digitalpebble.stormcrawler.util.URLUtil;
import eu.ows.owler.util.RobotsTags;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
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

public class BasicParserBolt extends StatusEmitterBolt {

    /** Metadata key name for tracking the anchors */
    public static final String ANCHORS_KEY_NAME = "anchors";

    /**
     * Whether to interpret the noFollow directive strictly (remove links) or not (remove anchor and
     * do not track original URL). True by default.
     */
    public static final String ROBOTS_NO_FOLLOW_STRICT = "robots.noFollow.strict";

    private static final Logger LOG = LoggerFactory.getLogger(BasicParserBolt.class);

    private ParseFilter parseFilters = null;
    private boolean trackAnchors = true;
    private boolean emitOutlinks = true;
    private int maxOutlinksPerPage = -1;
    private boolean robots_noFollow_strict = true;
    private String protocolMDprefix;
    private boolean robotsHeaderSkip;
    private boolean robotsMetaSkip;
    private boolean ignoreMetaRedirections;

    @Override
    public void prepare(
            Map<String, Object> conf, TopologyContext context, OutputCollector collector) {
        super.prepare(conf, context, collector);

        parseFilters = ParseFilters.fromConf(conf);
        emitOutlinks = ConfUtils.getBoolean(conf, "parser.emitOutlinks", true);
        trackAnchors = ConfUtils.getBoolean(conf, "track.anchors", true);
        robots_noFollow_strict = ConfUtils.getBoolean(conf, ROBOTS_NO_FOLLOW_STRICT, true);
        maxOutlinksPerPage = ConfUtils.getInt(conf, "parser.emitOutlinks.max.per.page", -1);
        protocolMDprefix = ConfUtils.getString(conf, ProtocolResponse.PROTOCOL_MD_PREFIX_PARAM, "");
        robotsHeaderSkip = ConfUtils.getBoolean(conf, "http.robots.headers.skip", false);
        robotsMetaSkip = ConfUtils.getBoolean(conf, "http.robots.meta.skip", false);
        ignoreMetaRedirections =
                ConfUtils.getBoolean(conf, "parser.ignore.meta.redirections", false);
    }

    @Override
    public void execute(Tuple tuple) {

        final byte[] content = tuple.getBinaryByField("content");
        final String url = tuple.getStringByField("url");
        final Metadata metadata = (Metadata) tuple.getValueByField("metadata");

        LOG.info("Parsing started for {}", url);

        final long start = System.currentTimeMillis();

        final String charset = CharsetIdentification.getCharset(metadata, content, -1);

        LOG.debug(
                "Charset identified as {} in {} msec",
                charset,
                (System.currentTimeMillis() - start));

        // Get the robots tags from the fetch metadata
        final RobotsTags robotsTags = new RobotsTags();
        if (!robotsHeaderSkip) {
            robotsTags.extractHTTPHeaders(metadata, protocolMDprefix);
        }

        final Map<String, List<String>> slinks;
        final Document jsoupDoc;
        try {
            final String html =
                    Charset.forName(charset).decode(ByteBuffer.wrap(content)).toString();
            jsoupDoc = Parser.htmlParser().parseInput(html, url);

            if (!robotsMetaSkip) {
                // extracts the robots directives from the meta tags
                final Element robotElement =
                        jsoupDoc.selectFirst("meta[name~=(?i)robots][content]");
                if (robotElement != null) {
                    robotsTags.extractMetaTags(robotElement.attr("content"));
                }
            }

            // store a normalised representation in metadata
            // so that the indexer is aware of it
            robotsTags.normaliseToMetadata(metadata);

            // do not extract the links if no follow has been set
            // and we are in strict mode
            if (robotsTags.isNoFollow() && robots_noFollow_strict) {
                slinks = new HashMap<>();
            } else {
                final Elements links = jsoupDoc.select("a[href]");
                slinks = new HashMap<>(links.size());
                final URL baseURL = new URL(url);
                for (final Element link : links) {
                    // nofollow
                    boolean noFollow = "nofollow".equalsIgnoreCase(link.attr("rel"));
                    // remove altogether
                    if (noFollow && robots_noFollow_strict) {
                        continue;
                    }

                    // link not specifically marked as no follow
                    // but whole page is
                    if (!noFollow && robotsTags.isNoFollow()) {
                        noFollow = true;
                    }

                    String targetURL = null;

                    try {
                        // abs:href tells jsoup to return fully qualified domains
                        // for relative urls
                        // but it is very slow as it builds intermediate URL objects
                        // and normalises the URL of the document every time
                        targetURL = URLUtil.resolveURL(baseURL, link.attr("href")).toExternalForm();
                    } catch (MalformedURLException e) {
                        LOG.debug(
                                "Cannot resolve URL with baseURL : {} and href : {}",
                                baseURL,
                                link.attr("href"),
                                e);
                    }

                    if (StringUtils.isBlank(targetURL)) {
                        continue;
                    }

                    final List<String> anchors =
                            slinks.computeIfAbsent(targetURL, a -> new LinkedList<>());

                    // any existing anchors for the same target?
                    final String anchor = link.text();
                    // track the anchors only if no follow is false
                    if (!noFollow && StringUtils.isNotBlank(anchor)) {
                        anchors.add(anchor);
                    }
                }
            }
        } catch (MalformedURLException e) {
            final String errorMessage = "Exception while parsing " + url + ": " + e;
            handleException(url, e, metadata, tuple, "content parsing", errorMessage);
            return;
        }

        // store identified charset in md
        metadata.setValue("parse.Content-Encoding", charset);

        // track that is has been successfully handled
        metadata.setValue("parsed.by", this.getClass().getName());

        long duration = System.currentTimeMillis() - start;

        LOG.info("Parsed {} in {} msec", url, duration);

        if (!ignoreMetaRedirections) {
            try {
                final String redirection = RefreshTag.extractRefreshURL(jsoupDoc);
                if (StringUtils.isNotBlank(redirection)) {
                    // stores the URL it redirects to used for debugging mainly
                    // do not resolve the target URL
                    LOG.info("Found redir in {} to {}", url, redirection);
                    metadata.setValue("_redirTo", redirection);

                    // https://github.com/DigitalPebble/storm-crawler/issues/954
                    if (allowRedirs() && StringUtils.isNotBlank(redirection)) {
                        emitOutlink(tuple, new URL(url), redirection, metadata);
                    }

                    // Mark URL as redirected
                    collector.emit(
                            com.digitalpebble.stormcrawler.Constants.StatusStreamName,
                            tuple,
                            new Values(url, metadata, Status.REDIRECTION));
                    collector.ack(tuple);
                    return;
                }
            } catch (MalformedURLException e) {
                LOG.error("MalformedURLException on {}", url);
            }
        }

        List<Outlink> outlinks = toOutlinks(url, metadata, slinks);

        ParseResult parse = new ParseResult(outlinks);

        // parse data of the parent URL
        ParseData parseData = parse.get(url);
        parseData.setMetadata(metadata);
        parseData.setText("");
        parseData.setContent(content);

        // apply the parse filters if any
        try {
            DocumentFragment fragment = null;
            // lazy building of fragment
            if (parseFilters.needsDOM()) {
                fragment = DocumentFragmentBuilder.fromJsoup(jsoupDoc);
            }
            parseFilters.filter(url, content, fragment, parse);
        } catch (RuntimeException e) {
            final String errorMessage =
                    "Exception while running parse filters on " + url + ": " + e;
            handleException(url, e, metadata, tuple, "content filtering", errorMessage);
            return;
        }

        if (emitOutlinks) {
            for (Outlink outlink : parse.getOutlinks()) {
                collector.emit(
                        StatusStreamName,
                        tuple,
                        new Values(
                                outlink.getTargetURL(), outlink.getMetadata(), Status.DISCOVERED));
            }
        }

        // emit each document/subdocument in the ParseResult object
        // there should be at least one ParseData item for the "parent" URL

        for (final Map.Entry<String, ParseData> doc : parse) {
            final ParseData parseDoc = doc.getValue();
            collector.emit(
                    tuple,
                    new Values(
                            doc.getKey(),
                            parseDoc.getContent(),
                            parseDoc.getMetadata(),
                            parseDoc.getText()));
        }

        LOG.info("Total parsing for {} in {} msec", url, System.currentTimeMillis() - start);

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
        // send to status stream in case another component wants to update
        // its status
        metadata.setValue(Constants.STATUS_ERROR_SOURCE, errorSource);
        metadata.setValue(Constants.STATUS_ERROR_MESSAGE, errorMessage);
        collector.emit(StatusStreamName, tuple, new Values(url, metadata, Status.ERROR));
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }

    protected List<Outlink> toOutlinks(
            String url, Metadata metadata, Map<String, List<String>> slinks) {

        if (slinks.size() == 0) {
            return new LinkedList<>();
        }

        URL sourceUrl;
        try {
            sourceUrl = new URL(url);
        } catch (MalformedURLException e) {
            // we would have known by now as previous components check whether
            // the URL is valid
            LOG.error("MalformedURLException on {}", url);
            return new LinkedList<>();
        }

        final Map<String, Outlink> outlinks = new HashMap<>();
        for (final Map.Entry<String, List<String>> linkEntry : slinks.entrySet()) {

            if (maxOutlinksPerPage >= 0 && outlinks.size() >= maxOutlinksPerPage) {
                LOG.info(
                        "Found {} unique links for {} trimming to {}",
                        slinks.size(),
                        url,
                        maxOutlinksPerPage);
                break;
            }

            final String targetURL = linkEntry.getKey();
            Outlink outlink = filterOutlink(sourceUrl, targetURL, metadata);
            if (outlink == null) {
                continue;
            }

            // the same link could already be there post-normalisation
            final Outlink oldOutlink = outlinks.get(outlink.getTargetURL());
            if (oldOutlink != null) {
                outlink = oldOutlink;
            }

            final List<String> anchors = linkEntry.getValue();
            if (trackAnchors && anchors.size() > 0) {
                outlink.getMetadata().addValues(ANCHORS_KEY_NAME, anchors);
                // sets the first anchor
                outlink.setAnchor(anchors.get(0));
            }

            if (oldOutlink == null) {
                outlinks.put(outlink.getTargetURL(), outlink);
            }
        }

        return new LinkedList<>(outlinks.values());
    }
}
