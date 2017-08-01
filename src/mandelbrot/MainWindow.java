package mandelbrot;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Galvin on 2/25/2015.
 */
public class MainWindow extends JFrame{
    RenderPanel renderPanel;
    MandelTextFieldPanel mandelTextFieldPanel;

    public MainWindow() {
        //Determine screen size, and resolution for program
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        int screenWidth;
        int screenHeight = gd.getDisplayMode().getHeight();

        if(screenHeight < 768){
            screenWidth = 800;
            screenHeight= 600;
        }else if(screenHeight < 800){
            screenWidth = 1024;
            screenHeight = 768;
        }else if(screenHeight > 800){
            screenWidth = 1280;
            screenHeight = 960;
        }else{
            screenWidth = 800;
            screenHeight= 600;
        }

        //Instantiate panels, change sizes of each panel here
        renderPanel = new RenderPanel(screenWidth,screenHeight);
        mandelTextFieldPanel = new MandelTextFieldPanel(renderPanel);

        //Add Panels here
        setLayout(new BorderLayout());
        add(renderPanel, BorderLayout.CENTER);
        add(mandelTextFieldPanel, BorderLayout.SOUTH);

        //Window options here
        setTitle("MandelBrot Set & Julia Set");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(screenWidth, screenHeight);
        setResizable(false);
        setVisible(true);
    }
}

//Renders the mandelbrot & julia panel
class RenderPanel extends JPanel{
    public BufferedImage bufferedImage, bufferedImageTwo;
    final int width, height, area;
    int numIterations = 100;

    //Threading
    int[] indexes;
    ArrayList<Thread> threads = new ArrayList<Thread>();
    volatile  long startTime, endTime;
    AtomicInteger a = new AtomicInteger();

    //Bounds set, default is real axis -2 to 2, imaginary -1.6 to 6
    double xMin = -2.0, xMax = 2.0;
    double yMax = 1.6, yMin = -1.6;

    //Code for rectangular selection
    Point mouseStart, mouseEnd;
    Rectangle rectangle;

    public int getNumIterations() {
        return numIterations;
    }

