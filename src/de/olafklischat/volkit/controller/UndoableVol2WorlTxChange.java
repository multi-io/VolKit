package de.olafklischat.volkit.controller;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import de.olafklischat.volkit.view.SliceViewer;

public class UndoableVol2WorlTxChange extends AbstractUndoableEdit {
	private SliceViewer[] svs;
	private float[] preTx, postTx;
	
	public UndoableVol2WorlTxChange(SliceViewer[] svs, float[] preTx,
			float[] postTx) {
		super();
		this.svs = svs;
		this.preTx = preTx;
		this.postTx = postTx;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		for (SliceViewer sv : svs) {
			sv.setVolumeToWorldTransform(preTx);
		}
	}
	
	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		for (SliceViewer sv : svs) {
			sv.setVolumeToWorldTransform(postTx);
		}
	}
	
}
