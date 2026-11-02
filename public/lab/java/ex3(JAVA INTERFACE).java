Import java.io.*;
Import java.util.*;
Interface AreaCalculable{
Double calculateArea();
}
Abstract class shape{
Protected int length;
Protected int width;
public Shape(int length, int width) { this.length = length;
this.width = width;
}
public abstract void printArea();
}
class Rectangle extends Shape implements AreaCalculable { public Rectangle(int length,
int width) {
super(length, width);
}
@Override
public void printArea() {
System.out.println("Area of Rectangle: " + calculateArea());
}
@Override
public double calculateArea() { return length * width;
} }
class Triangle extends Shape implements AreaCalculable { public Triangle(int length, int
width) {
super(length, width);
}
@Override
public void printArea() {
System.out.println("Area of Triangle: " + calculateArea());
}
@Override
public double calculateArea() { return 0.5 * length * width;
} }
class Circle extends Shape implements AreaCalculable { public Circle(int radius) {
super(radius, radius); // Width and length both represent the radius for a circle
}
@Override
public void printArea() {
System.out.println("Area of Circle: " + calculateArea());
}
@Override
public double calculateArea() { return Math.PI * length * length;
} }
public class Main {
public static void main(String[] args) { Rectangle rectangle = new Rectangle(5, 4);
rectangle.printArea();
Triangle triangle = new Triangle(6, 8); triangle.printArea();
Circle circle = new Circle(3); circle.printArea();
} }