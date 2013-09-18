package de.olafklischat.volkit;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

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
                JFrame f = new JFrame("SliceView");
                f.setSize(900,700);
                SliceViewer sv = new SliceViewer();
                f.getContentPane().add(sv, BorderLayout.CENTER);
                f.setVisible(true);
            }
        });
    }

}
