package com.example.bpmn;

import java.util.ArrayList;
import java.util.List;

/**
 * File mẫu học cú pháp cơ bản của Java (Java 21)
 */
public class HelloWorld {

    public static void main(String[] args) {
        // 1. In ra màn hình console
        System.out.println("==========================================");
        System.out.println("🎉 Xin chào! Hello, World từ Java 21!");
        System.out.println("==========================================");

        // 2. Khai báo biến và kiểu dữ liệu cơ bản
        int age = 20;
        double score = 9.5;
        boolean isLearningJava = true;
        String name = "Java Developer";


        System.out.println("\n--- 1. Biến & Kiểu dữ liệu ---");
        System.out.println("Tên: " + name);
        System.out.println("Tuổi: " + age);
        System.out.println("Điểm: " + score);
        System.out.println("Đang học Java: " + isLearningJava);

        // 3. Câu lệnh điều kiện (if - else)
        System.out.println("\n--- 2. Cấu trúc rẽ nhánh (if - else) ---");
        if (score >= 8.0) {
            System.out.println("Xếp loại: Giỏi");
        } else if (score >= 6.5) {
            System.out.println("Xếp loại: Khá");
        } else {
            System.out.println("Xếp loại: Trung bình");
        }

        // 4. Vòng lặp (for & for-each)
        System.out.println("\n--- 3. Vòng lặp (Loops) & Danh sách (List) ---");
        List<String> topics = new ArrayList<>();
        topics.add("Cú pháp cơ bản (Syntax)");
        topics.add("Hướng đối tượng (OOP)");
        topics.add("Collections (List, Map, Set)");
        topics.add("Xử lý ngoại lệ (Exception Handling)");

        // Vòng lặp for-each duyệt qua từng phần tử
        for (int i = 0; i < topics.size(); i++) {
            System.out.println("Chủ đề " + (i + 1) + ": " + topics.get(i));
        }

        // 5. Gọi hàm / phương thức (Method)
        System.out.println("\n--- 4. Gọi phương thức (Method) ---");
        int sum = addNumbers(15, 25);
        System.out.println("Tổng của 15 + 25 = " + sum);

        // 6. Đối tượng đơn giản (Object / Record trong Java)
        System.out.println("\n--- 5. Khởi tạo đối tượng (Object) ---");
        Student student = new Student("Thien", 22, "Công nghệ thông tin");
        student.printInfo();
    }

    // Định nghĩa phương thức tính toán
    public static int addNumbers(int a, int b) {
        return a + b;
    }

    // Lớp đối tượng mẫu để học OOP
    static class Student {
        private String name;
        private int age;
        private String major;

        public Student(String name, int age, String major) {
            this.name = name;
            this.age = age;
            this.major = major;
        }

        public void printInfo() {
            System.out.println("Sinh viên: " + name + " | Tuổi: " + age + " | Chuyên ngành: " + major);
        }
    }
}
