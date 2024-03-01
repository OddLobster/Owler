Open Web Crawler
============================
## OWler’s documentation

```{admonition} Warning
:class: caution
This documentation is currently under development.
```


OWler is an open-source focused web crawler that navigates the [Common Crawl](https://commoncrawl.org/) to downloads WARC files and store extracted content into an index. 
It collects web pages that satisfy some specific criteria, e.g., URLs that belong to a given domain or that contain a user-specified topic. 
In it's early version, OWler aims to use URL classifiers to distinguish between relevant and irrelevant content in a given domain.

OWler supports many features, such as:
- Regular crawling of a fixed list of web sites
- Continuous re-crawling of sitemaps to discover new pages
- Discovery and crawling of new relevant web sites through automatic link prioritization
- Indexing of crawled pages using [Elasticsearch](https://www.elastic.co/products/elasticsearch)
- Web interface for navigating crawled pages in real-time and for for crawler monitoring
- Configuration of different types of URL classifiers (machine-learning, regex, etc.) 



```{note}
**OWler 1.0-SNAPSHOT** is using Tika 2.6.0 and is tested against:

Java 11.0.17.\
Elasticsearch 7.17.2.\
Apache Storm 2.4.0.\
Apache ZooKeeper 3.8.0.
```


OWler is part of the [OpenWebSearch.EU](http://ows.eu/) project:  


```{image} images/ows-logo.png
:alt: OpenWebSearch.EU Logo
:height: 65px
:align: left
```  




