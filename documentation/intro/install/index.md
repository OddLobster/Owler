# Installing OWler

You can either build OWler from the source code and run it locally, or use Docker to build an image and run it in a container.
OWler comes with a playbook that installs and configures most of the dependencies to run the crawler.

## Ansible Playbook
Ansible can be installed from PyPi:

```{code} bash
# Optional: Create a fresh venv
python3 -m venv venv && source venv/bin/activate

pip install ansible
```

See [Ansible installation guide](https://docs.ansible.com/ansible/latest/installation_guide/index.html) for details on how to install Ansible for your platform.

## Using Docker

**Prerequisite:** You will need to install a recent version of Docker. See [Docker installation guide](https://docs.docker.com/engine/installation/) for details on how to install Docker for your platform.

1. Clone or download [OWler's](https://opencode.it4i.eu/openwebsearcheu/wp1/owseu-crawler/owler) repository to your local drive.

```{code} bash
# Clone repository
git clone https://opencode.it4i.eu/openwebsearcheu/wp1/owseu-crawler/owler
cd owler
```

2. Run one of the following command inside this directory. The `docker_playbook.yml` playbook will start OWler in a dockerized environment.

```{code} bash
ansible-playbook docker_playbook.yml --ask-become-pass
```

```{code} bash
mkdir crawldata
# Download and extract WARC path form CommonCrawls (e.g. https://commoncrawl.org/2022/10/sep-oct-2022-crawl-archive-now-available/)
wget -c https://data.commoncrawl.org/crawl-data/CC-MAIN-2022-40/warc.paths.gz -P crawldata && gunzip crawldata/warc.paths.gz
# export env variables
export PATH=/Users/changeme/opt/apache-maven-3.8.6/bin:$PATH
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.17.jdk/Contents/Home   
# Clone and install storm-crawler  
git clone https://github.com/DigitalPebble/storm-crawler.git
cp -fr files/storm-crawler/pom.xml storm-crawler
cd storm-crawler
mvn clean install
cd ..
mvn clean package
docker-compose --env-file .env -f docker-compose.yml up --build --renew-anon-volumes --remove-orphans

# On a separate shell
docker-compose run --rm storm-crawler
storm jar warc-crawler.jar org.apache.storm.flux.Flux topology/warc-elastic-crawler/owler.flux -e
```


## Using Docker / local mixed environment

In some cases, running nimbus, storm and zookeeper can be a burden for the system, especially when you only need a local machine.
The following setup removes cluster dependencies and runs kibana, elastic and storm, but only with storm local mode execution

```bash
docker-compose -f docker-compose-storm-local.yml up --build --renew-anon-volumes --remove-orphans
```

In a separate terminal:

- initialise index (may depend on topology):
  `topology/warc-elastic-crawler/ES_IndexInit.sh`
- run the topology:
    ```
    docker-compose -f docker-compose-storm-local.yml run --rm storm-crawler-local
    storm local warc-crawler.jar org.apache.storm.flux.Flux topology/warc-elastic-crawler/owler.flux -e --local-ttl 3600
    ```

In case your topology is configured with placeholders, run
```bash
storm local warc-crawler.jar org.apache.storm.flux.Flux topology/warc-elastic-crawler/owler.flux -e --filter topology/warc-elastic-crawler/.dev.properties
```



### Apache Storm

Install storm for [a development environment](https://storm.apache.org/releases/2.4.0/Setting-up-development-environment.html) such that the `storm` command is available: 

docker-compose run --rm storm-crawler
storm local warc-crawler.jar org.apache.storm.flux.Flux topology/warc-elastic-crawler/owler.flux -e

## Using local Environment 

**Prerequisite:** You will need to install recent version of Java (JDK 11) and Git.


### Install Elastic stack 
- Download [Elasticsearch](https://www.elastic.co/downloads/elasticsearch)
- Download [Kibana](https://www.elastic.co/downloads/kibana)
- Start Elasticsearch server
- Start Kibana server
- Check that Kibana is running by opening (http://localhost:5601)



### Start OWler
1. Clone or download [OWler's](https://opencode.it4i.eu/openwebsearcheu/wp1/owseu-crawler/owler) repository to your local drive.

```{code} bash
# Clone repository
git clone https://opencode.it4i.eu/openwebsearcheu/wp1/owseu-crawler/owler
cd owler
```

2. Run one of the following command inside this directory. The `local_playbook.yml` playbook will start OWler in a local environment.

```{code} bash
ansible-playbook local_playbook.yml --ask-become-pass
```