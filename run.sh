#!/bin/sh

set -e

cd "`dirname $0`"

commonbase="/home/olaf/sofd-common"
twlsrc=/home/olaf/src/twl

utilcp="$commonbase/de.sofd.util/build/classes"
swingcp="$commonbase/de.sofd.swing/build/classes"
draw2dcp="$commonbase/de.sofd.draw2d/build/classes"
viskitcp="$commonbase/de.sofd.viskit/build/classes"
twlcp="$twlsrc/bin:`echo $twlsrc/elibs/*.jar | tr ' ' ':'`"

selfcp="bin:`echo lib/*jar lib/jogl/*jar | tr ' ' ':'`"

cp="$selfcp:$twlcp:$swingcp:$draw2dcp:$viskitcp:$utilcp"

# jogl
# java -Xmx8000m -Xms300m -Djava.library.path=lib/jogl/linux64 -Dno.jogl.verbose=true -Dno.jogl.debug=1 -cp "$cp" de/olafklischat/volkit/App

# lwjgl
java -Xmx10000m -Xms300m -Djava.library.path="$twlsrc/elibs/native/linux" -Dno.jogl.verbose=true -Dno.jogl.debug=1 -DnoVolKit.debug=1 -cp "$cp" de/olafklischat/volkit/App
