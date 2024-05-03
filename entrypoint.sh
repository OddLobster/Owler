#!/bin/bash
ls -l /apache-storm-2.4.0/
ls -a
sleep 2
storm jar owler.jar org.apache.storm.flux.Flux topology/regular-pipeline/owler.flux --filter dev.properties
sleep 2
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frue-ra-frontier" PutURLs -f seeds.txt
sleep 10
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frue-ra-frontier" PutURLs -f seeds.txt

while true; do
  sleep 60
done

wait
