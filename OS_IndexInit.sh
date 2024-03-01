#!/bin/bash

set -e

OSHOST="http://localhost:9200"
OSCREDENTIALS=""
# OSHOST="https://opensearch.pads.fim.uni-passau.de:443"
# OSCREDENTIALS="-u admin:admin"
STATUS_INDEX="owler-status"
METRICS_INDEX="owler-metrics"
CONTENT_INDEX="owler-content"

# deletes and recreates a status index with a bespoke schema

echo "Deleting status index" $STATUS_INDEX

curl $OSCREDENTIALS -s -XDELETE $OSHOST/$STATUS_INDEX/

echo
echo
echo "Creating status index" $STATUS_INDEX "with mapping"

curl $OSCREDENTIALS -s -XPUT $OSHOST/$STATUS_INDEX -H 'Content-Type: application/json'  --upload-file src/main/resources/mappings/status.mapping

echo
echo

# deletes and recreates a status index with a bespoke schema

echo "Deleting metrics indices" $METRICS_INDEX

curl $OSCREDENTIALS -s -XDELETE $OSHOST/$METRICS_INDEX*/

echo
echo
echo "Creating metrics template" $METRICS_INDEX "with mapping"

curl $OSCREDENTIALS -s -XPOST $OSHOST/_template/metrics -H 'Content-Type: application/json'  --upload-file src/main/resources/mappings/metrics.mapping

echo
echo

# deletes and recreates a doc index with a bespoke schema

echo "Deleting content index" $CONTENT_INDEX

curl $OSCREDENTIALS -s -XDELETE "$OSHOST/$CONTENT_INDEX/"

echo
echo
echo "Creating content index" $CONTENT_INDEX "with mapping"

curl $OSCREDENTIALS -s -XPUT $OSHOST/$CONTENT_INDEX -H 'Content-Type: application/json' --upload-file src/main/resources/mappings/content.mapping

echo
