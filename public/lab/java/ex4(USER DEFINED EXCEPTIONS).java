import java.io.*;
import
java.lang.*;
public class
Demo
{
public static void main(String[] args)
{
try
{
try
{
int b=20/0; //ArithmeticException
}
catch(ArithmeticException e)
{
System.out.println("ArithmeticException is Found");
}
int a[]=new int[5];
a[5]=30; //ArrayIndexOutOfBoundsException String s="abc";
int i=Integer.parseInt(s); //NumberFormatException
String s1=null; System.out.println(s1.length());
//NullPointerException
}
catch(NumberFormatException e)
{
System.out.println("NumberFormatException is Found");
}
catch(ArrayIndexOutOfBoundsException e)
{
System.out.println("ArrayIndexOutOfBoundsException is Found");
}
catch(NullPointerException e)
{
System.out.println("NullPointerException is Found");
}
catch(Exception e)
{
System.out.println("Exception is found");
}
finally
{
System.out.println("finally block is always executed");
}}}