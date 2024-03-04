name: "${eu.ows.owler.crawler.name}"

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


spouts:
  - id: "filespout"
    className: "com.digitalpebble.stormcrawler.spout.FileSpout"
    parallelism: 1
    constructorArgs:
      - ${eu.ows.owler.spouts.input}
      - ${eu.ows.owler.filespout.seeds}
      - false

bolts:
  - id: "parse"
    className: "com.digitalpebble.stormcrawler.bolt.FetcherBolt"
    parallelism: 1

  - id: "index"
    className: "com.digitalpebble.stormcrawler.indexing.DummyIndexer"
    parallelism: 1
  

streams:
  - from: "filespout"
    to: "parse"
    grouping:
      type: SHUFFLE

  - from: "parse"
    to: "index"
    grouping: 
      type: SHUFFLE