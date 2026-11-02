// Q5_AbstractShapeArea.java
// 5. Write a java program to create abstract class for calculating area of different shape.
abstract class AbstractShape {
    String shapeName;
    public AbstractShape(String name) { this.shapeName = name; }
    public abstract double calculateArea();
    public void displayArea() {
        System.out.println("Area of " + shapeName + " = " + calculateArea());
    }
}

class RectangleShape extends AbstractShape {
    double length, width;
    public RectangleShape(double l, double w) {
        super("Rectangle");
        this.length = l;
        this.width = w;
    }
    @Override
    public double calculateArea() { return length * width; }
}

class TriangleShape extends AbstractShape {
    double base, height;
    public TriangleShape(double b, double h) {
        super("Triangle");
        this.base = b;
        this.height = h;
    }
    @Override
    public double calculateArea() { return 0.5 * base * height; }
}

class CircleShape extends AbstractShape {
    double radius;
    public CircleShape(double r) {
        super("Circle");
        this.radius = r;
    }
    @Override
    public double calculateArea() { return Math.PI * radius * radius; }
}

public class Q5_AbstractShapeArea {
    public static void main(String[] args) {
        AbstractShape s1 = new RectangleShape(10, 5);
        AbstractShape s2 = new TriangleShape(8, 6);
        AbstractShape s3 = new CircleShape(7);

        s1.displayArea();
        s2.displayArea();
        s3.displayArea();
    }
}
