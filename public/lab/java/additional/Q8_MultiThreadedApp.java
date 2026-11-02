// Q8_MultiThreadedApp.java
// 8. Write a java program for implementing multi-threaded application.
import java.util.Random;

class NumberGenerator extends Thread {
    public void run() {
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            int num = rand.nextInt(50) + 1;
            System.out.println("\nGenerated Number: " + num);
            if (num % 2 == 0) {
                new SquareThread(num).start();
            } else {
                new CubeThread(num).start();
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }
    }
}

class SquareThread extends Thread {
    int n;
    SquareThread(int n) { this.n = n; }
    public void run() {
        System.out.println("Square of " + n + " = " + (n * n));
    }
}

class CubeThread extends Thread {
    int n;
    CubeThread(int n) { this.n = n; }
    public void run() {
        System.out.println("Cube of " + n + " = " + (n * n * n));
    }
}

public class Q8_MultiThreadedApp {
    public static void main(String[] args) {
        System.out.println("Starting Multi-threaded Application...");
        NumberGenerator generator = new NumberGenerator();
        generator.start();
    }
}
