.PHONY: fast runall run build

fast:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn install -DskipTests -T 24
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

run:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install -DskipTests -T 24
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes

build:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install

runall:
	mvn com.cosium.code:git-code-format-maven-plugin:4.2:format-code
	mvn clean install
	docker-compose -f docker-compose.yml up -d --build --renew-anon-volumes