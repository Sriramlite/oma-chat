// Q16_ShapePrintArea.java
// 16. Write a Java Program to create an abstract class named Shape that contains two integers and an empty method named printArea(). 
// Provide three classes named Rectangle, Triangle and Circle such that each one of the classes extends the class Shape. 
// Each one of the classes contains only the method printArea( ) that prints the area of the given shape.

abstract class Shape {
    int a, b;
    public Shape(int a, int b) {
        this.a = a;
        this.b = b;
    }
    abstract void printArea();
}

class RectangleShapeClass extends Shape {
    public RectangleShapeClass(int length, int breadth) {
        super(length, breadth);
    }
    void printArea() {
        System.out.println("Area of Rectangle (" + a + " x " + b + ") = " + (a * b));
    }
}

class TriangleShapeClass extends Shape {
    public TriangleShapeClass(int base, int height) {
        super(base, height);
    }
    void printArea() {
        System.out.println("Area of Triangle (0.5 x " + a + " x " + b + ") = " + (0.5 * a * b));
    }
}

class CircleShapeClass extends Shape {
    public CircleShapeClass(int radius) {
        super(radius, 0);
    }
    void printArea() {
        System.out.println("Area of Circle (pi x " + a + "^2) = " + (Math.PI * a * a));
    }
}

public class Q16_ShapePrintArea {
    public static void main(String[] args) {
        Shape rect = new RectangleShapeClass(10, 20);
        Shape tri = new TriangleShapeClass(10, 15);
        Shape circle = new CircleShapeClass(7);

        rect.printArea();
        tri.printArea();
        circle.printArea();
    }
}
