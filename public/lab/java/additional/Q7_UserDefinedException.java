// Q7_UserDefinedException.java
// 7. Write a java program for implementing user defined exceptions handling.
import java.util.Scanner;

class InvalidAgeException extends Exception {
    public InvalidAgeException(String message) {
        super(message);
    }
}

public class Q7_UserDefinedException {
    public static void validateAge(int age) throws InvalidAgeException {
        if (age < 18) {
            throw new InvalidAgeException("Age is below 18 (" + age + "). Not eligible for voting!");
        } else {
            System.out.println("Welcome! Age " + age + " is eligible for voting.");
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter your age: ");
        int age = sc.nextInt();
        try {
            validateAge(age);
        } catch (InvalidAgeException e) {
            System.out.println("Caught Custom Exception: " + e.getMessage());
        } finally {
            System.out.println("Validation process completed.");
            sc.close();
        }
    }
}
