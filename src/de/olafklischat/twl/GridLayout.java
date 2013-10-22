package de.olafklischat.twl;

import de.matthiasmann.twl.Widget;


public class GridLayout extends Widget {

    int colCount, rowCount;
    
    public GridLayout(int colCount, int rowCount) {
        this.colCount = colCount;
        this.rowCount = rowCount;
    }
    
    @Override
    protected void layout() {
        int w = getInnerWidth();
        int h = getInnerHeight();
        int boxW = w / colCount;
        int boxH = h / rowCount;

        final int n = Math.min(getNumChildren(), colCount * rowCount);
        for(int idx=0 ; idx < n; idx++) {
            Widget child = getChild(idx);
            child.setSize(boxW, boxH);
            int boxRow = idx / colCount;
            int boxCol = idx % colCount;
            child.setPosition(getInnerX() + boxCol * boxW, getInnerY() + boxRow * boxH);
        }
    }
    
}
