.PHONY: runall run build

runall:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install
	(docker stop frontier || true) && docker run -d --rm --name frontier -p 7071:7071 -p 9100:9100 crawlercommons/url-frontier -s 9100
	sleep 1
	java -cp target/owler-0.1-SNAPSHOT.jar crawlercommons.urlfrontier.client.Client PutURLs -f input/seeds.txt
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

run:
	(docker stop frontier || true) && docker run -d --rm --name frontier -p 7071:7071 -p 9100:9100 crawlercommons/url-frontier -s 9100
	sleep 1
	java -cp target/owler-0.1-SNAPSHOT.jar crawlercommons.urlfrontier.client.Client PutURLs -f input/seeds.txt
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

build:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install