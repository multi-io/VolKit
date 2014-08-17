package de.olafklischat.volkit.controller;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import de.olafklischat.volkit.view.SliceViewer;
import de.olafklischat.volkit.view.VolumeViewer;

public class UndoableVol2WorldTransformChange extends AbstractUndoableEdit {
    private VolumeViewer vv;
	private SliceViewer[] svs;
	private float[] preTr, postTr;
	
	public UndoableVol2WorldTransformChange(float[] preTr, float[] postTr, VolumeViewer vv, SliceViewer... svs) {
		super();
		this.vv = vv;
		this.svs = svs;
		this.preTr = preTr;
		this.postTr = postTr;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
        vv.setVolumeToWorldTransform(preTr);
		for (SliceViewer sv : svs) {
			sv.setVolumeToWorldTransform(preTr);
		}
	}
	
	@Override
	public void redo() throws CannotRedoException {
		super.redo();
        vv.setVolumeToWorldTransform(postTr);
		for (SliceViewer sv : svs) {
			sv.setVolumeToWorldTransform(postTr);
		}
	}
	
}
