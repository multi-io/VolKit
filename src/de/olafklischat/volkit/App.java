package de.olafklischat.volkit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import de.olafklischat.volkit.controller.TripleSliceViewerController;
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
                    JFrame f = new JFrame("SliceView");
                    
                    f.getContentPane().setBackground(Color.GRAY);
                    f.getContentPane().setLayout(new BorderLayout());
                    
                    JPanel mainPane = new JPanel();
                    mainPane.setBackground(Color.GRAY);
                    f.getContentPane().add(mainPane, BorderLayout.CENTER);
                    
                    mainPane.setLayout(new GridLayout(2, 2, 5, 5));
                    
                    SliceViewer sv1 = new SliceViewer();
                    sv1.setBackground(Color.DARK_GRAY);
                    mainPane.add(sv1);

                    SliceViewer sv2 = new SliceViewer();
                    sv2.setBackground(Color.DARK_GRAY);
                    mainPane.add(sv2);

                    SliceViewer sv3 = new SliceViewer();
                    sv3.setBackground(Color.DARK_GRAY);
                    mainPane.add(sv3);
                    
                    final TripleSliceViewerController slicesController = new TripleSliceViewerController(sv1, sv2, sv3);

                    mainPane.add(new JLabel(":-)", JLabel.CENTER));
                    
                    JToolBar toolbar = new JToolBar();
                    toolbar.setFloatable(false);
                    toolbar.setBackground(Color.GRAY);
                    f.getContentPane().add(toolbar, BorderLayout.NORTH);
                    toolbar.add(new AbstractAction("Tx RST") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            slicesController.resetVolumeToWorldTransform();
                        }
                    });
                    toolbar.add(new AbstractAction("Nav RST") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            slicesController.resetZNavigations();
                        }
                    });
                    
                    f.setSize(1200,900);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                    
                    slicesController.loadVolumeDataSet("/home/olaf/oliverdicom/INCISIX");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

}
