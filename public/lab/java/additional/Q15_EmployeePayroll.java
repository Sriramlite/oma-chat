// Q15_EmployeePayroll.java
// 15. Develop a java application with an Employee class with Emp_name, Emp_id, Address, Mail_id, Mobile no as members.
// Inherit the classes, Programmer, Assistant Professor, Associate Professor and Professor from employee class. 
// Add Basic Pay (BP) as the member of all the inherited classes with 97% of BP as DA, 10 % of BP as HRA, 
// 12% of BP as PF, 0.1% of BP for staff club funds. Generate pay slips for the employees with their gross and net salary.

class Employee {
    String empName, empId, address, mailId, mobileNo;

    public Employee(String name, String id, String address, String mail, String mobile) {
        this.empName = name;
        this.empId = id;
        this.address = address;
        this.mailId = mail;
        this.mobileNo = mobile;
    }

    public void printEmployeeDetails(String designation, double bp) {
        double da = 0.97 * bp;
        double hra = 0.10 * bp;
        double pf = 0.12 * bp;
        double staffClub = 0.001 * bp;
        double gross = bp + da + hra;
        double net = gross - (pf + staffClub);

        System.out.println("\n-------------------------------------------");
        System.out.println("               PAY SLIP                    ");
        System.out.println("-------------------------------------------");
        System.out.println("Employee ID   : " + empId);
        System.out.println("Employee Name : " + empName);
        System.out.println("Designation   : " + designation);
        System.out.println("Address       : " + address);
        System.out.println("Email         : " + mailId);
        System.out.println("Mobile        : " + mobileNo);
        System.out.println("-------------------------------------------");
        System.out.printf("Basic Pay (BP): Rs. %.2f\n", bp);
        System.out.printf("DA (97%%)      : Rs. %.2f\n", da);
        System.out.printf("HRA (10%%)     : Rs. %.2f\n", hra);
        System.out.printf("PF (12%%)      : Rs. %.2f\n", pf);
        System.out.printf("Staff Club(0.1%%): Rs. %.2f\n", staffClub);
        System.out.println("-------------------------------------------");
        System.out.printf("GROSS SALARY  : Rs. %.2f\n", gross);
        System.out.printf("NET SALARY    : Rs. %.2f\n", net);
        System.out.println("-------------------------------------------");
    }
}

class Programmer extends Employee {
    double bp;
    public Programmer(String name, String id, String addr, String mail, String mob, double bp) {
        super(name, id, addr, mail, mob);
        this.bp = bp;
    }
    public void generatePaySlip() { printEmployeeDetails("Programmer", bp); }
}

class AssistantProfessor extends Employee {
    double bp;
    public AssistantProfessor(String name, String id, String addr, String mail, String mob, double bp) {
        super(name, id, addr, mail, mob);
        this.bp = bp;
    }
    public void generatePaySlip() { printEmployeeDetails("Assistant Professor", bp); }
}

class AssociateProfessor extends Employee {
    double bp;
    public AssociateProfessor(String name, String id, String addr, String mail, String mob, double bp) {
        super(name, id, addr, mail, mob);
        this.bp = bp;
    }
    public void generatePaySlip() { printEmployeeDetails("Associate Professor", bp); }
}

class Professor extends Employee {
    double bp;
    public Professor(String name, String id, String addr, String mail, String mob, double bp) {
        super(name, id, addr, mail, mob);
        this.bp = bp;
    }
    public void generatePaySlip() { printEmployeeDetails("Professor", bp); }
}

public class Q15_EmployeePayroll {
    public static void main(String[] args) {
        Programmer p = new Programmer("John Doe", "P101", "Chennai", "john@oma.com", "9876543210", 45000);
        p.generatePaySlip();

        Professor prof = new Professor("Dr. Alan Smith", "PRF201", "Coimbatore", "alan@oma.com", "9123456789", 95000);
        prof.generatePaySlip();
    }
}
