package ui;

import db.Database;
import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class LoginForm extends JFrame {
    JTextField txtUser = new JTextField();
    JPasswordField txtPass = new JPasswordField();
    JComboBox<String> cbRole = new JComboBox<>(new String[]{"CUSTOMER", "OWNER"});
    JButton btnLogin = new JButton("Login");
    JButton btnRegister = new JButton("Register");

    public LoginForm() {
        setTitle("Hotel Booking - Login");
        setSize(420, 260);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(new JLabel("Username:")); form.add(txtUser);
        form.add(new JLabel("Password:")); form.add(txtPass);
        form.add(new JLabel("Login as:")); form.add(cbRole);
        form.add(btnLogin); form.add(btnRegister);

        add(new JLabel(" ", SwingConstants.CENTER), BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);

        // Only allow registration for customers
        cbRole.addActionListener(e -> btnRegister.setVisible(cbRole.getSelectedItem().equals("CUSTOMER")));

        btnLogin.addActionListener(e -> login());
        btnRegister.addActionListener(e -> {
            new RegisterForm();
            dispose();
        });

        btnRegister.setVisible(true);
        setVisible(true);
    }

    void login() {
        String user = txtUser.getText();
        String pass = new String(txtPass.getPassword());
        String role = cbRole.getSelectedItem().toString();

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM users WHERE username=? AND password=? AND user_type=?")) {
            ps.setString(1, user);
            ps.setString(2, pass);
            ps.setString(3, role);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("id");
                if (role.equalsIgnoreCase("OWNER")) new OwnerDashboardClean(userId);
                else new CustomerDashboard(userId);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials or role mismatch");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }
}
