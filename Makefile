.PHONY: fast run build exec format

fast:
	mvn install -DskipTests -T 24 -f pom-fast.xml
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

fastclean:
	mvn clean install -DskipTests -T 24 -f pom-fast.xml
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

run:
	mvn clean install -DskipTests -Pskip-owasp-check -T 24
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

runnocache:
	mvn install -DskipTests -Pskip-owasp-check -Dmaven.build.cache.enabled=false
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

build:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install

exec:
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker compose -f docker-compose.yml up -d --build --renew-anon-volumes 

format:
	mvn com.cosium.code:git-code-format-maven-plugin:format-code

run_ows:
	mvn clean install -DskipTests -T 24
	docker-compose -f docker-compose-owsdefault.yml up -d --build --renew-anon-volumes
	sleep 60
	docker-compose -f docker-compose-frontier-service.yml up -d --build --renew-anon-volumes

exec_ows:
	docker-compose -f docker-compose-owsdefault.yml up -d --build --renew-anon-volumes
	sleep 60
	docker-compose -f docker-compose-frontier-service.yml up -d --build --renew-anon-volumes
	sleep 60
	docker-compose -f docker-compose-owler.yml up -d --build --renew-anon-volumes

cleanup:
	docker-compose -f docker-compose-frontier-service.yml down
	docker-compose -f docker-compose-owsdefault.yml down