    public RenderPanel(int inputWidth, int inputHeight){
        //Takes the width and height declared in MainWindow constructor
        this.width = inputWidth;
        this.height = inputHeight;
        this.area = inputWidth * inputHeight;
        setPreferredSize(new Dimension(width,height));

        bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        //Does it as random, so it shows multi threading is utilised
        indexes = new int[area];
        for (int i = 0; i < area; i++)
            indexes[i] = i;

        for (int i = 0; i < area; i++) {
            int j = (int) (Math.random() * area);

            int t = indexes[i];
            indexes[i] = indexes[j];
            indexes[j] = t;
        }

        //Default render
        renderMandel(numIterations);
        renderJulia(numIterations,0,0);

        //Mouse adapter for rectangular selection
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseStart = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                //Removes the selection when released
                repaint();
                calculateZoom();
                renderMandel(numIterations);
                //Removes the object once mouse is released
                rectangle = null;
            }
        });

        //Mouse adapter for dragging
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
                mouseEnd = e.getPoint();

                //Only if there is a difference in mouse start and mouse end, draw a rectangle selection
                if (!mouseEnd.equals(mouseStart)) {
                    //Locks to rectangle ratio, so as not to skew the image
                    double ratio = (double) width / height;

                    int rectWidth = (int) Math.abs(mouseEnd.getX() - mouseStart.getX());
                    int rectHeight = (int) Math.abs(mouseEnd.getY() - mouseStart.getY());

                    //If rectWidth and height is 0, return
                    if (rectWidth == 0 || rectHeight == 0)
                        return;

                    if ((rectWidth / rectHeight) > ratio) {
                        rectHeight = (int) (rectWidth / ratio);
                    } else {
                        rectWidth = (int) (rectHeight * ratio);
                    }

                    int rectPointX = (int) mouseStart.getX() - (mouseStart.getX() > mouseEnd.getX() ? rectWidth : 0);
                    int rectPointY = (int) mouseStart.getY() - (mouseStart.getY() > mouseEnd.getY() ? rectHeight : 0);

                    //Creates a rectangle object with the mouse values
                    rectangle = new Rectangle(rectPointX, rectPointY, rectWidth, rectHeight);

                    repaint();
                }
            }
        });
    }

    public synchronized float getProgress() {
        return (float) a.get() / area;
    }

    //
    public void calculateZoom(){
        double prevMinX = calculateX(rectangle.x);
        double prevMinY = calculateY(rectangle.y + rectangle.height);

        double prevMaxX = calculateX(rectangle.x + rectangle.width);
        double prevMaxY = calculateY(rectangle.y);

        xMin = prevMinX;
        yMin = prevMinY;
        yMax = prevMaxY;
        xMax = prevMaxX;
//        System.out.println(xMin + "\t" + yMin);
//        System.out.println(xMax + "\t" + yMax);
    }


    public double calculateX(int x) {
        //xMax and xMin is set as as the boundary values
        return xMin + x * ((xMax - xMin) / width);
    }

    public double calculateY(int y) {
        //yMax and yMin is set as the boundary values
        return yMax - y * ((yMax - yMin) / height);
    }

    //Multi thread code
    class Rendering extends Thread {
        final int nIter;

        public Rendering(int numIterations) {
            this.nIter = numIterations;
        }

        @Override
        public void run() {
            //Extract the data buffered, and store it into int array, access it and draw it ourselves which is faster
            final int[] data = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();

            //Code to generate colors based on number of iterations
            int[] colors = new int[nIter];
            for (int j = 0; j < nIter; j++) {
                colors[j] = Color.HSBtoRGB(j / 256f, 1, j / (j + 8f));
            }
            int idx;
            while((idx = a.getAndIncrement()) < area && !currentThread().isInterrupted()) {
                int i = indexes[idx];

                //convert 1d into 2d
                final int x = i % width;
                final int y = i / width;

                //convert 2d into complex
                final double cx = calculateX(x);
                final double cy = calculateY(y);

                //init complex values
                Complex Zc = new Complex(cx, cy);
                Complex Zn = new Complex(0, 0);

                int iterations = 0;

                //If have not escape the mandelbrot set ie, < 4, and iterations lesser than number defined, pass it again
                while (Zn.modSquared() < 4 && iterations < nIter && !currentThread().isInterrupted()) {
                    Zn.square();
                    Zn.add(Zc);
                    iterations++;
                }


                if(iterations < nIter) {
                    data[i] = colors[iterations];
                }else{
                    data[i] = 0;
                }

                if (x == 0){
                    repaint();
                }
            }

            //Compare end and start time to see how long it takes
            endTime = System.currentTimeMillis();
            System.out.println((System.currentTimeMillis() - startTime) / 1000d + " s. to render");
        }
    }

    public void renderMandel(int numIterations) {
        a.set(0);
        //Start timer here to calculate time it takes to render the mandelbrot
        startTime = System.currentTimeMillis();

        //Interrupt all current threads first before doing anything
        for (Thread t : threads)
            t.interrupt();

        //Wait for all the threads for completion
        for (Thread t : threads)
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        //Get the number of avaliable threads to render the mandelbrot
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            Thread t = new Rendering(numIterations);
            t.start();
            threads.add(t);
        }

