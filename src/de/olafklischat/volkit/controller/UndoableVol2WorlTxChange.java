package de.olafklischat.volkit.controller;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import de.olafklischat.volkit.view.SliceViewer;

public class UndoableVol2WorlTxChange extends AbstractUndoableEdit {
	private SliceViewer[] svs;
	private float[] preTr, postTr;
	
	public UndoableVol2WorlTxChange(float[] preTx, float[] postTx, SliceViewer... svs) {
		super();
		this.svs = svs;
		this.preTr = preTx;
		this.postTr = postTx;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		for (SliceViewer sv : svs) {
			sv.setVolumeToWorldTransform(preTr);
		}
	}
	
	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		for (SliceViewer sv : svs) {
			sv.setVolumeToWorldTransform(postTr);
		}
	}
	
}
