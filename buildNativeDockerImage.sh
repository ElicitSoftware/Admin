echo "Set java to 25"
jenv local 25
echo "build native Admin"

./mvnw clean package -Dmaven.test.skip=true -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.profile=docker -X
