import java.io.*; abstract class shape
{
int a=10,b=20;
abstract void printArea();
}
class Circle extends shape
{
void printArea()
{
double pi=3.14; double area=pi*a*a; System.out.println("circle area is"+area);
} }
class Rectangle extends shape
{
void printArea()
{
double area=a*b; System.out.println("Rectangle area is"+area);
} }
class Triangle extends shape
{
void printArea()
{
double area=0.5*a*b;
System.out.println("Triangle area is"+area);
} }
class Demo
{
public static void main(String[] args)
{
Circle c1=new Circle(); c1.printArea(); Rectangle r1=new Rectangle(); r1.printArea();
Triangle t1=new Triangle(); t1.printArea();
} }