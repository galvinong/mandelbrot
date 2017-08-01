package mandelbrot;
/**
 * Created by Galvin on 2/25/2015.
 */

public class Complex {
    private double re;
    private double im;

    public Complex(double re, double im){
        this.re = re;
        this.im = im;
    }

    public double getReal() {return re;}

    public double getIm() {return im;}

    public void add(Complex d){
        //Adds complex number d to this complex number
        this.re += d.getReal();
        this.im += d.getIm();
    }

    public void square() {
        //Square complex numbers by using (a+b)^2 = a^2 + 2ab + b^2
        double tu = re * re - im * im; //a^2 + b^2, minus cause of imaginary num i^2 = -1
        im = 2 * re * im;  //2ab, only number with i
        re = tu;
    }

    public double modSquared(){
        //|z|^2, ^2 removes the square root of modulus, and leaves us with just real^2, and imag^2
        return re * re + im * im;
    }

}
