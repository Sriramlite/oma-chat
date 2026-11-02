// Q6_InterfaceShapeArea.java
// 6. Write a java program to create interface for calculating area of different shape.
interface ShapeAreaCalculator {
    double calculateArea();
}

class Rectangle implements ShapeAreaCalculator {
    double length, breadth;
    public Rectangle(double l, double b) { length = l; breadth = b; }
    public double calculateArea() { return length * breadth; }
}

class Circle implements ShapeAreaCalculator {
    double radius;
    public Circle(double r) { radius = r; }
    public double calculateArea() { return Math.PI * radius * radius; }
}

class Triangle implements ShapeAreaCalculator {
    double base, height;
    public Triangle(double b, double h) { base = b; height = h; }
    public double calculateArea() { return 0.5 * base * height; }
}

public class Q6_InterfaceShapeArea {
    public static void main(String[] args) {
        ShapeAreaCalculator rect = new Rectangle(12, 4);
        ShapeAreaCalculator circ = new Circle(5);
        ShapeAreaCalculator tri = new Triangle(10, 6);

        System.out.println("Rectangle Area: " + rect.calculateArea());
        System.out.println("Circle Area: " + circ.calculateArea());
        System.out.println("Triangle Area: " + tri.calculateArea());
    }
}
