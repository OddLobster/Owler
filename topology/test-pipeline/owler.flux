name: "owler-regular-pipeline"

# - reads URLs from the URLFrontier using the FrontierSpout
# - it follows standard StormCrawler bolts for parsing and indexing the content
# - writes page captures into new WARC files using WARCHdfsBolt

config:
  status.redirection: true

includes:
  - resource: true
    file: "/crawler-default.yaml"
    override: false

  - resource: false
    file: "topology/regular-pipeline/owler.yml"
    override: true

  - resource: false
    file: "topology/regular-pipeline/opensearch-conf.yml"
    override: true
    

components:
  - id: "WARCFileNameFormat"
    className: "eu.ows.owler.warc.OWSWARCFileNameFormat"
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
  - id: "jsoup"
    className: "com.digitalpebble.stormcrawler.bolt.JSoupParserBolt"
    parallelism: 4
  - id: "shunt"
    className: "com.digitalpebble.stormcrawler.tika.RedirectionBolt"
    parallelism: 1
  - id: "tika"
    className: "com.digitalpebble.stormcrawler.tika.ParserBolt"
    parallelism: 1
  - id: "index"
    className: "com.digitalpebble.stormcrawler.indexing.DummyIndexer"
    parallelism: 1
  - id: "status_frontier"
    className: "com.digitalpebble.stormcrawler.urlfrontier.StatusUpdaterBolt"
    parallelism: 1
  - id: "logger_shunt"
    className: "eu.ows.owler.log.RedirectionBolt"
    parallelism: 1
  - id: "status_logger"
    className: "eu.ows.owler.log.StatusLoggerBolt"
    parallelism: 1
  - id: "warc"
    className: "com.digitalpebble.stormcrawler.warc.WARCHdfsBolt"
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
    to: "jsoup"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "jsoup"
    to: "warc"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "jsoup"
    to: "shunt"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "shunt"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "shunt"
    to: "tika"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "tika"

  - from: "tika"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "fetcher"
    to: "logger_shunt"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "status"

  - from: "jsoup"
    to: "logger_shunt"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "status"

  - from: "index"
    to: "logger_shunt"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "status"

  - from: "tika"
    to: "logger_shunt"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "status"

  - from: "logger_shunt"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "logger_shunt"
    to: "status_logger"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "logfile_status"
