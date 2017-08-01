package mandelbrot;

import javax.swing.*;

/**
 * Created by Galvin on 2/25/2015.
 */
public class MandelBrot {
    public static void main (String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainWindow();
            }
        });
    }
}
