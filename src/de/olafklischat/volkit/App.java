package de.olafklischat.volkit;

import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.volkit.view.SliceViewer;
import de.sofd.viskit.image3D.jogl.minigui.layout.BorderLayout;

public class App {

    /**
     * @param args
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    VolumeDataSet vds = VolumeDataSet.readFromDirectory(new File("/home/olaf/oliverdicom/INCISIX"));
                    JFrame f = new JFrame("SliceView");
                    f.setSize(900,700);
                    SliceViewer sv = new SliceViewer(vds);
                    f.getContentPane().add(sv, BorderLayout.CENTER);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

}
