echo "Set java to 21"
jenv local 21.0.2
echo "build Admin"

./mvnw clean package -Dquarkus.profile=docker -Pproduction -Dquarkus.container-image.build=true
# ./mvnw clean package -Dmaven.test.skip=true -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.profile=docker