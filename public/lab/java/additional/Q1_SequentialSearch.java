// Q1_SequentialSearch.java
// 1. Solve problem by using sequential search algorithms (Selection, insertion)
import java.util.Scanner;

public class Q1_SequentialSearch {
    // Sequential (Linear) Search
    public static int sequentialSearch(int[] arr, int key) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == key) return i;
        }
        return -1;
    }

    // Selection Sort
    public static void selectionSort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIdx]) minIdx = j;
            }
            int temp = arr[minIdx];
            arr[minIdx] = arr[i];
            arr[i] = temp;
        }
    }

    // Insertion Sort
    public static void insertionSort(int[] arr) {
        int n = arr.length;
        for (int i = 1; i < n; i++) {
            int key = arr[i];
            int j = i - 1;
            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    public static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
        System.out.println();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter number of elements: ");
        int n = sc.nextInt();
        int[] arr = new int[n];
        System.out.println("Enter " + n + " elements:");
        for (int i = 0; i < n; i++) arr[i] = sc.nextInt();

        System.out.print("Enter element to search (Sequential Search): ");
        int key = sc.nextInt();
        int result = sequentialSearch(arr, key);
        if (result != -1) {
            System.out.println("Element " + key + " found at index " + result);
        } else {
            System.out.println("Element " + key + " not found!");
        }

        int[] arrSelect = arr.clone();
        selectionSort(arrSelect);
        System.out.print("Array after Selection Sort: ");
        printArray(arrSelect);

        int[] arrInsert = arr.clone();
        insertionSort(arrInsert);
        System.out.print("Array after Insertion Sort: ");
        printArray(arrInsert);

        sc.close();
    }
}
