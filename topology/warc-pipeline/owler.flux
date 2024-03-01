name: "${eu.ows.owler.crawler.name}"

# - reads WARC files using the WARCSpout
# - the WARC paths file has to be provided in the folder ./input
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
  - id: "warcspout"
    className: "com.digitalpebble.stormcrawler.warc.WARCSpout"
    parallelism: 1
    constructorArgs:
      - ${eu.ows.owler.spouts.input}
      - ${eu.ows.owler.warcspout.paths}

bolts:
  - id: "jsoup"
    className: "com.digitalpebble.stormcrawler.bolt.JSoupParserBolt"
    parallelism: 4
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
  - from: "warcspout"
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

  - from: "warcspout"
    to: "status_frontier"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "jsoup"
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