//
//        bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//        final int[] data = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
//
//        this.numIterations = numIterations;
//
//        //Code to generate heat mapping colors
//        colors = new int[numIterations];
//        for (int j = 0; j < numIterations; j++) {
//            colors[j] = Color.HSBtoRGB(j / 256f, 1, j / (j + 8f));
//        }
//
//        for (int i = 0; i < area; i++) {
//
//            //convert 1d into 2d
//            final int x = i % width;
//            final int y = i / width;
//
//            //convert 2d into complex
//            final double cx = calculateX(x);
//            final double cy = calculateY(y);
//
//            //init complex values
//            Complex Zc = new Complex(cx, cy);
//            Complex Zn = new Complex(0, 0);
//
//            int iterations = 0;
//
//            //If have not escape the mandelbrot set ie, < 4, and iterations lesser than number defined, pass it again
//            while (Zn.modSquared() < 4 && iterations < numIterations) {
//                Zn.square();
//                Zn.add(Zc);
//                iterations++;
//            }
//
//            data[i] = iterations < numIterations ? colors[iterations] : 0;
//
//            if(x == width)
//                repaint();
//        }
    }

    //Quadratic julia sets are generated by the quadratic mapping  z(n+1) = (zn)^2 + c for fixed c
    public void renderJulia(int numIterations, double complexX, double complexY){
        //Draw julia set at the top right hand, 25%
        int width = ((this.width * 25) / 100);
        int height = ((this.height * 25) / 100);
        int area = width * height;

        int[] colors = new int[numIterations];
        for (int j = 0; j < numIterations; j++) {
            colors[j] = Color.HSBtoRGB(j / 256f, 1, j / (j + 8f));
        }

        bufferedImageTwo = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        //C is fixed, at the coordinate, user selected point
        Complex Zc = new Complex(complexX, complexY);

        for (int i = 0; i < area; i++) {
            //convert 1d into 2d
            final int x = i % width;
            final int y = i / width;

            //convert 2d into complex
            final double cx = -2.0 + x * (4d / width);
            final double cy = 1.6 - y * (3.2d / height);

            //Zn is now with cx and cy
            Complex Zn = new Complex(cx, cy);
            int iterations = 0;

            while (Zn.modSquared() < 4 && iterations < numIterations) {
                Zn.square();
                Zn.add(Zc);
                iterations++;
            }

            if (iterations < numIterations) {
                bufferedImageTwo.setRGB(x, y, colors[iterations]);
            } else {
                bufferedImageTwo.setRGB(x, y, Color.BLACK.getRGB());
            }

        }
        repaint();
    }

    public void paint(Graphics g) {
        //Draw the mandelbrot set
        g.drawImage(bufferedImage, 0, 0, null);

        //Draw the image specifying to start at 75% of the width for julia set
        g.drawImage(bufferedImageTwo, ((width * 75) / 100), 0, null);

        //Logic for drawing rectangular selection
        if (rectangle != null) {
            g.setColor(Color.BLUE);
            ((Graphics2D) g).draw(rectangle);
            g.setColor(new Color(0, 0, 255, 100));
            ((Graphics2D) g).fill(rectangle);
        }

    }
}

//Bottom bar code here, TextFields and button
class MandelTextFieldPanel extends JPanel implements ActionListener {

    public JTextField realTextField, imagTextField, iterateTextField;
    private JLabel realLabel, imagLabel, iterateLabel, progressLabel;
    private Timer timer = new Timer(100, this);

    private JProgressBar progressBar;
    private JButton saveImageBtn;
    private DefaultComboBoxModel imagesComboBoxModel;
    private JComboBox<RenderValues> imageJComboBox;

    private RenderPanel renderPanel;

    int mandelWidth, mandelHeight;

    int numIterations;

    public MandelTextFieldPanel(final RenderPanel renderPanel) {
        renderPanel.getComponents();
        //Real, imaginary and iterations
        realLabel = new JLabel("Real Axis");
        imagLabel = new JLabel("Imaginary Axis");
        iterateLabel = new JLabel("Iterations");
        realTextField = new JTextField("0", 5);
        imagTextField = new JTextField("0", 5);
        iterateTextField = new JTextField("100", 5);

        this.renderPanel = renderPanel;

        // progressbar
        progressBar = new JProgressBar();
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);

        timer.start();

