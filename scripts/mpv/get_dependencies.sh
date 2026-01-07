#!/usr/bin/env bash

set -exou pipefail

BUILD_PATH="./deps"

mkdir -p "$BUILD_PATH"
pushd "$BUILD_PATH" || exit

function clone(){
  repo=$1
  branch=$2
  dir=$3
  shift 3

  if [[ -d "$dir" ]]; then
    pushd "$dir" || exit
    git checkout --force "$branch"
    popd || exit
  else
    git clone "$repo" --depth 1 --single-branch -b "$branch" "$dir" "$@"
  fi
}

clone "https://github.com/videolan/dav1d" "1.5.3" dav1d

clone "https://github.com/FFmpeg/FFmpeg" "n8.0" ffmpeg

clone "https://gitlab.freedesktop.org/freetype/freetype.git" "VER-2-14-1" freetype2 --recurse-submodules

clone "https://github.com/libass/libass" "0.17.4" libass

clone "https://github.com/haasn/libplacebo" "v7.351.0" libplacebo --recurse-submodules

clone "https://github.com/mpv-player/mpv" "v0.41.0" mpv

if [[ ! -d mbedtls ]]; then
	mkdir mbedtls
	wget https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-3.6.4/mbedtls-3.6.4.tar.bz2 -O - | \
		tar -xj -C mbedtls --strip-components=1
fi

if [[ ! -d fribidi ]]; then
	mkdir fribidi
	wget https://github.com/fribidi/fribidi/releases/download/v1.0.16/fribidi-1.0.16.tar.xz -O - | \
		tar -xJ -C fribidi --strip-components=1
fi

if [[ ! -d harfbuzz ]]; then
	mkdir harfbuzz
	wget https://github.com/harfbuzz/harfbuzz/releases/download/12.1.0/harfbuzz-12.1.0.tar.xz -O - | \
		tar -xJ -C harfbuzz --strip-components=1
fi

version_unibreak="6.1"
if [[ ! -d unibreak ]]; then
	mkdir unibreak
	wget https://github.com/adah1972/libunibreak/releases/download/libunibreak_${version_unibreak//./_}/libunibreak-${version_unibreak}.tar.gz -O - | \
		tar -xz -C unibreak --strip-components=1
fi

if [[ ! -d lua ]]; then
	mkdir lua
	wget https://www.lua.org/ftp/lua-5.2.4.tar.gz -O - | \
		tar -xz -C lua --strip-components=1
fi

# python packages: jsonschema jinja2 meson

popd || exit
