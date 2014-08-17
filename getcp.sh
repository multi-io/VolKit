twlsrc="$(cd ../twl/; pwd)"
twlcp="$twlsrc/dist/TWL.jar:`echo $twlsrc/elibs/*.jar | tr ' ' ':'`"

selfcp="bin:`echo lib/*jar lib/jogl/*jar | tr ' ' ':'`"

cp="$selfcp:$twlcp"
