#!/bin/sh

set -e

cd "`dirname $0`"
. ./getcp.sh

# jogl
# java -Xmx8000m -Xms300m -Djava.library.path=lib/jogl/linux64 -Dno.jogl.verbose=true -Dno.jogl.debug=1 -cp "$cp" de/olafklischat/volkit/App

# lwjgl
java -Xmx800m -Xms200m -Djava.library.path="$twlsrc/elibs/native/linux" -Dno.jogl.verbose=true -Dno.jogl.debug=1 -DnoVolKit.debug=1 -cp "$cp" de/olafklischat/volkit/App
