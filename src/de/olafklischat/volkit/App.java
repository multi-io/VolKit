package de.olafklischat.volkit;

import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
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
                    f.getContentPane().setLayout(new GridLayout(2, 2));
                    
                    SliceViewer sv1 = new SliceViewer(vds);
                    sv1.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XY);
                    f.getContentPane().add(sv1);

                    f.getContentPane().add(new JButton(":-)"));
                    
                    SliceViewer sv2 = new SliceViewer(vds);
                    sv2.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XZ);
                    f.getContentPane().add(sv2);

                    SliceViewer sv3 = new SliceViewer(vds);
                    sv3.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_YZ);
                    f.getContentPane().add(sv3);

                    f.setSize(1200,900);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

}
