package eu.ows.owler.warc;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.hdfs.bolt.format.FileNameFormat;
import org.apache.storm.task.TopologyContext;

public class OWSWARCFileNameFormat implements FileNameFormat {

    private String path = "/";
    private final String crawlerName;

    private final String extension = ".warc.gz";

    /**
     * Overrides the default path.
     *
     * @param path
     * @return
     */
    public FileNameFormat withPath(String path) {
        this.path = path;
        return this;
    }

    public OWSWARCFileNameFormat(final String crawlerName) {
        super();
        this.crawlerName = crawlerName;
    }

    public OWSWARCFileNameFormat() {
        this.crawlerName = "defaultCrawlerName";
    }
    
    @Override
    public void prepare(Map<String, Object> conf, TopologyContext topologyContext) {}

    @Override
    public String getName(long rotation, long timeStamp) {
        final SimpleDateFormat time = new SimpleDateFormat("HHmmss");
        time.setTimeZone(TimeZone.getTimeZone("GMT"));

        final SimpleDateFormat year = new SimpleDateFormat("yyyy");
        year.setTimeZone(TimeZone.getTimeZone("GMT"));

        final SimpleDateFormat month = new SimpleDateFormat("M");
        month.setTimeZone(TimeZone.getTimeZone("GMT"));

        final SimpleDateFormat day = new SimpleDateFormat("d");
        day.setTimeZone(TimeZone.getTimeZone("GMT"));

        String path =
                "year="
                        + year.format(new Date(timeStamp))
                        + "/month="
                        + month.format(new Date(timeStamp))
                        + "/day="
                        + day.format(new Date(timeStamp))
                        + "/";

        if (StringUtils.isNotBlank(crawlerName)) {
            path += "crawler=" + crawlerName + "/";
        }

        return path
                + time.format(new Date(timeStamp))
                + "-"
                + String.format("%05d", rotation)
                + extension;
    }

    @Override
    public String getPath() {
        return path;
    }
}
