echo "Set java to 25"
jenv local 25
export JAVA_HOME="$(jenv javahome)"
echo "build Admin"

# The administrator's manual (UC-029) is typeset before the package step so the PDF is on the
# classpath when Quarkus builds the image, and is stamped with the version and build date
# this image carries (UC-029 BR-003, NFR-017). SKIP_MANUAL=1 builds without it; the
# application then simply does not offer the entry (UC-029 A1).
VERSION="$(sed -n '1,20p' pom.xml | grep -m1 -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g')"
BUILD_DATE="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
./docs/manual/build-manual.sh "${VERSION}" "${BUILD_DATE}"

./mvnw clean package -Dquarkus.profile=docker -Dquarkus.container-image.build=true
# ./mvnw clean package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.profile=docker