package de.olafklischat.volkit;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import de.olafklischat.volkit.controller.TripleSliceViewerController;
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
                    long t0 = System.currentTimeMillis();
                    VolumeDataSet vds = VolumeDataSet.readFromDirectory("/home/olaf/oliverdicom/INCISIX");
                    long t1 = System.currentTimeMillis();
                    System.out.println("time for reading: " + (t1-t0) + " ms.");
                    JFrame f = new JFrame("SliceView");
                    f.getContentPane().setBackground(Color.GRAY);
                    f.getContentPane().setLayout(new GridLayout(2, 2, 5, 5));
                    
                    SliceViewer sv1 = new SliceViewer(vds);
                    sv1.setBackground(Color.DARK_GRAY);
                    f.getContentPane().add(sv1);

                    SliceViewer sv2 = new SliceViewer(vds);
                    sv2.setBackground(Color.DARK_GRAY);
                    f.getContentPane().add(sv2);

                    SliceViewer sv3 = new SliceViewer(vds);
                    sv3.setBackground(Color.DARK_GRAY);
                    f.getContentPane().add(sv3);
                    
                    new TripleSliceViewerController(sv1, sv2, sv3);

                    f.getContentPane().add(new JLabel(":-)", JLabel.CENTER));
                    
                    f.setSize(1200,900);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

}
