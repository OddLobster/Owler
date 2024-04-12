.PHONY: runall run build

runall:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install
	(docker stop frue_ra_frontier || true) && docker run -d --rm --name frue_ra_frontier -p 43001:43001 -p 43002:43002 crawlercommons/url-frontier -s 43002
	sleep 1
	java -cp target/owler-0.1-SNAPSHOT.jar crawlercommons.urlfrontier.client.Client PutURLs -f seeds.txt
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

run:
	(docker stop frue_ra_frontier || true) && docker run -d --rm --name frue_ra_frontier -p 43001:43001 -p 43002:43002 crawlercommons/url-frontier -s 43002
	sleep 1
	java -cp target/owler-0.1-SNAPSHOT.jar crawlercommons.urlfrontier.client.Client PutURLs -f seeds.txt
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

build:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install