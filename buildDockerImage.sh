echo "Set java to 25"
jenv local 25
echo "build Admin"

./mvnw clean package -Dquarkus.profile=docker -Dquarkus.container-image.build=true
# ./mvnw clean package -Dmaven.test.skip=true -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.profile=docker