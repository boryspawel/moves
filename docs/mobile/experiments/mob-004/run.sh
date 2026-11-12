#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
generator_jar="$repo_root/web/node_modules/@openapitools/openapi-generator-cli/versions/7.26.0.jar"
spec="$repo_root/web/openapi/openapi.json"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/moves-mob004-repro.XXXXXX")"

test -n "${JAVA_HOME:-}" || { echo "JAVA_HOME must point to JDK 17" >&2; exit 2; }
test -x "$JAVA_HOME/bin/java" || { echo "JAVA_HOME must contain bin/java" >&2; exit 2; }
test -f "$generator_jar" || { echo "missing pinned OpenAPI Generator: $generator_jar" >&2; exit 2; }

sha256sum "$spec"

"$JAVA_HOME/bin/java" -jar "$generator_jar" generate \
  -i "$spec" -g kotlin -o "$work_dir/client" --library jvm-retrofit2 \
  --additional-properties=serializationLibrary=kotlinx_serialization,dateLibrary=java8,generateOneOfAnyOfWrappers=true,packageName=com.motionecosystem.moves.api,apiPackage=com.motionecosystem.moves.api.api,modelPackage=com.motionecosystem.moves.api.model,sourceFolder=src/main/kotlin,enumPropertyNaming=UPPERCASE

# Pin only this disposable compile gate. Gradle 8.14.3-bin was executed successfully;
# the generated 8.14.5 wrapper download was corrupt and therefore is not treated as tested.
sed -i "s/gradleVersion = '8.14.5'/gradleVersion = '8.14.3'/; s/-all.zip/-bin.zip/; s/ext.kotlin_version = '2.4.0'/ext.kotlin_version = '2.4.21'/; s/kotlinx-serialization-json:1.11.0/kotlinx-serialization-json:1.13.0/; s/logging-interceptor:5.4.0/logging-interceptor:5.5.1/" "$work_dir/client/build.gradle"
sed -i 's/gradle-8.14.5-all.zip/gradle-8.14.3-bin.zip/' "$work_dir/client/gradle/wrapper/gradle-wrapper.properties"

gradle_cmd="${GRADLE_CMD:-$work_dir/client/gradlew}"
JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" GRADLE_USER_HOME="${GRADLE_USER_HOME:-$work_dir/gradle-home}" \
  "$gradle_cmd" -p "$work_dir/client" compileKotlin --no-daemon
