package ui;

import db.Database;
import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class RegisterForm extends JFrame {
    JTextField name = new JTextField();
    JTextField username = new JTextField();
    JPasswordField password = new JPasswordField();
    JTextField email = new JTextField();
    JTextField phone = new JTextField();

    JButton back = new JButton("Back");
    JButton register = new JButton("Register");

    public RegisterForm() {
        setTitle("Register");
        setSize(420, 360);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(new JLabel("Name:")); form.add(name);
        form.add(new JLabel("Username:")); form.add(username);
        form.add(new JLabel("Password:")); form.add(password);
        form.add(new JLabel("Email:")); form.add(email);
        form.add(new JLabel("Phone:")); form.add(phone);
        form.add(back); form.add(register);

        add(new JLabel(" ", SwingConstants.CENTER), BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);

        register.addActionListener(e -> registerUser());
        back.addActionListener(e -> {
            new LoginForm();
            dispose();
        });

        setVisible(true);
    }

    void registerUser() {
        String nm = name.getText().trim();
        String un = username.getText().trim();
        String pw = new String(password.getPassword());
        String em = email.getText().trim().toLowerCase();
        String ph = phone.getText().trim();

        // Basic validations
        if (!em.contains("@") || !em.endsWith(".com")) { JOptionPane.showMessageDialog(this, "Email must contain @ and end with .com"); return; }
        if (ph.length() != 10 || !ph.chars().allMatch(Character::isDigit)) { JOptionPane.showMessageDialog(this, "Phone must be 10 digits"); return; }

        try (Connection conn = Database.connect()) {
            // check username uniqueness
            try (PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE username=?")) {
                check.setString(1, un);
                ResultSet rs = check.executeQuery();
                if (rs.next()) { JOptionPane.showMessageDialog(this, "Username already exists"); return; }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users(name, username, password, email, phone, user_type) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, nm);
                ps.setString(2, un);
                ps.setString(3, pw);
                ps.setString(4, em);
                ps.setString(5, ph);
                ps.setString(6, "CUSTOMER");
                ps.executeUpdate();
            }

            JOptionPane.showMessageDialog(this, "Registered successfully!");
            new LoginForm();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }
}
