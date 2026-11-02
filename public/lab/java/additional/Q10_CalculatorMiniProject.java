// Q10_CalculatorMiniProject.java
// 10. Design a calculator as a mini project using event driven programming paradigm of java with the following options:
// a) Decimal Manipulations b) Scientific Manipulations.
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Q10_CalculatorMiniProject extends JFrame implements ActionListener {
    private JTextField display;
    private double num1 = 0, num2 = 0, result = 0;
    private String operator = "";
    private boolean startNew = true;

    public Q10_CalculatorMiniProject() {
        setTitle("Java Event-Driven Scientific Calculator");
        setSize(420, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        display = new JTextField("0");
        display.setFont(new Font("Arial", Font.BOLD, 26));
        display.setHorizontalAlignment(JTextField.RIGHT);
        display.setEditable(false);
        display.setBackground(Color.WHITE);
        display.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(display, BorderLayout.NORTH);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(6, 4, 6, 6));

        String[] buttons = {
            "sin", "cos", "tan", "sqrt",
            "log", "x^2", "1/x", "C",
            "7", "8", "9", "/",
            "4", "5", "6", "*",
            "1", "2", "3", "-",
            "0", ".", "=", "+"
        };

        for (String text : buttons) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Arial", Font.BOLD, 16));
            btn.addActionListener(this);
            panel.add(btn);
        }

        add(panel, BorderLayout.CENTER);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();

        if ((cmd.charAt(0) >= '0' && cmd.charAt(0) <= '9') || cmd.equals(".")) {
            if (startNew) {
                display.setText(cmd.equals(".") ? "0." : cmd);
                startNew = false;
            } else {
                if (cmd.equals(".") && display.getText().contains(".")) return;
                display.setText(display.getText() + cmd);
            }
        } else if (cmd.equals("C")) {
            display.setText("0");
            num1 = num2 = result = 0;
            operator = "";
            startNew = true;
        } else if (cmd.equals("=")) {
            num2 = Double.parseDouble(display.getText());
            switch (operator) {
                case "+": result = num1 + num2; break;
                case "-": result = num1 - num2; break;
                case "*": result = num1 * num2; break;
                case "/": result = num2 != 0 ? num1 / num2 : 0; break;
            }
            display.setText(String.valueOf(result));
            startNew = true;
        } else if (cmd.equals("sin") || cmd.equals("cos") || cmd.equals("tan") || 
                   cmd.equals("sqrt") || cmd.equals("log") || cmd.equals("x^2") || cmd.equals("1/x")) {
            double val = Double.parseDouble(display.getText());
            switch (cmd) {
                case "sin": result = Math.sin(Math.toRadians(val)); break;
                case "cos": result = Math.cos(Math.toRadians(val)); break;
                case "tan": result = Math.tan(Math.toRadians(val)); break;
                case "sqrt": result = Math.sqrt(val); break;
                case "log": result = Math.log10(val); break;
                case "x^2": result = val * val; break;
                case "1/x": result = val != 0 ? 1 / val : 0; break;
            }
            display.setText(String.valueOf(result));
            startNew = true;
        } else { // Binary operators +, -, *, /
            num1 = Double.parseDouble(display.getText());
            operator = cmd;
            startNew = true;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Q10_CalculatorMiniProject());
    }
}
