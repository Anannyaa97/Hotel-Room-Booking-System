package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

/**
 * Clean OwnerDashboard (single-definition).
 * Provides Rooms, Bookings and Customers tabs and a simple "Spot Book" flow.
 */
public class OwnerDashboard extends JFrame {
    private final int ownerId;
    private final JTable roomsTable = new JTable();
    private final JTable bookingsTable = new JTable();
    private final JTable customersTable = new JTable();

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        setTitle("Owner Dashboard");
        setSize(900, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        JButton spot = new JButton("Spot Book");
        top.add(back); top.add(spot);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Rooms", new JScrollPane(roomsTable));
        tabs.add("Bookings", new JScrollPane(bookingsTable));
        tabs.add("Customers", new JScrollPane(customersTable));
        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> { new LoginForm(); dispose(); });
        spot.addActionListener(e -> spotBookDialog());

        loadRooms(); loadBookings(); loadCustomers();
        setVisible(true);
    }

    private void loadRooms() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, room_number, type, price, available FROM rooms ORDER BY room_number")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
            roomsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadBookings() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id ORDER BY b.id DESC")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4), rs.getDate(5)});
            bookingsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadCustomers() {
        try (Connection c = Database.connect()) {
            try (Statement st = c.createStatement()) { st.executeUpdate("ALTER TABLE users ADD COLUMN created_by_owner BOOLEAN NOT NULL DEFAULT FALSE"); } catch (SQLException ignore) {}
            try (PreparedStatement ps = c.prepareStatement("SELECT id, name, username, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER' ORDER BY username")) {
                ResultSet rs = ps.executeQuery(); DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Username","SpotCreated"},0);
                while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)});
                customersTable.setModel(m);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            DefaultComboBoxModel<String> custModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) { try (ResultSet r = p.executeQuery()) { while (r.next()) custModel.addElement(r.getInt(1)+":"+r.getString(2)); } }

            DefaultComboBoxModel<String> roomModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, room_number, available, price FROM rooms ORDER BY room_number")) { try (ResultSet r = p.executeQuery()) { while (r.next()) roomModel.addElement(r.getInt(1)+":"+r.getString(2)+"|"+r.getBoolean(3)+"|"+r.getDouble(4)); } }

            JComboBox<String> custCombo = new JComboBox<>(custModel);
            JComboBox<String> roomCombo = new JComboBox<>(roomModel);
            JPanel ui = new JPanel(new GridLayout(5,2,6,6)); ui.add(new JLabel("Customer:")); ui.add(custCombo); ui.add(new JLabel("Room:")); ui.add(roomCombo);
            ui.add(new JLabel("Start (YYYY-MM-DD):")); JTextField s = new JTextField(); ui.add(s); ui.add(new JLabel("End (YYYY-MM-DD):")); JTextField e = new JTextField(); ui.add(e);
            JCheckBox markPaid = new JCheckBox("Mark payment as PAID"); ui.add(markPaid); ui.add(new JLabel());

            int ok = JOptionPane.showConfirmDialog(this, ui, "Spot Book", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem(); if (cs==null||rs==null) { JOptionPane.showMessageDialog(this, "Select entries"); return; }
            int uid = Integer.parseInt(cs.split(":" )[0]); int rid = Integer.parseInt(rs.split(":" )[0]); java.sql.Date sd = java.sql.Date.valueOf(s.getText().trim()); java.sql.Date ed = java.sql.Date.valueOf(e.getText().trim());

            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, sd); pin.setDate(4, ed); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING"); pin.executeUpdate(); }
            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking saved");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: "+ex.getMessage()); }
    }
}
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class OwnerDashboard extends JFrame {
    private final int ownerId;
    private final JTable roomsTable = new JTable();
    private final JTable bookingsTable = new JTable();
    private final JTable customersTable = new JTable();

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        setTitle("Owner Dashboard");
        setSize(900, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        JButton spot = new JButton("Spot Book");
        top.add(back); top.add(spot);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Rooms", new JScrollPane(roomsTable));
        tabs.add("Bookings", new JScrollPane(bookingsTable));
        tabs.add("Customers", new JScrollPane(customersTable));
        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> { new LoginForm(); dispose(); });
        spot.addActionListener(e -> spotBookDialog());

        loadRooms(); loadBookings(); loadCustomers();
        setVisible(true);
    }

    private void loadRooms() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, room_number, type, price, available FROM rooms")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
            roomsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadBookings() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4), rs.getDate(5)});
            bookingsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadCustomers() {
        try (Connection c = Database.connect(); PreparedStatement st = c.prepareStatement("SELECT id, name, username, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER'")) {
            ResultSet rs = st.executeQuery(); DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Username","SpotCreated"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)});
            customersTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            DefaultComboBoxModel<String> custModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) { try (ResultSet r = p.executeQuery()) { while (r.next()) custModel.addElement(r.getInt(1)+":"+r.getString(2)); } }

            DefaultComboBoxModel<String> roomModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, room_number, available, price FROM rooms ORDER BY room_number")) { try (ResultSet r = p.executeQuery()) { while (r.next()) roomModel.addElement(r.getInt(1)+":"+r.getString(2)+"|"+r.getBoolean(3)+"|"+r.getDouble(4)); } }

            JComboBox<String> custCombo = new JComboBox<>(custModel);
            JComboBox<String> roomCombo = new JComboBox<>(roomModel);
            JPanel ui = new JPanel(new GridLayout(5,2,6,6)); ui.add(new JLabel("Customer:")); ui.add(custCombo); ui.add(new JLabel("Room:")); ui.add(roomCombo);
            ui.add(new JLabel("Start (YYYY-MM-DD):")); JTextField s = new JTextField(); ui.add(s); ui.add(new JLabel("End (YYYY-MM-DD):")); JTextField e = new JTextField(); ui.add(e);
            JCheckBox markPaid = new JCheckBox("Mark payment as PAID"); ui.add(markPaid); ui.add(new JLabel());

            int ok = JOptionPane.showConfirmDialog(this, ui, "Spot Book", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem(); if (cs==null||rs==null) { JOptionPane.showMessageDialog(this, "Select entries"); return; }
            int uid = Integer.parseInt(cs.split(":" )[0]); int rid = Integer.parseInt(rs.split(":" )[0]); java.sql.Date sd = java.sql.Date.valueOf(s.getText().trim()); java.sql.Date ed = java.sql.Date.valueOf(e.getText().trim());

            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, sd); pin.setDate(4, ed); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING"); pin.executeUpdate();
            }
            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking saved");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: "+ex.getMessage()); }
    }
}
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

/**
 * OwnerDashboard - single clean implementation.
 * Provides: Rooms, Bookings, Customers tabs and Owner "Spot Book" functionality.
 */
