echo "Set java to 25"
jenv local 25
export JAVA_HOME="$(jenv javahome)"
echo "build Admin"

# The administrator's manual (UC-029) ships inside the image (BR-004), stamped with the version
# and build date of the build that typeset it (BR-003, NFR-017). Typesetting means a TeX Live
# container and the slowest minute of a development build, so this script reuses the PDF a
# previous run left at src/main/resources/manual/ and typesets only when there is none; a
# reused manual then carries that earlier build's stamp, which an unpublished image can live
# with. BUILD_MANUAL=1 typesets a fresh one; SKIP_MANUAL=1 builds without one, and the
# application then simply does not offer the entry (UC-029 A1). Published images never take
# the shortcut: the release workflow (.github/workflows/maven.yml) typesets the manual for
# every tagged build, pre-release or final.
MANUAL_PDF="src/main/resources/manual/elicit-admin-manual.pdf"
if [ "${BUILD_MANUAL:-0}" != 1 ] && [ "${SKIP_MANUAL:-0}" != 1 ] && [ -s "${MANUAL_PDF}" ]; then
    echo "[manual] reusing ${MANUAL_PDF} from $(date -r "${MANUAL_PDF}" '+%Y-%m-%d %H:%M'); BUILD_MANUAL=1 typesets a fresh one"
else
    VERSION="$(sed -n '1,20p' pom.xml | grep -m1 -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g')"
    BUILD_DATE="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    ./docs/manual/build-manual.sh "${VERSION}" "${BUILD_DATE}"
fi

./mvnw clean package -Dquarkus.profile=docker -Dquarkus.container-image.build=true
# ./mvnw clean package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.profile=docker