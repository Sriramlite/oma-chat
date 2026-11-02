// Q14_DogConstructor.java
// 14. Write a Java program to create a class called Dog with instance variables name and color. 
// Implement a parameterized constructor that takes name and color as parameters and initializes the instance variables. 
// Print the values of the variables.

class Dog {
    private String name;
    private String color;

    // Parameterized constructor
    public Dog(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public void display() {
        System.out.println("Dog Details:");
        System.out.println("Name  : " + name);
        System.out.println("Color : " + color);
    }
}

public class Q14_DogConstructor {
    public static void main(String[] args) {
        Dog dog1 = new Dog("Buddy", "Golden Brown");
        Dog dog2 = new Dog("Max", "Black");

        dog1.display();
        System.out.println();
        dog2.display();
    }
}
