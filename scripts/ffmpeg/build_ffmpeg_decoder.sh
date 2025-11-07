#!/usr/bin/env bash
set -ex

if [ -z "$1" ]; then
  echo "Error: Must provide NDK path"
  exit 1
fi
NDK_PATH="$1"

SCRIPT_PATH="$(realpath "${BASH_SOURCE[0]}")"
SCRIPT_DIR="$(dirname "${SCRIPT_PATH}")"
PROJECT_ROOT="$(realpath "${SCRIPT_DIR}/../../")"

# Config
ANDROID_ABI=21
ENABLED_DECODERS=(dca ac3 eac3 mlp truehd)
FFMPEG_BRANCH="release/6.0"

# Path configs
DIR_PATH="$(pwd)"
TARGET_PATH="$DIR_PATH/app/libs"
MEDIA_PATH="$DIR_PATH//ffmpeg_decoder/media"
FFMPEG_MODULE_PATH="$MEDIA_PATH/libraries/decoder_ffmpeg/src/main"
FFMPEG_PATH="$DIR_PATH/ffmpeg_decoder/ffmpeg"
HOST="$(uname -s | tr '[:upper:]' '[:lower:]')"
HOST_PLATFORM="$HOST-x86_64"

mkdir -p "$TARGET_PATH"
mkdir -p ffmpeg_decoder

echo "$PROJECT_ROOT/gradle/libs.versions.toml"

media_version="$(grep "androidx-media3 = " "$PROJECT_ROOT/gradle/libs.versions.toml" | awk -F'"' '{print $2}')"

pushd ffmpeg_decoder || exit

if [[ -d media ]]; then
  pushd media || exit
  git checkout --force "$media_version"
else
  git clone https://github.com/androidx/media.git --depth 1 --single-branch -b "$media_version" media
fi

if [[ -d ffmpeg ]]; then
  pushd ffmpeg || exit
  git checkout --force "$FFMPEG_BRANCH"
else
  git clone https://github.com/FFmpeg/FFmpeg --depth 1 --single-branch -b "$FFMPEG_BRANCH" ffmpeg
fi

ln -s "$FFMPEG_PATH" "${FFMPEG_MODULE_PATH}/jni/ffmpeg"

pushd "${FFMPEG_MODULE_PATH}/jni" || exit

./build_ffmpeg.sh "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"

pushd "$MEDIA_PATH" || exit
./gradlew :lib-decoder-ffmpeg:assemble
popd || exit

popd || exit
cp "$MEDIA_PATH/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar" "$TARGET_PATH/"
popd || exit
