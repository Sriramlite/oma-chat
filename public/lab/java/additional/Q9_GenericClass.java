// Q9_GenericClass.java
// 9. Write a java program for implementing generic class.
class Container<T> {
    private T item;

    public void set(T item) {
        this.item = item;
    }

    public T get() {
        return item;
    }

    public void displayType() {
        System.out.println("Value: " + item + " | Data Type: " + item.getClass().getSimpleName());
    }
}

public class Q9_GenericClass {
    public static void main(String[] args) {
        Container<Integer> intBox = new Container<>();
        intBox.set(100);
        intBox.displayType();

        Container<String> strBox = new Container<>();
        strBox.set("Java Object Oriented Programming");
        strBox.displayType();

        Container<Double> dblBox = new Container<>();
        dblBox.set(98.75);
        dblBox.displayType();
    }
}
