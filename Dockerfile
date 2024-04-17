FROM storm:${STORM_VERSION:-2.4.0}

RUN apt-get update -qq && \
	apt-get install -yq --no-install-recommends \
		curl \
		jq \
		less \
		vim

#
# Storm crawler / WARC crawler
#
ENV OWLER_VERSION=0.1-SNAPSHOT
RUN mkdir /crawler && \
    chmod -R a+rx /crawler

# add the crawler uber-jar
COPY target/owler-$OWLER_VERSION.jar /crawler/owler.jar

# and topology configuration files
COPY topology/ /crawler/topology/

# and the dev.properties file
COPY dev.properties /crawler/dev.properties

# copy seed urls to populate urlfrontier
COPY seeds.txt /crawler/data/input

COPY entrypoint.sh /crawler/entrypoint.sh
RUN chmod +x /crawler/entrypoint.sh

RUN chown -R "storm:storm" /crawler/

USER storm
WORKDIR /crawler/

ENTRYPOINT ["./entrypoint.sh"]

