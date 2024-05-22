#!/bin/bash
ls -l /apache-storm-2.4.0/
ls -a
sleep 2
storm jar owler.jar org.apache.storm.flux.Flux topology/regular-pipeline/owler.flux --filter dev.properties
sleep 2
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frontier1" PutURLs -f seeds.txt
sleep 3
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frontier1" GetURLs


#TODO REMOVE!!!!
while true; do
  sleep 60
done

wait
