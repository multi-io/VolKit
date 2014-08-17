#!/bin/bash

set -e

cd "`dirname $0`"
. ./getcp.sh

javac -cp "$cp" -d bin `find src -name '*java'`
cp -f src/log4j.properties bin/
cp -rf src/shader bin/
cp -f src/de/olafklischat/volkit/*.{xml,png,fnt} bin/de/olafklischat/volkit/
