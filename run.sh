#!/bin/sh

set -e

cd "`dirname $0`"

commonbase="$HOME/sofd-common"
twlsrc="$HOME/src/twl"

utilcp="$commonbase/de.sofd.util/build/classes"
swingcp="$commonbase/de.sofd.swing/build/classes"
draw2dcp="$commonbase/de.sofd.draw2d/build/classes"
viskitcp="$commonbase/de.sofd.viskit/build/classes"
sofdtwlcp="$commonbase/de.olafklischat.twl/build/classes"
sofdtwlawtcp="$commonbase/de.olafklischat.twlawt/bin"
twlcp="$twlsrc/bin:`echo $twlsrc/elibs/*.jar | tr ' ' ':'`"

selfcp="bin:`echo lib/*jar lib/jogl/*jar | tr ' ' ':'`"

cp="$selfcp:$twlcp:$swingcp:$draw2dcp:$sofdtwlcp:$sofdtwlawtcp:$viskitcp:$utilcp"

# jogl
# java -Xmx8000m -Xms300m -Djava.library.path=lib/jogl/linux64 -Dno.jogl.verbose=true -Dno.jogl.debug=1 -cp "$cp" de/olafklischat/volkit/App

# lwjgl
java -Xmx10000m -Xms300m -Djava.library.path="$twlsrc/elibs/native/linux" -Dno.jogl.verbose=true -Dno.jogl.debug=1 -DnoVolKit.debug=1 -cp "$cp" de/olafklischat/volkit/App
