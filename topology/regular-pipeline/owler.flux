name: "${eu.ows.owler.crawler.name}"

# - reads URLs from the URLFrontier using the FrontierSpout
# - it follows standard StormCrawler bolts for parsing and indexing the content
# - writes page captures into new WARC files using WARCHdfsBolt

includes:
  - resource: true
    file: "/crawler-default.yaml"
    override: false

  - resource: false
    file: "topology/regular-pipeline/owler.yml"
    override: true

#   - resource: false
#     file: "topology/regular-pipeline/opensearch-conf.yml"
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
         - "OWLer https://openwebsearch.eu/owler/"
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
      className: "eu.ows.owler.spout.FrontierSpout"
      parallelism: 1

bolts:
  - id: "fetcher"
    className: "eu.ows.owler.bolt.FetcherBolt"
    parallelism: 1
  - id: "parser"
    className: "eu.ows.owler.bolt.OWSParserBolt"
    parallelism: 2
  - id: "hits"
    className: "eu.ows.owler.bolt.HITSBolt"
    parallelism: 1
  - id: "segmenter"
    className: "eu.ows.owler.bolt.PageSegmentBolt"
    parallelism: 1
  - id: "embedder"
    className: "eu.ows.owler.bolt.EmbeddingBolt"
    parallelism: 4
  - id: "classifier"
    className: "eu.ows.owler.bolt.ClassificationBolt"
    parallelism: 1
  - id: "lof"
    className: "eu.ows.owler.bolt.LOFBolt"
    parallelism: 2
  - id: "eval"
    className: "eu.ows.owler.bolt.EvaluationBolt"
    parallelism: 1
  - id: "shunt"
    className: "com.digitalpebble.stormcrawler.tika.RedirectionBolt"
    parallelism: 1
  - id: "tika"
    className: "com.digitalpebble.stormcrawler.tika.ParserBolt"
    parallelism: 1
  - id: "status_frontier"
    className: "com.digitalpebble.stormcrawler.urlfrontier.StatusUpdaterBolt"
    parallelism: 1
  - id: "index"
    className: "com.digitalpebble.stormcrawler.indexing.DummyIndexer"
    parallelism: 1
  - id: "warc_preprocessor"
    className: "eu.ows.owler.warc.WARCBlockPrepareBolt"
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
    to: "parser"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "parser"
    to: "segmenter"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "segment"

  - from: "segmenter"
    to: "embedder"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "embedder"
    to: "lof"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "lof"
    to: "classifier"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "lof"
    to: "hits"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "parser"
    to: "shunt"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "shunt"
    to: "tika"
    grouping:
      type: LOCAL_OR_SHUFFLE
      streamId: "tika"

  - from: "tika"
    to: "segmenter"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "embedder"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "classifier"
    to: "warc_preprocessor"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "warc_preprocessor"
    to: "warc"
    grouping: 
      type: LOCAL_OR_SHUFFLE

  - from: "fetcher"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "embedder"
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

  - from: "tika"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "classifier"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"
