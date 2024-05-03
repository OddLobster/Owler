.PHONY: fast run build exec format

fast:
	mvn install -DskipTests -T 24 -f pom-fast.xml
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

fastclean:
	mvn clean install -DskipTests -T 24 -f pom-fast.xml
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

run:
	mvn clean install -DskipTests -T 24
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

build:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install

exec:
	MY_UID=$$(id -u) MY_GID=$$(id -g) docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

format:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code