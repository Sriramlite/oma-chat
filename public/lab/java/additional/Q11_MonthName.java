// Q11_MonthName.java
// 11. Write a Java program that keeps a number from the user and generates an integer between 1 and 12 and displays the name of the month.
import java.util.Scanner;

public class Q11_MonthName {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter a month number (1 - 12): ");
        if (!sc.hasNextInt()) {
            System.out.println("Invalid input! Please enter an integer.");
            sc.close();
            return;
        }
        int month = sc.nextInt();
        String monthName;

        switch (month) {
            case 1: monthName = "January"; break;
            case 2: monthName = "February"; break;
            case 3: monthName = "March"; break;
            case 4: monthName = "April"; break;
            case 5: monthName = "May"; break;
            case 6: monthName = "June"; break;
            case 7: monthName = "July"; break;
            case 8: monthName = "August"; break;
            case 9: monthName = "September"; break;
            case 10: monthName = "October"; break;
            case 11: monthName = "November"; break;
            case 12: monthName = "December"; break;
            default: monthName = "Invalid month number! Must be between 1 and 12."; break;
        }

        System.out.println("Month Name: " + monthName);
        sc.close();
    }
}
