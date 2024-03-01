List of topologies:
- Regular pipeline:
  - Spouts:
    - `FrontierSpout` - reads the URLs from the URL Frontier
  - Bolts:
    - `FetcherBolt` - fetches web content to URLs
    - `JSoupParserBolt` - parses HTML content
    - `tika.RedirectionBolt` - redirects tuples to the Tika parser if necessary
    - `tika.ParserBolt` - parses non-HTML content
    - `DummyIndexer` - indexes the tuples to `/dev/null` (necessary for propagating tuples with status `FETCHED` to the status stream)
    - `log.RedirectionBolt` - directs the tuples to the `StatusLoggerBolt` if status is `DISCOVERED` else to the `StatusUpdaterBolt`
    - `log.StatusLoggerBolt` - writes the tuples in a `status.log` file
    - `urlfrontier.StatusUpdaterBolt` - updates the status of the URLs in the URL Frontier
    - `WARCBolt` - writes the tuples in a stream of WARC files to an S3 endpoint

- WARC pipeline:
  - Spouts:
    - `WARCSpout` - reads the WARC paths from a file
  - Bolts:
    - `JSoupParserBolt` - parses HTML content
    - `tika.RedirectionBolt` - redirects tuples to the Tika parser if necessary
    - `tika.ParserBolt` - parses non-HTML content
    - `DummyIndexer` - indexes the tuples to `/dev/null` (necessary for propagating tuples with status `FETCHED` to the status stream)
    - `log.RedirectionBolt` - directs the tuples to the `StatusLoggerBolt` if status is `DISCOVERED` else to the `StatusUpdaterBolt`
    - `log.StatusLoggerBolt` - writes the tuples in a `status.log` file
    - `urlfrontier.StatusUpdaterBolt` - updates the status of the URLs in the URL Frontier
    - `WARCBolt` - writes the tuples in a stream of WARC files to an S3 endpoint

- Sitemap pipeline:
  - Spouts:
    - `FrontierSpout` - reads the URLs from the URL Frontier
  - Bolts:
    - `FetcherBolt` - fetches web content to URLs
    - `SiteMapParserBolt` - parses the sitemap.xml files
    - `log.RedirectionBolt` - directs the tuples to the `StatusLoggerBolt` if status is `DISCOVERED` else to the `StatusUpdaterBolt`
    - `log.StatusLoggerBolt` - writes the tuples in a `status.log` file
    - `urlfrontier.StatusUpdaterBolt` - updates the status of the URLs in the URL Frontier

- The remaining topologies/pipelines are experimental