public class OwnerDashboard extends JFrame {
    private final int ownerId;
    private final JTable roomsTable = new JTable();
    private final JTable bookingsTable = new JTable();
    private final JTable customersTable = new JTable();

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        setTitle("Owner Dashboard");
        setSize(900, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        JButton spot = new JButton("Spot Book");
        top.add(back); top.add(spot);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Rooms", new JScrollPane(roomsTable));
        tabs.add("Bookings", new JScrollPane(bookingsTable));
        tabs.add("Customers", new JScrollPane(customersTable));
        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> { new LoginForm(); dispose(); });
        spot.addActionListener(e -> spotBookDialog());

        loadRooms(); loadBookings(); loadCustomers();
        setVisible(true);
    }

    private void loadRooms() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, room_number, type, price, available FROM rooms")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
            roomsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadBookings() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4), rs.getDate(5)});
            bookingsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadCustomers() {
        try (Connection c = Database.connect(); PreparedStatement st = c.prepareStatement("SELECT id, name, username, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER'")) {
            ResultSet rs = st.executeQuery(); DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Username","SpotCreated"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)});
            customersTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            // Customers dropdown
            DefaultComboBoxModel<String> custModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) {
                try (ResultSet r = p.executeQuery()) { while (r.next()) custModel.addElement(r.getInt(1)+":"+r.getString(2)); }
            }

            // Rooms dropdown
            DefaultComboBoxModel<String> roomModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, room_number, available, price FROM rooms ORDER BY room_number")) {
                try (ResultSet r = p.executeQuery()) { while (r.next()) roomModel.addElement(r.getInt(1)+":"+r.getString(2)+"|"+r.getBoolean(3)+"|"+r.getDouble(4)); }
            }

            JComboBox<String> custCombo = new JComboBox<>(custModel);
            JComboBox<String> roomCombo = new JComboBox<>(roomModel);
            JPanel ui = new JPanel(new GridLayout(5,2,6,6));
            ui.add(new JLabel("Customer:")); ui.add(custCombo);
            ui.add(new JLabel("Room:")); ui.add(roomCombo);
            ui.add(new JLabel("Start (YYYY-MM-DD):")); JTextField s = new JTextField(); ui.add(s);
            ui.add(new JLabel("End (YYYY-MM-DD):")); JTextField e = new JTextField(); ui.add(e);
            JCheckBox markPaid = new JCheckBox("Mark payment as PAID"); ui.add(markPaid); ui.add(new JLabel());

            int ok = JOptionPane.showConfirmDialog(this, ui, "Spot Book", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem();
            if (cs==null||rs==null) { JOptionPane.showMessageDialog(this, "Select entries"); return; }
            int uid = Integer.parseInt(cs.split(":" )[0]); int rid = Integer.parseInt(rs.split(":" )[0]);
            java.sql.Date sd = java.sql.Date.valueOf(s.getText().trim()); java.sql.Date ed = java.sql.Date.valueOf(e.getText().trim());

            // Simple insert booking + mark room unavailable
            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, sd); pin.setDate(4, ed); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING");
                pin.executeUpdate();
            }
            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking saved");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: "+ex.getMessage()); }
    }
}
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

/**
 * Minimal, clean OwnerDashboard implementation.
 */
public class OwnerDashboard extends JFrame {
    private final int ownerId;
    private final JTable roomsTable = new JTable();
    private final JTable bookingsTable = new JTable();
    private final JTable customersTable = new JTable();

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        setTitle("Owner Dashboard");
        setSize(900, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        JButton spot = new JButton("Spot Book");
        top.add(back); top.add(spot);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Rooms", new JScrollPane(roomsTable));
        tabs.add("Bookings", new JScrollPane(bookingsTable));
        tabs.add("Customers", new JScrollPane(customersTable));
        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> { new LoginForm(); dispose(); });
        spot.addActionListener(e -> spotBookDialog());

        loadRooms(); loadBookings(); loadCustomers();
        setVisible(true);
    }

    private void loadRooms() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, room_number, type, price, available FROM rooms")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
            roomsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadBookings() {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4), rs.getDate(5)});
            bookingsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadCustomers() {
        try (Connection c = Database.connect(); PreparedStatement st = c.prepareStatement("SELECT id, name, username, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER'")) {
            ResultSet rs = st.executeQuery(); DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Username","SpotCreated"},0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)});
            customersTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            DefaultComboBoxModel<String> custModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER'")) { ResultSet r = p.executeQuery(); while (r.next()) custModel.addElement(r.getInt(1)+":"+r.getString(2)); }

            DefaultComboBoxModel<String> roomModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, room_number, available, price FROM rooms")) { ResultSet r = p.executeQuery(); while (r.next()) roomModel.addElement(r.getInt(1)+":"+r.getString(2)+"|"+r.getBoolean(3)+"|"+r.getDouble(4)); }

            JComboBox<String> custCombo = new JComboBox<>(custModel);
            JComboBox<String> roomCombo = new JComboBox<>(roomModel);
            JPanel ui = new JPanel(new GridLayout(4,2)); ui.add(new JLabel("Customer:")); ui.add(custCombo); ui.add(new JLabel("Room:")); ui.add(roomCombo); ui.add(new JLabel("Start (YYYY-MM-DD):")); JTextField s = new JTextField(); ui.add(s); ui.add(new JLabel("End (YYYY-MM-DD):")); JTextField e = new JTextField(); ui.add(e);

            int ok = JOptionPane.showConfirmDialog(this, ui, "Spot Book", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem(); if (cs==null||rs==null) { JOptionPane.showMessageDialog(this,"Select entries"); return; }
            int uid = Integer.parseInt(cs.split(":")[0]); int rid = Integer.parseInt(rs.split(":")[0]); java.sql.Date sd = java.sql.Date.valueOf(s.getText().trim()); java.sql.Date ed = java.sql.Date.valueOf(e.getText().trim());

            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)")) { pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, sd); pin.setDate(4, ed); pin.setString(5, "BOOKED"); pin.setString(6, "PENDING"); pin.executeUpdate(); }
            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking saved");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: "+ex.getMessage()); }
    }
}
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

/**
 * OwnerDashboard (clean single file). Implements owner spot-booking UI,
 * shows rooms, bookings and customers, and updates room availability.
 */
public class OwnerDashboard extends JFrame {
    private final int ownerId;
    private final JTable roomsTable = new JTable();
    private final JTable bookingsTable = new JTable();
    private final JTable customersTable = new JTable();

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        setTitle("Owner Dashboard");
        setSize(900, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        JButton spot = new JButton("Spot Book");
        top.add(back); top.add(spot);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Rooms", new JScrollPane(roomsTable));
        tabs.add("Bookings", new JScrollPane(bookingsTable));
        tabs.add("Customers", new JScrollPane(customersTable));
        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> { new LoginForm(); dispose(); });
        spot.addActionListener(e -> spotBookDialog());

