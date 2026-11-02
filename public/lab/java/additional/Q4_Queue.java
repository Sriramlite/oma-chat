// Q4_Queue.java
// 4. Develop queue data structures using classes and objects.
class Queue {
    private int maxSize;
    private int[] queueArray;
    private int front;
    private int rear;
    private int count;

    public Queue(int size) {
        this.maxSize = size;
        this.queueArray = new int[maxSize];
        this.front = 0;
        this.rear = -1;
        this.count = 0;
    }

    public void enqueue(int value) {
        if (isFull()) {
            System.out.println("Queue Overflow! Cannot enqueue " + value);
            return;
        }
        rear = (rear + 1) % maxSize;
        queueArray[rear] = value;
        count++;
        System.out.println("Enqueued: " + value);
    }

    public int dequeue() {
        if (isEmpty()) {
            System.out.println("Queue Underflow! No elements to dequeue.");
            return -1;
        }
        int val = queueArray[front];
        front = (front + 1) % maxSize;
        count--;
        return val;
    }

    public boolean isEmpty() {
        return (count == 0);
    }

    public boolean isFull() {
        return (count == maxSize);
    }

    public void display() {
        if (isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }
        System.out.print("Queue elements: ");
        for (int i = 0; i < count; i++) {
            System.out.print(queueArray[(front + i) % maxSize] + " ");
        }
        System.out.println();
    }
}

public class Q4_Queue {
    public static void main(String[] args) {
        Queue q = new Queue(5);
        q.enqueue(100);
        q.enqueue(200);
        q.enqueue(300);
        q.display();
        System.out.println("Dequeued: " + q.dequeue());
        q.display();
    }
}
