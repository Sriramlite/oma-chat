Import java.util.LinkedList;
import java.util.Queue;
public class main
{
public static void main(String[] args)
{
Queue<String>queue=new
LinkedList<String>(); queue.add("A");
queue.add("B");
queue.add("C");
queue.add("D");
System.out.println(" The Front element is"
+queue.peek()); queue.remove();
queue.remove();
System.out.println(" The Front element is" +queue.peek());
System.out.println("The queue size is"+queue.size());
if (queue.isEmpty())
{
System.out.println("The queue is empty");
}
else
{
System.out.println("The queue is not empty");
} } }