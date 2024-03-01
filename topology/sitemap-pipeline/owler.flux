name: "${eu.ows.owler.crawler.name}"

# - reads URLs from the URLFrontier using the FrontierSpout
# - it parses the sitemap XML content

includes:
  - resource: true
    file: "/crawler-default.yaml"
    override: false

  - resource: false
    file: "topology/regular-pipeline/owler.yml"
    override: true

  - resource: false
    file: "topology/regular-pipeline/owler.yml"
    override: true

#   - resource: false
#     file: "topology/sitemap-pipeline/opensearch-conf.yml"
#     override: true

components:
  - id: "WARCFileNameFormat"
    className: "eu.ows.owler.warc.OWSWARCFileNameFormat"
    constructorArgs:
      - ${eu.ows.owler.crawler.name}
    configMethods:
      - name: "withPath"
        args:
          - "${eu.ows.owler.warcbolt.fspath}"
  - id: "WARCFileRotationPolicy"
    className: "com.digitalpebble.stormcrawler.warc.FileTimeSizeRotationPolicy"
    constructorArgs:
      - ${eu.ows.owler.warcbolt.max.fsize.mb}
      - MB
    configMethods:
      - name: "setTimeRotationInterval"
        args:
          - 10
          - MINUTES
  - id: "WARCInfo"
    className: "java.util.LinkedHashMap"
    configMethods:
      - name: "put"
        args:
         - "software"
         - "OWler https://openwebsearch.eu/owler/"
      - name: "put"
        args:
         - "format"
         - "WARC File Format 1.1"
      - name: "put"
        args:
         - "conformsTo"
         - "https://iipc.github.io/warc-specifications/specifications/warc-format/warc-1.1/"

spouts:
  - id: "frontierspout"
    className: "com.digitalpebble.stormcrawler.urlfrontier.Spout"
    parallelism: 10

bolts:
  - id: "fetcher"
    className: "com.digitalpebble.stormcrawler.bolt.FetcherBolt"
    parallelism: 1
  - id: "sitemap"
    className: "com.digitalpebble.stormcrawler.bolt.SiteMapParserBolt"
    parallelism: 2
  - id: "status_frontier"
    className: "com.digitalpebble.stormcrawler.urlfrontier.StatusUpdaterBolt"
    parallelism: 1
  - id: "index"
    className: "com.digitalpebble.stormcrawler.indexing.DummyIndexer"
    parallelism: 1
  - id: "warc"
    className: "eu.ows.owler.warc.OWSWARCHdfsBolt"
    parallelism: 1
    configMethods:
      - name: "withFileNameFormat"
        args:
          - ref: "WARCFileNameFormat"
      - name: "withRotationPolicy"
        args:
          - ref: "WARCFileRotationPolicy"
      - name: "withRequestRecords"
      - name: "withHeader"
        args:
          - ref: "WARCInfo"
      - name: "withConfigKey"
        args:
          - "awscreds"
      - name: "withFsUrl"
        args:
          - "${eu.ows.owler.warcbolt.fsurl}"

streams:
  - from: "frontierspout"
    to: "fetcher"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "fetcher"
    to: "sitemap"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "fetcher"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "sitemap"
    to: "warc"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "sitemap"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "sitemap"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "index"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"
