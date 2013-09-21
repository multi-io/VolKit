package de.olafklischat.volkit;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.volkit.view.SliceViewer;

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
                    f.getContentPane().setBackground(Color.GRAY);
                    f.getContentPane().setLayout(new GridLayout(2, 2, 5, 5));
                    
                    SliceViewer sv1 = new SliceViewer(vds);
                    sv1.setBackground(Color.DARK_GRAY);
                    sv1.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XY);
                    f.getContentPane().add(sv1);

                    f.getContentPane().add(new JLabel(":-)", JLabel.CENTER));
                    
                    SliceViewer sv2 = new SliceViewer(vds);
                    sv2.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XZ);
                    sv2.setBackground(Color.DARK_GRAY);
                    f.getContentPane().add(sv2);

                    SliceViewer sv3 = new SliceViewer(vds);
                    sv3.setBackground(Color.DARK_GRAY);
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
