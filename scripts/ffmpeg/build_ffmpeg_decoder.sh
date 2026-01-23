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
ENABLED_DECODERS=(dca ac3 eac3 mlp truehd flac alac pcm_mulaw pcm_alaw mp3)
FFMPEG_BRANCH="release/6.0"
DAV1D_BRANCH="1.5.3"

# Path configs
DIR_PATH="$(pwd)"
TARGET_PATH="$PROJECT_ROOT/app/libs"
MEDIA_PATH="$DIR_PATH/ffmpeg_decoder/media"
FFMPEG_MODULE_PATH="$MEDIA_PATH/libraries/decoder_ffmpeg/src/main"
FFMPEG_PATH="$DIR_PATH/ffmpeg_decoder/ffmpeg"
AV1_MODULE_PATH="$MEDIA_PATH/libraries/decoder_av1/src/main"
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

[[ ! -d "${FFMPEG_MODULE_PATH}/jni/ffmpeg" ]] && ln -s "$FFMPEG_PATH" "${FFMPEG_MODULE_PATH}/jni/ffmpeg"

pushd "${FFMPEG_MODULE_PATH}/jni" || exit

./build_ffmpeg.sh "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"

# av1 module

pushd "$AV1_MODULE_PATH/jni" || exit

if [[ ! -d cpu_features ]]; then
  git clone https://github.com/google/cpu_features --depth 1 --single-branch cpu_features
fi

pushd "$AV1_MODULE_PATH/jni" || exit

if [[ -d dav1d ]]; then
  pushd dav1d || exit
  git checkout --force "$DAV1D_BRANCH"
else
  git clone https://code.videolan.org/videolan/dav1d --depth 1 --single-branch -b "$DAV1D_BRANCH" dav1d
fi

pushd "$AV1_MODULE_PATH/jni" || exit

/usr/bin/env bash ./build_dav1d.sh "${AV1_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}"


pushd "$MEDIA_PATH" || exit
./gradlew :lib-decoder-ffmpeg:assemble :lib-decoder-av1:assemble
popd || exit

popd || exit
cp "$MEDIA_PATH/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar" "$TARGET_PATH/"
cp "$MEDIA_PATH/libraries/decoder_av1/buildout/outputs/aar/lib-decoder-av1-release.aar" "$TARGET_PATH/"
popd || exit
