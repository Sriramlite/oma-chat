// Q13_DistanceConverter.java
// 13. Develop a Java application to implement distance converter(meter to KM, miles to KM, and vice versa) using packages.
package distanceconverter;

import java.util.Scanner;

class Converter {
    public static double meterToKm(double meters) {
        return meters / 1000.0;
    }

    public static double kmToMeter(double km) {
        return km * 1000.0;
    }

    public static double milesToKm(double miles) {
        return miles * 1.60934;
    }

    public static double kmToMiles(double km) {
        return km / 1.60934;
    }
}

public class Q13_DistanceConverter {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Distance Converter Application ===");
        System.out.println("1. Meter to KM");
        System.out.println("2. KM to Meter");
        System.out.println("3. Miles to KM");
        System.out.println("4. KM to Miles");
        System.out.print("Choose an option (1-4): ");
        int choice = sc.nextInt();

        System.out.print("Enter value to convert: ");
        double val = sc.nextDouble();

        switch (choice) {
            case 1:
                System.out.printf("%.2f Meters = %.4f KM\n", val, Converter.meterToKm(val));
                break;
            case 2:
                System.out.printf("%.2f KM = %.2f Meters\n", val, Converter.kmToMeter(val));
                break;
            case 3:
                System.out.printf("%.2f Miles = %.4f KM\n", val, Converter.milesToKm(val));
                break;
            case 4:
                System.out.printf("%.2f KM = %.4f Miles\n", val, Converter.kmToMiles(val));
                break;
            default:
                System.out.println("Invalid Option Selected!");
        }
        sc.close();
    }
}
