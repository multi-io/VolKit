#!/bin/sh

set -e

cd "`dirname $0`"

utilcp=/home/olaf/sofd-common/de.sofd.util/build/classes
swingcp=/home/olaf/sofd-common/de.sofd.swing/build/classes
draw2dcp=/home/olaf/sofd-common/de.sofd.draw2d/build/classes
viskitcp=/home/olaf/sofd-common/de.sofd.viskit/build/classes
selfcp="bin:`echo lib/*jar lib/jogl/*jar | tr ' ' ':'`"

cp="$selfcp:$swingcp:$draw2dcp:$viskitcp:$utilcp"

java -Xmx8000m -Xms300m -Djava.library.path=lib/jogl/linux64 -Dno.jogl.verbose=true -Dno.jogl.debug=1 -cp "$cp" de/olafklischat/volkit/App