        //Favourite and save images
        saveImageBtn = new JButton("Save JuliaSet Image");
        imagesComboBoxModel = new DefaultComboBoxModel<RenderValues>();
        imageJComboBox = new JComboBox<RenderValues>(imagesComboBoxModel);
        mandelWidth = renderPanel.width;
        mandelHeight = renderPanel.height;

        //Connect the number of iterations throughout the panels
        numIterations = renderPanel.getNumIterations();

        //REAL TEXT FIELD, action: retrieves x and y to render julia set
        add(realLabel);
        add(realTextField);
        realTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                renderPanel.renderJulia(numIterations, Double.parseDouble(realTextField.getText()), Double.parseDouble(imagTextField.getText()));
            }
        });

        //IMAGINARY TEXT FIELD, action: retrieves x and y to render julia set
        add(imagLabel);
        add(imagTextField);
        imagTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                renderPanel.renderJulia(numIterations, Double.parseDouble(realTextField.getText()), Double.parseDouble(imagTextField.getText()));
            }
        });

        //ITERATIONS TEXT FIELD, action: updates the number of iterations
        add(iterateLabel);
        add(iterateTextField);
        iterateTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                System.out.println("Number of iterations " + Integer.parseInt(iterateTextField.getText()));
                renderPanel.numIterations = Integer.parseInt(iterateTextField.getText());
                renderPanel.renderMandel(numIterations);
                repaint();
            }
        });

        add(progressBar);

        add(Box.createHorizontalStrut(5));
        add(new JSeparator(SwingConstants.VERTICAL));
        add(Box.createHorizontalStrut(5));


        //COMBO BOX for retrieving favourite julia set picture
        add(imageJComboBox);
        imageJComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    RenderValues rValues = (RenderValues) e.getItem();
                    //let julia render panel renders with the given values
                    renderPanel.renderJulia(rValues.getNumIterations(), rValues.getComplexX(), rValues.getComplexY());
                }
            }
        });

        //SAVE IMAGE BUTTON, action: saves julia set image, output dialog and shows absolute path
        add(saveImageBtn);
        saveImageBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File outputfile = new File("juliaset.png");
                try {
                    ImageIO.write(renderPanel.bufferedImageTwo, "png", outputfile);
                    JOptionPane.showMessageDialog(renderPanel, "File saved as " + outputfile.getAbsolutePath());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });

        //ADD TO FAVOURITE, changed from button to click because of dynamic user select point
        renderPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RenderValues renderValues = new RenderValues(renderPanel.numIterations, Double.parseDouble(realTextField.getText()), Double.parseDouble(imagTextField.getText()));
                imagesComboBoxModel.addElement(renderValues);
                System.out.println("Image Added");
            }
        });

        //USER SELECTED POINT, calculates complex real and imaginary point based on the x and y coordinates of mouse
        renderPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                super.mouseMoved(e);
                //Sets the decimal format here
                DecimalFormat df = new DecimalFormat("#.##");
                //User selected point
                final double complexX = renderPanel.xMin + e.getX() * ((renderPanel.xMax - renderPanel.xMin) / renderPanel.width);
                final double complexY = renderPanel.yMax - e.getY() * ((renderPanel.yMax - renderPanel.yMin) / renderPanel.height);

                realTextField.setText(String.valueOf(df.format(complexX)));
                imagTextField.setText(String.valueOf(df.format(complexY)));

                //Calls julia render image after retrieving user selected point
                renderPanel.renderJulia(renderPanel.numIterations, complexX, complexY);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == timer) {
            progressBar.setValue((int) (renderPanel.getProgress() * 100));
        }
    }
}

//Class for saving values
class RenderValues{
    int numIterations;
    double complexX, complexY;

    public RenderValues(int numIterations, double complexX, double complexY) {
        this.numIterations = numIterations;
        this.complexX = complexX;
        this.complexY = complexY;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public double getComplexX() {
        return complexX;
    }

    public double getComplexY() {
        return complexY;
    }
}




