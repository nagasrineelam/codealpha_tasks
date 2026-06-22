import java.util.ArrayList;
import java.util.Scanner;

/**
 * Student Grade Tracker
 * -----------------------------------------------------
 * A console-based Java application to record students and
 * their grades, then compute averages, highest/lowest scores,
 * and display a full summary report.
 */
public class StudentGradeTracker {

    // Represents a single student and their list of grades
    static class Student {
        private String name;
        private ArrayList<Double> grades;

        Student(String name) {
            this.name = name;
            this.grades = new ArrayList<>();
        }

        String getName() {
            return name;
        }

        ArrayList<Double> getGrades() {
            return grades;
        }

        void addGrade(double grade) {
            grades.add(grade);
        }

        double getAverage() {
            if (grades.isEmpty()) return 0.0;
            double sum = 0;
            for (double g : grades) sum += g;
            return sum / grades.size();
        }

        double getHighest() {
            if (grades.isEmpty()) return 0.0;
            double max = grades.get(0);
            for (double g : grades) if (g > max) max = g;
            return max;
        }

        double getLowest() {
            if (grades.isEmpty()) return 0.0;
            double min = grades.get(0);
            for (double g : grades) if (g < min) min = g;
            return min;
        }

        String getLetterGrade() {
            double avg = getAverage();
            if (avg >= 90) return "A";
            if (avg >= 80) return "B";
            if (avg >= 70) return "C";
            if (avg >= 60) return "D";
            return "F";
        }
    }

    // Master list of all students
    private static ArrayList<Student> students = new ArrayList<>();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        boolean running = true;

        System.out.println("=================================================");
        System.out.println("          STUDENT GRADE TRACKER");
        System.out.println("=================================================");

        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    addStudent();
                    break;
                case 2:
                    addGradeToStudent();
                    break;
                case 3:
                    viewStudentReport();
                    break;
                case 4:
                    viewSummaryReport();
                    break;
                case 5:
                    removeStudent();
                    break;
                case 6:
                    running = false;
                    System.out.println("\nExiting Student Grade Tracker. Goodbye!");
                    break;
                default:
                    System.out.println("\nInvalid choice. Please select a number from 1 to 6.\n");
            }
        }

        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n-------------------- MENU --------------------");
        System.out.println("1. Add a new student");
        System.out.println("2. Add a grade to a student");
        System.out.println("3. View one student's report");
        System.out.println("4. View summary report (all students)");
        System.out.println("5. Remove a student");
        System.out.println("6. Exit");
        System.out.println("-----------------------------------------------");
    }

    // ---------- Core actions ----------

    private static void addStudent() {
        System.out.print("\nEnter student name: ");
        String name = scanner.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Student name cannot be empty.");
            return;
        }
        if (findStudent(name) != null) {
            System.out.println("A student with that name already exists.");
            return;
        }

        students.add(new Student(name));
        System.out.println("Student \"" + name + "\" added successfully.");
    }

    private static void addGradeToStudent() {
        if (students.isEmpty()) {
            System.out.println("\nNo students yet. Please add a student first.");
            return;
        }

        Student s = selectStudent();
        if (s == null) return;

        double grade = readDouble("Enter grade for " + s.getName() + " (0-100): ");
        if (grade < 0 || grade > 100) {
            System.out.println("Grade must be between 0 and 100. Not added.");
            return;
        }

        s.addGrade(grade);
        System.out.println("Grade " + grade + " added for " + s.getName() + ".");
    }

    private static void viewStudentReport() {
        if (students.isEmpty()) {
            System.out.println("\nNo students yet. Please add a student first.");
            return;
        }

        Student s = selectStudent();
        if (s == null) return;

        System.out.println("\n----------- Report for " + s.getName() + " -----------");
        if (s.getGrades().isEmpty()) {
            System.out.println("No grades recorded yet.");
        } else {
            System.out.println("Grades   : " + s.getGrades());
            System.out.printf("Average  : %.2f%n", s.getAverage());
            System.out.printf("Highest  : %.2f%n", s.getHighest());
            System.out.printf("Lowest   : %.2f%n", s.getLowest());
            System.out.println("Letter   : " + s.getLetterGrade());
        }
        System.out.println("------------------------------------------------");
    }

    private static void viewSummaryReport() {
        if (students.isEmpty()) {
            System.out.println("\nNo students yet. Please add a student first.");
            return;
        }

        System.out.println("\n=========================== SUMMARY REPORT ===========================");
        System.out.printf("%-15s %-8s %-8s %-8s %-10s %-6s%n",
                "Name", "Count", "Avg", "High", "Low", "Grade");
        System.out.println("------------------------------------------------------------------------");

        double classTotal = 0;
        int classGradeCount = 0;
        double classHighest = Double.NEGATIVE_INFINITY;
        double classLowest = Double.POSITIVE_INFINITY;
        String topStudent = "";
        double topAverage = Double.NEGATIVE_INFINITY;

        for (Student s : students) {
            int count = s.getGrades().size();
            double avg = s.getAverage();
            double high = s.getHighest();
            double low = s.getLowest();

            System.out.printf("%-15s %-8d %-8.2f %-8.2f %-10.2f %-6s%n",
                    s.getName(), count, avg, high, low, s.getLetterGrade());

            if (count > 0) {
                for (double g : s.getGrades()) classTotal += g;
                classGradeCount += count;
                if (high > classHighest) classHighest = high;
                if (low < classLowest) classLowest = low;
                if (avg > topAverage) {
                    topAverage = avg;
                    topStudent = s.getName();
                }
            }
        }

        System.out.println("------------------------------------------------------------------------");

        if (classGradeCount > 0) {
            double classAverage = classTotal / classGradeCount;
            System.out.printf("Class average (all grades): %.2f%n", classAverage);
            System.out.printf("Highest score in class    : %.2f%n", classHighest);
            System.out.printf("Lowest score in class      : %.2f%n", classLowest);
            System.out.println("Top performing student     : " + topStudent +
                    String.format(" (%.2f avg)", topAverage));
        } else {
            System.out.println("No grades have been recorded for any student yet.");
        }
        System.out.println("=========================================================================");
    }

    private static void removeStudent() {
        if (students.isEmpty()) {
            System.out.println("\nNo students yet. Please add a student first.");
            return;
        }

        Student s = selectStudent();
        if (s == null) return;

        students.remove(s);
        System.out.println("Removed student \"" + s.getName() + "\".");
    }

    // ---------- Helper methods ----------

    private static Student findStudent(String name) {
        for (Student s : students) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    private static Student selectStudent() {
        System.out.println("\nStudents:");
        for (int i = 0; i < students.size(); i++) {
            System.out.println((i + 1) + ". " + students.get(i).getName());
        }
        int index = readInt("Select a student by number: ") - 1;

        if (index < 0 || index >= students.size()) {
            System.out.println("Invalid selection.");
            return null;
        }
        return students.get(index);
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private static double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
