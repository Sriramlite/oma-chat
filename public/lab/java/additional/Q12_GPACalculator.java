// Q12_GPACalculator.java
// 12. Create a Java Application to calculate GPA for 5 subjects using the marks of the subject and credits of the subject 
// and implement a user defined exception for subject mark, credits, subject name, and subject code.
import java.util.Scanner;

class InvalidSubjectException extends Exception {
    public InvalidSubjectException(String msg) {
        super(msg);
    }
}

public class Q12_GPACalculator {
    public static int getGradePoint(int mark) {
        if (mark >= 90) return 10;
        else if (mark >= 80) return 9;
        else if (mark >= 70) return 8;
        else if (mark >= 60) return 7;
        else if (mark >= 50) return 6;
        else return 0;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int numSubjects = 5;
        double totalGradePoints = 0;
        int totalCredits = 0;

        System.out.println("=== GPA Calculator for " + numSubjects + " Subjects ===");
        try {
            for (int i = 1; i <= numSubjects; i++) {
                System.out.println("\nSubject " + i + ":");
                System.out.print("Enter Subject Code (e.g., CS303): ");
                String code = sc.next().trim();
                if (code.isEmpty() || code.length() < 3) {
                    throw new InvalidSubjectException("Invalid Subject Code: " + code);
                }

                System.out.print("Enter Subject Name: ");
                String name = sc.next().trim();
                if (name.isEmpty()) {
                    throw new InvalidSubjectException("Subject Name cannot be empty!");
                }

                System.out.print("Enter Credits (1 - 5): ");
                int credits = sc.nextInt();
                if (credits <= 0 || credits > 5) {
                    throw new InvalidSubjectException("Invalid Credits (" + credits + ")! Must be between 1 and 5.");
                }

                System.out.print("Enter Marks (0 - 100): ");
                int mark = sc.nextInt();
                if (mark < 0 || mark > 100) {
                    throw new InvalidSubjectException("Invalid Mark (" + mark + ")! Must be between 0 and 100.");
                }

                int gp = getGradePoint(mark);
                totalGradePoints += (gp * credits);
                totalCredits += credits;
            }

            double gpa = totalGradePoints / totalCredits;
            System.out.println("\n=================================");
            System.out.printf("Total Credits: %d\n", totalCredits);
            System.out.printf("Calculated GPA: %.2f / 10.0\n", gpa);
            System.out.println("=================================");

        } catch (InvalidSubjectException e) {
            System.out.println("\n[ERROR] Custom Exception: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("\n[ERROR] Input Error: " + e.getMessage());
        } finally {
            sc.close();
        }
    }
}