        loadRooms(); loadBookings(); loadCustomers();
        setVisible(true);
    }

    private void loadRooms() {
        try (Connection conn = Database.connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT id, room_number, type, price, available FROM rooms ORDER BY room_number")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"}, 0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
            roomsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading rooms: " + ex.getMessage()); }
    }

    private void loadBookings() {
        try (Connection conn = Database.connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date, b.status, b.payment_status FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id ORDER BY b.id DESC")) {
            DefaultTableModel m = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End","Status","Payment"}, 0);
            while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getString("room_number"), rs.getDate("start_date"), rs.getDate("end_date"), rs.getString("status"), rs.getString("payment_status")});
            bookingsTable.setModel(m);
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading bookings: " + ex.getMessage()); }
    }

    private void loadCustomers() {
        try (Connection conn = Database.connect()) {
            try (Statement st = conn.createStatement()) { st.executeUpdate("ALTER TABLE users ADD COLUMN created_by_owner BOOLEAN NOT NULL DEFAULT FALSE"); } catch (SQLException ignore) {}
            String q = "SELECT id, name, username, email, phone, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER' ORDER BY username";
            try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
                DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Username","Email","Phone","SpotCreated"}, 0);
                while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("username"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("created_by_owner")});
                customersTable.setModel(m);
            }
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading customers: " + ex.getMessage()); }
    }

    private void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            DefaultComboBoxModel<String> custModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) { ResultSet r = p.executeQuery(); while (r.next()) custModel.addElement(r.getInt("id")+":"+r.getString("username")); }

            DefaultComboBoxModel<String> roomModel = new DefaultComboBoxModel<>();
            try (PreparedStatement p = conn.prepareStatement("SELECT id, room_number, available, price FROM rooms ORDER BY room_number")) { ResultSet r = p.executeQuery(); while (r.next()) roomModel.addElement(r.getInt("id")+":"+r.getString("room_number")+"|"+r.getBoolean("available")+"|"+r.getDouble("price")); }

            JComboBox<String> custCombo = new JComboBox<>(custModel);
            JComboBox<String> roomCombo = new JComboBox<>(roomModel);
            JTextField startField = new JTextField(); JTextField endField = new JTextField(); JCheckBox markPaid = new JCheckBox("Mark payment as PAID");
            JPanel p = new JPanel(new GridLayout(5,2,6,6));
            p.add(new JLabel("Customer:")); p.add(custCombo);
            p.add(new JLabel("Room:")); p.add(roomCombo);
            p.add(new JLabel("Start (YYYY-MM-DD):")); p.add(startField);
            p.add(new JLabel("End (YYYY-MM-DD):")); p.add(endField);
            p.add(markPaid); p.add(new JLabel());

            int res = JOptionPane.showConfirmDialog(this, p, "Spot Book", JOptionPane.OK_CANCEL_OPTION); if (res != JOptionPane.OK_OPTION) return;
            String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem(); if (cs==null||rs==null) { JOptionPane.showMessageDialog(this, "Select customer and room"); return; }
            int uid = Integer.parseInt(cs.split(":")[0]); int rid = Integer.parseInt(rs.split(":")[0]); String start = startField.getText().trim(); String end = endField.getText().trim(); if (start.isEmpty()||end.isEmpty()) { JOptionPane.showMessageDialog(this, "Start and end required"); return; }

            boolean available = true; double price = 0.0; try (PreparedStatement pr = conn.prepareStatement("SELECT available, price FROM rooms WHERE id=?")) { pr.setInt(1, rid); try (ResultSet rr = pr.executeQuery()) { if (rr.next()) { available = rr.getBoolean("available"); price = rr.getDouble("price"); } else { JOptionPane.showMessageDialog(this, "Room not found"); return; } } }
            if (!available) { int confirm = JOptionPane.showConfirmDialog(this, "Room is currently unavailable. Force book?", "Confirm", JOptionPane.YES_NO_OPTION); if (confirm != JOptionPane.YES_OPTION) return; }

            java.time.LocalDate sDate = java.time.LocalDate.parse(start); java.time.LocalDate eDate = java.time.LocalDate.parse(end); long nights = java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate); if (nights <= 0) { JOptionPane.showMessageDialog(this, "End date must be after start date"); return; }

            int bookingId = -1; try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, java.sql.Date.valueOf(start)); pin.setDate(4, java.sql.Date.valueOf(end)); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING"); pin.executeUpdate(); try (ResultSet k = pin.getGeneratedKeys()) { if (k.next()) bookingId = k.getInt(1); } }

            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            if (markPaid.isSelected()) { double total = price * nights; try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method) VALUES(?,?,?,?,?)")) { ps.setInt(1, uid); ps.setInt(2, bookingId); ps.setDouble(3, total); ps.setString(4, "PAID"); ps.setString(5, "CASH"); ps.executeUpdate(); } }

            loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking created (ID: " + bookingId + ")");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: " + ex.getMessage()); }
    }
}
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class OwnerDashboard extends JFrame {
    int ownerId;
    JTable roomsTable;
    JTable bookingsTable;
    package ui;

    import db.Database;
    import javax.swing.*;
    import javax.swing.table.DefaultTableModel;
    import java.awt.*;
    import java.sql.*;

    /**
     * Clean, single-definition OwnerDashboard. Replaces corrupted file contents.
     */
    public class OwnerDashboard extends JFrame {
        int ownerId;
        JTable roomsTable;
        JTable bookingsTable;
        JTable customersTable;

        public OwnerDashboard(int ownerId) {
            this.ownerId = ownerId;
            setTitle("Owner Dashboard");
            setSize(900, 500);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(8,8));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton back = new JButton("Back");
            JButton btnTopSpotBook = new JButton("Spot Book");
            top.add(back);
            top.add(btnTopSpotBook);
            add(top, BorderLayout.NORTH);

            JTabbedPane tabs = new JTabbedPane();

            JPanel rm = new JPanel(new BorderLayout());
            roomsTable = new JTable();
            rm.add(new JScrollPane(roomsTable), BorderLayout.CENTER);

            JPanel rmActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnAddRoom = new JButton("Add Room");
            JButton btnUpdateRoom = new JButton("Update Room");
            rmActions.add(btnAddRoom);
            rmActions.add(btnUpdateRoom);
            rm.add(rmActions, BorderLayout.SOUTH);
            tabs.add("Room Management", rm);

            package ui;

            import db.Database;
            import javax.swing.*;
            import javax.swing.table.DefaultTableModel;
            import java.awt.*;
            import java.sql.*;

            // Single clean OwnerDashboard: one package, one class, no duplicates
            public class OwnerDashboard extends JFrame {
                private final int ownerId;
                private final JTable roomsTable = new JTable();
                private final JTable bookingsTable = new JTable();
                private final JTable customersTable = new JTable();

                public OwnerDashboard(int ownerId) {
                    this.ownerId = ownerId;
                    setTitle("Owner Dashboard");
                    setSize(900, 520);
                    setLocationRelativeTo(null);
                    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    setLayout(new BorderLayout(8,8));

                    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
                    JButton back = new JButton("Back");
                    JButton spot = new JButton("Spot Book");
                    top.add(back); top.add(spot);
                    add(top, BorderLayout.NORTH);

                    JTabbedPane tabs = new JTabbedPane();
                    tabs.add("Rooms", new JScrollPane(roomsTable));
                    tabs.add("Bookings", new JScrollPane(bookingsTable));
                    tabs.add("Customers", new JScrollPane(customersTable));
                    add(tabs, BorderLayout.CENTER);

                    back.addActionListener(e -> { new LoginForm(); dispose(); });
                    spot.addActionListener(e -> spotBookDialog());

                    loadRooms(); loadBookings(); loadCustomers();
                    setVisible(true);
                }

                private void loadRooms() {
                    try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, room_number, type, price, available FROM rooms")) {
                        DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"},0);
                        while (rs.next()) m.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
                        roomsTable.setModel(m);
                    } catch (Exception ex) { ex.printStackTrace(); }
                }

                private void loadBookings() {
                    try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id")) {
                        DefaultTableModel m = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End"},0);
                        while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4), rs.getDate(5)});
                        bookingsTable.setModel(m);
                    } catch (Exception ex) { ex.printStackTrace(); }
                }

                private void loadCustomers() {
                    try (Connection c = Database.connect(); PreparedStatement st = c.prepareStatement("SELECT id, name, username, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER'")) {
                        ResultSet rs = st.executeQuery(); DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Username","SpotCreated"},0);
                        while (rs.next()) m.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)});
                        customersTable.setModel(m);
                    } catch (Exception ex) { ex.printStackTrace(); }
                }

                private void spotBookDialog() {
                    try (Connection conn = Database.connect()) {
                        DefaultComboBoxModel<String> custModel = new DefaultComboBoxModel<>();
                        try (PreparedStatement p = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) { try (ResultSet r = p.executeQuery()) { while (r.next()) custModel.addElement(r.getInt(1)+":"+r.getString(2)); } }

                        DefaultComboBoxModel<String> roomModel = new DefaultComboBoxModel<>();
                        try (PreparedStatement p = conn.prepareStatement("SELECT id, room_number, available, price FROM rooms ORDER BY room_number")) { try (ResultSet r = p.executeQuery()) { while (r.next()) roomModel.addElement(r.getInt(1)+":"+r.getString(2)+"|"+r.getBoolean(3)+"|"+r.getDouble(4)); } }

                        JComboBox<String> custCombo = new JComboBox<>(custModel);
                        JComboBox<String> roomCombo = new JComboBox<>(roomModel);
                        JPanel ui = new JPanel(new GridLayout(5,2,6,6)); ui.add(new JLabel("Customer:")); ui.add(custCombo); ui.add(new JLabel("Room:")); ui.add(roomCombo);
                        ui.add(new JLabel("Start (YYYY-MM-DD):")); JTextField s = new JTextField(); ui.add(s); ui.add(new JLabel("End (YYYY-MM-DD):")); JTextField e = new JTextField(); ui.add(e);
                        JCheckBox markPaid = new JCheckBox("Mark payment as PAID"); ui.add(markPaid); ui.add(new JLabel());

                        int ok = JOptionPane.showConfirmDialog(this, ui, "Spot Book", JOptionPane.OK_CANCEL_OPTION);
                        if (ok != JOptionPane.OK_OPTION) return;
                        String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem(); if (cs==null||rs==null) { JOptionPane.showMessageDialog(this, "Select entries"); return; }
                        int uid = Integer.parseInt(cs.split(":" )[0]); int rid = Integer.parseInt(rs.split(":" )[0]); java.sql.Date sd = java.sql.Date.valueOf(s.getText().trim()); java.sql.Date ed = java.sql.Date.valueOf(e.getText().trim());

                        try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, sd); pin.setDate(4, ed); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING"); pin.executeUpdate(); }
                        try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

                        loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking saved");
                    } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: "+ex.getMessage()); }
                }
            }

                int bookingId = -1; try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, java.sql.Date.valueOf(start)); pin.setDate(4, java.sql.Date.valueOf(end)); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING"); pin.executeUpdate(); ResultSet keys = pin.getGeneratedKeys(); if (keys.next()) bookingId = keys.getInt(1); }

                try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

                double total = price * nights; if (markPaid.isSelected()) { try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) { ps.setInt(1, uid); ps.setInt(2, bookingId); ps.setDouble(3, total); ps.setString(4, "PAID"); ps.setString(5, "CASH"); ps.setString(6, null); ps.setString(7, null); ps.setString(8, null); ps.executeUpdate(); } }

                JOptionPane.showMessageDialog(this, "Spot booking created (ID: " + bookingId + ")"); loadRooms(); loadBookings(); loadCustomers();
            } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: " + ex.getMessage()); }
        }
    }
    }

    void loadBookings() {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date, b.status, b.payment_status FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End","Status","PaymentStatus"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getString("room_number"), rs.getDate("start_date"), rs.getDate("end_date"), rs.getString("status"), rs.getString("payment_status")});
            }
            bookingsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading bookings: " + ex.getMessage());
        }
    }

    void loadPayments(JTable paymentsTable) {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT p.id, u.username, p.booking_id, p.amount, p.status, p.payment_method, p.googlepay_number, p.qr_code, p.created_at FROM payments p JOIN users u ON p.user_id=u.id")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","BookingID","Amount","Status","Method","GPNum","QR","CreatedAt"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getInt("booking_id"), rs.getDouble("amount"), rs.getString("status"), rs.getString("payment_method"), rs.getString("googlepay_number"), rs.getString("qr_code"), rs.getTimestamp("created_at")});
            }
            paymentsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading payments: " + ex.getMessage());
        }
    }

    void loadCustomers() {
        try (Connection conn = Database.connect()) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by_owner BOOLEAN NOT NULL DEFAULT FALSE");
            } catch (SQLException ignore) {}

            String q = "SELECT id, name, username, email, phone, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER'";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ResultSet rs = ps.executeQuery();
                DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","Username","Email","Phone","Type","SpotCreated"}, 0);
                while (rs.next()) {
                    boolean spot = rs.getBoolean("created_by_owner");
                    model.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("username"), rs.getString("email"), rs.getString("phone"), "CUSTOMER", spot});
                }
                customersTable.setModel(model);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading customers: " + ex.getMessage());
        }
    }

    void loadReviews(JTable reviewsTable) {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT r.id, u.username, rm.room_number, r.review_text, r.rating FROM reviews r JOIN users u ON r.user_id=u.id JOIN rooms rm ON r.room_id=rm.id")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","Room#","Review","Rating"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getString("room_number"), rs.getString("review_text"), rs.getInt("rating")});
            }
            reviewsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading reviews: " + ex.getMessage());
        }
    }

    void addRoomDialog() {
        JTextField roomNum = new JTextField();
        JTextField type = new JTextField();
        JTextField price = new JTextField();
        JPanel p = new JPanel(new GridLayout(3,2,6,6));
        p.add(new JLabel("Room Number:")); p.add(roomNum);
        p.add(new JLabel("Type:")); p.add(type);
        p.add(new JLabel("Price:")); p.add(price);
        int r = JOptionPane.showConfirmDialog(this, p, "Add Room", JOptionPane.OK_CANCEL_OPTION);
        if (r==JOptionPane.OK_OPTION) {
            String rn = roomNum.getText().trim();
            String tp = type.getText().trim();
            String pr = price.getText().trim();
            if (rn.isEmpty() || tp.isEmpty() || pr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields (room number, type, price).");
                return;
            }
            double pd;
            try {
                pd = Double.parseDouble(pr);
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Price must be a valid number.");
                return;
            }
            try (Connection conn = Database.connect();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO rooms(room_number, type, price, available) VALUES(?,?,?,?)")) {
                ps.setString(1, rn);
                ps.setString(2, tp);
                ps.setDouble(3, pd);
                ps.setBoolean(4, true);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Room added");
                loadRooms();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Add room error: "+ex.getMessage());
            }
        }
    }

    void updateRoomDialog() {
        int sel = roomsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a room to update"); return; }
        int roomId = (int) roomsTable.getModel().getValueAt(sel, 0);
        String curNumber = (String) roomsTable.getModel().getValueAt(sel, 1);
        String curType = (String) roomsTable.getModel().getValueAt(sel, 2);
        double curPrice = ((Number) roomsTable.getModel().getValueAt(sel, 3)).doubleValue();
        boolean curAvailable = (boolean) roomsTable.getModel().getValueAt(sel, 4);

        JTextField roomNum = new JTextField(curNumber);
        JTextField type = new JTextField(curType);
        JTextField price = new JTextField(String.valueOf(curPrice));
        JCheckBox available = new JCheckBox("Available", curAvailable);
        JPanel p = new JPanel(new GridLayout(4,2,6,6));
        p.add(new JLabel("Room Number:")); p.add(roomNum);
        p.add(new JLabel("Type:")); p.add(type);
        p.add(new JLabel("Price:")); p.add(price);
        p.add(new JLabel("Available:")); p.add(available);
        int r = JOptionPane.showConfirmDialog(this, p, "Update Room", JOptionPane.OK_CANCEL_OPTION);
        if (r==JOptionPane.OK_OPTION) {
            String rn = roomNum.getText().trim();
            String tp = type.getText().trim();
            String pr = price.getText().trim();
            if (rn.isEmpty() || tp.isEmpty() || pr.isEmpty()) { JOptionPane.showMessageDialog(this, "Please fill all fields"); return; }
            double pd;
            try { pd = Double.parseDouble(pr); } catch (NumberFormatException nfe) { JOptionPane.showMessageDialog(this, "Price must be a valid number."); return; }
            try (Connection conn = Database.connect();
                 PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET room_number=?, type=?, price=?, available=? WHERE id=?")) {
                ps.setString(1, rn);
                ps.setString(2, tp);
                ps.setDouble(3, pd);
                ps.setBoolean(4, available.isSelected());
                ps.setInt(5, roomId);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Room updated");
                loadRooms();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Update room error: " + ex.getMessage());
            }
        }
    }

    void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            DefaultComboBoxModel<String> customersModel = new DefaultComboBoxModel<>();
            try (PreparedStatement pc = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) {
                ResultSet rc = pc.executeQuery();
                while (rc.next()) customersModel.addElement(rc.getInt("id") + ":" + rc.getString("username"));
            }

            DefaultComboBoxModel<String> roomsModel = new DefaultComboBoxModel<>();
            try (PreparedStatement pr = conn.prepareStatement("SELECT id, room_number, available FROM rooms ORDER BY room_number")) {
                ResultSet rr = pr.executeQuery();
                while (rr.next()) {
                    String tag = rr.getBoolean("available") ? "(available)" : "(unavailable)";
                    roomsModel.addElement(rr.getInt("id") + ":" + rr.getString("room_number") + " " + tag);
                }
            }

            JPanel p = new JPanel(new GridLayout(5,3,6,6));
            JComboBox<String> customersCombo = new JComboBox<>(customersModel);
            JComboBox<String> roomsCombo = new JComboBox<>(roomsModel);
            JTextField startField = new JTextField();
            JTextField endField = new JTextField();
            JCheckBox markPaid = new JCheckBox("Mark payment as PAID");

            JButton newCustomerBtn = new JButton("New Customer");

            p.add(new JLabel("Customer:")); p.add(customersCombo); p.add(newCustomerBtn);
            p.add(new JLabel("Room:")); p.add(roomsCombo); p.add(new JLabel(""));
            p.add(new JLabel("Start Date (YYYY-MM-DD):")); p.add(startField); p.add(new JLabel(""));
            p.add(new JLabel("End Date (YYYY-MM-DD):")); p.add(endField); p.add(new JLabel(""));
            p.add(new JLabel("")); p.add(markPaid); p.add(new JLabel(""));

            newCustomerBtn.addActionListener(evt -> {
                JPanel cp = new JPanel(new GridLayout(5,2,6,6));
                JTextField nameField = new JTextField();
                JTextField unameField = new JTextField();
                JPasswordField pwdField = new JPasswordField();
                JTextField emailField = new JTextField();
                JTextField phoneField = new JTextField();
                cp.add(new JLabel("Name:")); cp.add(nameField);
                cp.add(new JLabel("Username:")); cp.add(unameField);
                cp.add(new JLabel("Password:")); cp.add(pwdField);
                cp.add(new JLabel("Email:")); cp.add(emailField);
                cp.add(new JLabel("Phone (10 digits):")); cp.add(phoneField);
                int cr = JOptionPane.showConfirmDialog(this, cp, "Create Customer", JOptionPane.OK_CANCEL_OPTION);
                if (cr != JOptionPane.OK_OPTION) return;
                String nm = nameField.getText().trim();
                String un = unameField.getText().trim();
                String pw = new String(pwdField.getPassword());
                String em = emailField.getText().trim();
                String ph = phoneField.getText().trim();
                if (nm.isEmpty() || un.isEmpty() || pw.isEmpty() || em.isEmpty() || ph.isEmpty()) { JOptionPane.showMessageDialog(this, "All fields required to create customer"); return; }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO users(name, username, password, email, phone, user_type, created_by_owner) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, nm); ins.setString(2, un); ins.setString(3, pw); ins.setString(4, em); ins.setString(5, ph); ins.setString(6, "CUSTOMER"); ins.setBoolean(7, true);
                    ins.executeUpdate();
                    ResultSet gk = ins.getGeneratedKeys();
                    if (gk.next()) {
                        customersModel.addElement(gk.getInt(1) + ":" + un);
                        customersCombo.setSelectedIndex(customersModel.getSize()-1);
                        loadCustomers();
                    }
                } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Create customer error: " + ex.getMessage()); }
            });

            int r = JOptionPane.showConfirmDialog(this, p, "Spot Book for Customer", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;

            String selCust = (String) customersCombo.getSelectedItem();
            String selRoom = (String) roomsCombo.getSelectedItem();
            if (selCust == null || selRoom == null) { JOptionPane.showMessageDialog(this, "Select a customer and room"); return; }
            int uid = Integer.parseInt(selCust.split(":" )[0]);
            int rid = Integer.parseInt(selRoom.split(":" )[0]);
            String start = startField.getText().trim();
            String end = endField.getText().trim();
            if (start.isEmpty() || end.isEmpty()) { JOptionPane.showMessageDialog(this, "Start and end dates required"); return; }

            try (PreparedStatement pu = conn.prepareStatement("SELECT user_type FROM users WHERE id=?")) {
                pu.setInt(1, uid);
                ResultSet ru = pu.executeQuery();
                if (!ru.next() || !"CUSTOMER".equalsIgnoreCase(ru.getString("user_type"))) {
                    JOptionPane.showMessageDialog(this, "Selected user is not a customer");
                    return;
                }
            }

            boolean available = false; double price = 0.0;
            try (PreparedStatement prs = conn.prepareStatement("SELECT available, price FROM rooms WHERE id=?")) {
                prs.setInt(1, rid);
                ResultSet rrs = prs.executeQuery();
                if (rrs.next()) { available = rrs.getBoolean("available"); price = rrs.getDouble("price"); }
                else { JOptionPane.showMessageDialog(this, "Room not found"); return; }
            }

            if (!available) {
                int confirm = JOptionPane.showConfirmDialog(this, "Room is currently unavailable. Force book anyway?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            java.time.LocalDate sDate = java.time.LocalDate.parse(start);
            java.time.LocalDate eDate = java.time.LocalDate.parse(end);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate);
            if (nights <= 0) { JOptionPane.showMessageDialog(this, "End date must be after start date"); return; }

            int bookingId = -1;
            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, java.sql.Date.valueOf(start)); pin.setDate(4, java.sql.Date.valueOf(end));
                pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected() ? "PAID" : "PENDING");
                pin.executeUpdate(); ResultSet keys = pin.getGeneratedKeys(); if (keys.next()) bookingId = keys.getInt(1);
            }

            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            double total = price * nights;
            if (markPaid.isSelected()) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, uid); ps.setInt(2, bookingId); ps.setDouble(3, total); ps.setString(4, "PAID"); ps.setString(5, "CASH"); ps.setString(6, null); ps.setString(7, null); ps.setString(8, null);
                    ps.executeUpdate();
                }
            }

            JOptionPane.showMessageDialog(this, "Spot booking created (ID: " + bookingId + ")");
            loadRooms(); loadBookings(); loadCustomers();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Spot booking error: " + ex.getMessage());
        }
    }
}
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class OwnerDashboard extends JFrame {
    int ownerId;
    JTable roomsTable;
    JTable bookingsTable;
    JTable customersTable;

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        setTitle("Owner Dashboard");
        setSize(900, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        JButton btnTopSpotBook = new JButton("Spot Book");
        top.add(back);
        top.add(btnTopSpotBook);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();

        JPanel rm = new JPanel(new BorderLayout());
        roomsTable = new JTable();
        rm.add(new JScrollPane(roomsTable), BorderLayout.CENTER);

        JPanel rmActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnAddRoom = new JButton("Add Room");
        JButton btnUpdateRoom = new JButton("Update Room");
        rmActions.add(btnAddRoom);
        rmActions.add(btnUpdateRoom);
        rm.add(rmActions, BorderLayout.SOUTH);

        tabs.add("Room Management", rm);

        JPanel bk = new JPanel(new BorderLayout());
        bookingsTable = new JTable();
        bk.add(new JScrollPane(bookingsTable), BorderLayout.CENTER);

        JPanel bkActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSpotBook = new JButton("Spot Book");
        bkActions.add(btnSpotBook);
        bk.add(bkActions, BorderLayout.SOUTH);

        tabs.add("All Bookings", bk);

        // View customers
        JPanel customersPanel = new JPanel(new BorderLayout());
        customersTable = new JTable();
        customersPanel.add(new JScrollPane(customersTable), BorderLayout.CENTER);
        tabs.add("View Customers", customersPanel);

        // View reviews
        JPanel reviewsPanel = new JPanel(new BorderLayout());
        JTable reviewsTable = new JTable();
        reviewsPanel.add(new JScrollPane(reviewsTable), BorderLayout.CENTER);
        tabs.add("View Reviews", reviewsPanel);

        // Payments tab
        JPanel paymentsPanel = new JPanel(new BorderLayout());
        JTable paymentsTable = new JTable();
        paymentsPanel.add(new JScrollPane(paymentsTable), BorderLayout.CENTER);
        tabs.add("Payments", paymentsPanel);

        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> {
            new LoginForm();
            dispose();
        });

        loadRooms();
        loadBookings();
        loadCustomers();
        loadReviews(reviewsTable);
        loadPayments(paymentsTable);

        btnAddRoom.addActionListener(e -> addRoomDialog());
        btnUpdateRoom.addActionListener(e -> updateRoomDialog());
        btnSpotBook.addActionListener(e -> spotBookDialog());
        btnTopSpotBook.addActionListener(e -> spotBookDialog());

        setVisible(true);
    }

    void loadRooms() {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, room_number, type, price, available FROM rooms")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Room#","Type","Price","Available"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price"), rs.getBoolean("available")});
            }
            roomsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading rooms: " + ex.getMessage());
        }
    }

    void loadBookings() {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date, b.status, b.payment_status FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","Room#","Start","End","Status","PaymentStatus"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getString("room_number"), rs.getDate("start_date"), rs.getDate("end_date"), rs.getString("status"), rs.getString("payment_status")});
            }
            bookingsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading bookings: " + ex.getMessage());
        }
    }

    void loadPayments(JTable paymentsTable) {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT p.id, u.username, p.booking_id, p.amount, p.status, p.payment_method, p.googlepay_number, p.qr_code, p.created_at FROM payments p JOIN users u ON p.user_id=u.id")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","BookingID","Amount","Status","Method","GPNum","QR","CreatedAt"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getInt("booking_id"), rs.getDouble("amount"), rs.getString("status"), rs.getString("payment_method"), rs.getString("googlepay_number"), rs.getString("qr_code"), rs.getTimestamp("created_at")});
            }
            paymentsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading payments: " + ex.getMessage());
        }
    }

    void loadCustomers() {
        try (Connection conn = Database.connect()) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by_owner BOOLEAN NOT NULL DEFAULT FALSE");
            } catch (SQLException ignore) {}

            String q = "SELECT id, name, username, email, phone, COALESCE(created_by_owner, FALSE) AS created_by_owner FROM users WHERE user_type='CUSTOMER'";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ResultSet rs = ps.executeQuery();
                DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","Username","Email","Phone","Type","SpotCreated"}, 0);
                while (rs.next()) {
                    boolean spot = rs.getBoolean("created_by_owner");
                    model.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("username"), rs.getString("email"), rs.getString("phone"), "CUSTOMER", spot});
                }
                customersTable.setModel(model);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading customers: " + ex.getMessage());
        }
    }

    void loadReviews(JTable reviewsTable) {
        try (Connection conn = Database.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT r.id, u.username, rm.room_number, r.review_text, r.rating FROM reviews r JOIN users u ON r.user_id=u.id JOIN rooms rm ON r.room_id=rm.id")) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","Room#","Review","Rating"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getString("room_number"), rs.getString("review_text"), rs.getInt("rating")});
            }
            reviewsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading reviews: " + ex.getMessage());
        }
    }

    void addRoomDialog() {
        JTextField roomNum = new JTextField();
        JTextField type = new JTextField();
        JTextField price = new JTextField();
        JPanel p = new JPanel(new GridLayout(3,2,6,6));
        p.add(new JLabel("Room Number:")); p.add(roomNum);
        p.add(new JLabel("Type:")); p.add(type);
        p.add(new JLabel("Price:")); p.add(price);
        int r = JOptionPane.showConfirmDialog(this, p, "Add Room", JOptionPane.OK_CANCEL_OPTION);
        if (r==JOptionPane.OK_OPTION) {
            String rn = roomNum.getText().trim();
            String tp = type.getText().trim();
            String pr = price.getText().trim();
            if (rn.isEmpty() || tp.isEmpty() || pr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields (room number, type, price).");
                return;
            }
            double pd;
            try {
                pd = Double.parseDouble(pr);
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Price must be a valid number.");
                return;
            }
            try (Connection conn = Database.connect();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO rooms(room_number, type, price, available) VALUES(?,?,?,?)")) {
                ps.setString(1, rn);
                ps.setString(2, tp);
                ps.setDouble(3, pd);
                ps.setBoolean(4, true);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Room added");
                loadRooms();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Add room error: "+ex.getMessage());
            }
        }
    }

    void updateRoomDialog() {
        int sel = roomsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a room to update"); return; }
        int roomId = (int) roomsTable.getModel().getValueAt(sel, 0);
        String curNumber = (String) roomsTable.getModel().getValueAt(sel, 1);
        String curType = (String) roomsTable.getModel().getValueAt(sel, 2);
        double curPrice = ((Number) roomsTable.getModel().getValueAt(sel, 3)).doubleValue();
        boolean curAvailable = (boolean) roomsTable.getModel().getValueAt(sel, 4);

        JTextField roomNum = new JTextField(curNumber);
        JTextField type = new JTextField(curType);
        JTextField price = new JTextField(String.valueOf(curPrice));
        JCheckBox available = new JCheckBox("Available", curAvailable);
        JPanel p = new JPanel(new GridLayout(4,2,6,6));
        p.add(new JLabel("Room Number:")); p.add(roomNum);
        p.add(new JLabel("Type:")); p.add(type);
        p.add(new JLabel("Price:")); p.add(price);
        p.add(new JLabel("Available:")); p.add(available);
        int r = JOptionPane.showConfirmDialog(this, p, "Update Room", JOptionPane.OK_CANCEL_OPTION);
        if (r==JOptionPane.OK_OPTION) {
            String rn = roomNum.getText().trim();
            String tp = type.getText().trim();
            String pr = price.getText().trim();
            if (rn.isEmpty() || tp.isEmpty() || pr.isEmpty()) { JOptionPane.showMessageDialog(this, "Please fill all fields"); return; }
            double pd;
            try { pd = Double.parseDouble(pr); } catch (NumberFormatException nfe) { JOptionPane.showMessageDialog(this, "Price must be a valid number."); return; }
            try (Connection conn = Database.connect();
                 PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET room_number=?, type=?, price=?, available=? WHERE id=?")) {
                ps.setString(1, rn);
                ps.setString(2, tp);
                ps.setDouble(3, pd);
                ps.setBoolean(4, available.isSelected());
                ps.setInt(5, roomId);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Room updated");
                loadRooms();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Update room error: " + ex.getMessage());
            }
        }
    }

    void spotBookDialog() {
        try (Connection conn = Database.connect()) {
            DefaultComboBoxModel<String> customersModel = new DefaultComboBoxModel<>();
            try (PreparedStatement pc = conn.prepareStatement("SELECT id, username FROM users WHERE user_type='CUSTOMER' ORDER BY username")) {
                ResultSet rc = pc.executeQuery();
                while (rc.next()) customersModel.addElement(rc.getInt("id") + ":" + rc.getString("username"));
            }

            DefaultComboBoxModel<String> roomsModel = new DefaultComboBoxModel<>();
            try (PreparedStatement pr = conn.prepareStatement("SELECT id, room_number, available FROM rooms ORDER BY room_number")) {
                ResultSet rr = pr.executeQuery();
                while (rr.next()) {
                    String tag = rr.getBoolean("available") ? "(available)" : "(unavailable)";
                    roomsModel.addElement(rr.getInt("id") + ":" + rr.getString("room_number") + " " + tag);
                }
            }

            JPanel p = new JPanel(new GridLayout(5,3,6,6));
            JComboBox<String> customersCombo = new JComboBox<>(customersModel);
            JComboBox<String> roomsCombo = new JComboBox<>(roomsModel);
            JTextField startField = new JTextField();
            JTextField endField = new JTextField();
            JCheckBox markPaid = new JCheckBox("Mark payment as PAID");

            JButton newCustomerBtn = new JButton("New Customer");

            p.add(new JLabel("Customer:")); p.add(customersCombo); p.add(newCustomerBtn);
            p.add(new JLabel("Room:")); p.add(roomsCombo); p.add(new JLabel(""));
            p.add(new JLabel("Start Date (YYYY-MM-DD):")); p.add(startField); p.add(new JLabel(""));
            p.add(new JLabel("End Date (YYYY-MM-DD):")); p.add(endField); p.add(new JLabel(""));
            p.add(new JLabel("")); p.add(markPaid); p.add(new JLabel(""));

            newCustomerBtn.addActionListener(evt -> {
                JPanel cp = new JPanel(new GridLayout(5,2,6,6));
                JTextField nameField = new JTextField();
                JTextField unameField = new JTextField();
                JPasswordField pwdField = new JPasswordField();
                JTextField emailField = new JTextField();
                JTextField phoneField = new JTextField();
                cp.add(new JLabel("Name:")); cp.add(nameField);
                cp.add(new JLabel("Username:")); cp.add(unameField);
                cp.add(new JLabel("Password:")); cp.add(pwdField);
                cp.add(new JLabel("Email:")); cp.add(emailField);
                cp.add(new JLabel("Phone (10 digits):")); cp.add(phoneField);
                int cr = JOptionPane.showConfirmDialog(this, cp, "Create Customer", JOptionPane.OK_CANCEL_OPTION);
                if (cr != JOptionPane.OK_OPTION) return;
                String nm = nameField.getText().trim();
                String un = unameField.getText().trim();
                String pw = new String(pwdField.getPassword());
                String em = emailField.getText().trim();
                String ph = phoneField.getText().trim();
                if (nm.isEmpty() || un.isEmpty() || pw.isEmpty() || em.isEmpty() || ph.isEmpty()) { JOptionPane.showMessageDialog(this, "All fields required to create customer"); return; }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO users(name, username, password, email, phone, user_type, created_by_owner) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, nm); ins.setString(2, un); ins.setString(3, pw); ins.setString(4, em); ins.setString(5, ph); ins.setString(6, "CUSTOMER"); ins.setBoolean(7, true);
                    ins.executeUpdate();
                    ResultSet gk = ins.getGeneratedKeys();
                    if (gk.next()) {
                        customersModel.addElement(gk.getInt(1) + ":" + un);
                        customersCombo.setSelectedIndex(customersModel.getSize()-1);
                        loadCustomers();
                    }
                } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Create customer error: " + ex.getMessage()); }
            });

            int r = JOptionPane.showConfirmDialog(this, p, "Spot Book for Customer", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;

            String selCust = (String) customersCombo.getSelectedItem();
            String selRoom = (String) roomsCombo.getSelectedItem();
            if (selCust == null || selRoom == null) { JOptionPane.showMessageDialog(this, "Select a customer and room"); return; }
            int uid = Integer.parseInt(selCust.split(":" )[0]);
            int rid = Integer.parseInt(selRoom.split(":" )[0]);
            String start = startField.getText().trim();
            String end = endField.getText().trim();
            if (start.isEmpty() || end.isEmpty()) { JOptionPane.showMessageDialog(this, "Start and end dates required"); return; }

            try (PreparedStatement pu = conn.prepareStatement("SELECT user_type FROM users WHERE id=?")) {
                pu.setInt(1, uid);
                ResultSet ru = pu.executeQuery();
                if (!ru.next() || !"CUSTOMER".equalsIgnoreCase(ru.getString("user_type"))) {
                    JOptionPane.showMessageDialog(this, "Selected user is not a customer");
                    return;
                }
            }

            boolean available = false; double price = 0.0;
            try (PreparedStatement prs = conn.prepareStatement("SELECT available, price FROM rooms WHERE id=?")) {
                prs.setInt(1, rid);
                ResultSet rrs = prs.executeQuery();
                if (rrs.next()) { available = rrs.getBoolean("available"); price = rrs.getDouble("price"); }
                else { JOptionPane.showMessageDialog(this, "Room not found"); return; }
            }

            if (!available) {
                int confirm = JOptionPane.showConfirmDialog(this, "Room is currently unavailable. Force book anyway?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            java.time.LocalDate sDate = java.time.LocalDate.parse(start);
            java.time.LocalDate eDate = java.time.LocalDate.parse(end);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate);
            if (nights <= 0) { JOptionPane.showMessageDialog(this, "End date must be after start date"); return; }

            int bookingId = -1;
            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, java.sql.Date.valueOf(start)); pin.setDate(4, java.sql.Date.valueOf(end));
                pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected() ? "PAID" : "PENDING");
                pin.executeUpdate(); ResultSet keys = pin.getGeneratedKeys(); if (keys.next()) bookingId = keys.getInt(1);
            }

            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            double total = price * nights;
            if (markPaid.isSelected()) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, uid); ps.setInt(2, bookingId); ps.setDouble(3, total); ps.setString(4, "PAID"); ps.setString(5, "CASH"); ps.setString(6, null); ps.setString(7, null); ps.setString(8, null);
                    ps.executeUpdate();
                }
            }

            JOptionPane.showMessageDialog(this, "Spot booking created (ID: " + bookingId + ")");
            loadRooms(); loadBookings(); loadCustomers();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Spot booking error: " + ex.getMessage());
        }
    }
}
                    ins.setString(2, un);
                    ins.setString(3, pw);
                    ins.setString(4, em);
                    ins.setString(5, ph);
                    ins.setString(6, "CUSTOMER");
                    ins.setBoolean(7, true);
                    ins.executeUpdate();
                    ResultSet gk = ins.getGeneratedKeys();
                    if (gk.next()) {
                        uid = gk.getInt(1);
                    }
                }

                // refresh customers view
                loadCustomers();
            }
            // check room availability
            boolean available = false;
            double price = 0.0;
            try (PreparedStatement prs = conn.prepareStatement("SELECT available, price FROM rooms WHERE id=?")) {
                prs.setInt(1, rid);
                ResultSet rrs = prs.executeQuery();
                if (rrs.next()) { available = rrs.getBoolean("available"); price = rrs.getDouble("price"); }
                else { JOptionPane.showMessageDialog(this, "Room not found"); return; }
            }

            if (!available) {
                int confirm = JOptionPane.showConfirmDialog(this, "Room is currently unavailable. Force book anyway?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            java.time.LocalDate sDate = java.time.LocalDate.parse(start);
            java.time.LocalDate eDate = java.time.LocalDate.parse(end);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate);
            if (nights <= 0) { JOptionPane.showMessageDialog(this, "End date must be after start date"); return; }

            // insert booking
            int bookingId = -1;
            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                pin.setInt(1, uid);
                pin.setInt(2, rid);
                pin.setDate(3, java.sql.Date.valueOf(start));
                pin.setDate(4, java.sql.Date.valueOf(end));
                pin.setString(5, "BOOKED");
                pin.setString(6, markPaid.isSelected() ? "PAID" : "PENDING");
                pin.executeUpdate();
                ResultSet keys = pin.getGeneratedKeys(); if (keys.next()) bookingId = keys.getInt(1);
            }

            // mark room unavailable
            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            double total = price * nights;
            if (markPaid.isSelected()) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, uid);
                    ps.setInt(2, bookingId);
                    ps.setDouble(3, total);
                    ps.setString(4, "PAID");
                    ps.setString(5, "CASH");
                    ps.setString(6, null);
                    ps.setString(7, null);
                    ps.setString(8, null);
                    ps.executeUpdate();
                }
            }

            JOptionPane.showMessageDialog(this, "Spot booking created (ID: " + bookingId + ")");
            loadRooms(); loadBookings();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Spot booking error: " + ex.getMessage());
        }
    }
}
