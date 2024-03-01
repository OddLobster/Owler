# Tips and Tricks

## Workarounds for raised issues

### Elastic Search Docker Issues

- elasticsearch might not startup with a 137 exit code. Try to reduce the memory size in `docker-compose.yml`, e.g. `- "ES_JAVA_OPTS=-Xms2g -Xmx2g"`
