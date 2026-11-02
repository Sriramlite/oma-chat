// Q2_BinarySearch.java
// 2. Solve problem by using binary search sorting algorithms (Selection, insertion)
import java.util.Scanner;

public class Q2_BinarySearch {
    // Binary Search on sorted array
    public static int binarySearch(int[] arr, int key) {
        int low = 0, high = arr.length - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (arr[mid] == key) return mid;
            if (arr[mid] < key) low = mid + 1;
            else high = mid - 1;
        }
        return -1;
    }

    public static void selectionSort(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[j] < arr[minIdx]) minIdx = j;
            }
            int temp = arr[minIdx];
            arr[minIdx] = arr[i];
            arr[i] = temp;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter number of elements: ");
        int n = sc.nextInt();
        int[] arr = new int[n];
        System.out.println("Enter " + n + " elements:");
        for (int i = 0; i < n; i++) arr[i] = sc.nextInt();

        selectionSort(arr);
        System.out.print("Sorted array for Binary Search: ");
        for (int x : arr) System.out.print(x + " ");
        System.out.println();

        System.out.print("Enter key to search using Binary Search: ");
        int key = sc.nextInt();
        int idx = binarySearch(arr, key);
        if (idx != -1) {
            System.out.println("Element " + key + " found at sorted index: " + idx);
        } else {
            System.out.println("Element " + key + " not found!");
        }
        sc.close();
    }
}
