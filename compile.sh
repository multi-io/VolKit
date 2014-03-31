#!/bin/bash

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

javac -cp "$cp" -d bin `find src -name '*java'`
cp -f src/log4j.properties bin/
cp -rf src/shader bin/
cp -f src/de/olafklischat/volkit/*.{xml,png,fnt} bin/de/olafklischat/volkit/
