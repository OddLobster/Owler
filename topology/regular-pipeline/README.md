# Regular crawling pipeline

## URL Frontier  
Clone, compile and run the frontier form the following repository:
(https://github.com/crawler-commons/url-frontier)[URL Frontier]  
(https://opencode.it4i.eu/openwebsearcheu/wp1/crawler-coordination/frontiers)[Opensearch implementation]

Before submitting the topology, inject seeds with
```
# Change to the root directory of the project
java -cp target/*.jar crawlercommons.urlfrontier.client.Client PutUrls -f seeds.txt
```

Specify the host address and the port of the URL Frontier in the `dev.properties` file as the parameters `urlfrontier.host` and `urlfrontier.port`.

## Injection of Seed URLs
Place the txt-file with the Seed URLs in the folder `./input` in the root directory of the project.

## Injection of WARC files
Place the file with the WARC paths in the folder `./input` in the root directory of the project.

## Important commands:
For changes in the StormCrawler Repository, navigate to the project directory and run `mvn clean install`  
`mvn clean package`  

Submitting the topology to a local Storm cluster (debugging):  
`storm local target/owler-0.1-SNAPSHOT.jar org.apache.storm.flux.Flux topology/regular-pipeline/owler.flux --filter dev.properties`  

Submitting the topology to a dockerized Storm cluster:  
`docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes`  
Optionally: `docker-compose -f docker-compose-os.yml up -d --renew-anon-volumes`  
Optionally: `./OS_IndexInit.sh`  
`docker-compose run --rm owler`  
Optionally: `vim dev.properties`  
`storm jar owler.jar org.apache.storm.flux.Flux topology/regular-pipeline/owler.flux --filter dev.properties`  
`docker-compose down  --remove-orphans`