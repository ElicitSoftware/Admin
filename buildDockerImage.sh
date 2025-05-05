mvn package -Pproduction
docker build -f src/main/docker/Dockerfile.jvm -t edu.umich.med.spi/admin:docker .
