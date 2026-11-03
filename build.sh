#!/usr/bin/env sh
set -eu
gradle clean build
echo 'Built: build/libs/FlexyCrates-1.0.0.jar'
