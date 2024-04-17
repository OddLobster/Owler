#!/bin/bash

storm jar owler.jar org.apache.storm.flux.Flux topology/regular-pipeline/owler.flux --filter dev.properties
sleep 2
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frue-ra-frontier" PutURLs -f /data/input/seeds.txt
sleep 2
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frue-ra-frontier" PutURLs -f /data/input/seeds.txt
sleep 120
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frue-ra-frontier" PutURLs -f /data/input/seeds.txt
sleep 240
java -cp owler.jar crawlercommons.urlfrontier.client.Client --host="frue-ra-frontier" PutURLs -f /data/input/seeds.txt

wait
