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
  - id: "parser"
    className: "eu.ows.owler.bolt.BasicParserBolt"
    parallelism: 8
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
    to: "parser"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "parser"
    to: "warc"
    grouping:
      type: LOCAL_OR_SHUFFLE
