package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class OwnerDashboardClean extends JFrame {
    private final int ownerId;
    private final JTable roomsTable = new JTable();
    private final JTable bookingsTable = new JTable();
    private final JTable customersTable = new JTable();

    public OwnerDashboardClean(int ownerId) {
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
    // Rooms panel with actions
    JPanel roomsPanel = new JPanel(new BorderLayout());
    roomsPanel.add(new JScrollPane(roomsTable), BorderLayout.CENTER);
    JPanel roomsActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton addRoomBtn = new JButton("Add Room");
    JButton updateRoomBtn = new JButton("Update Room");
    roomsActions.add(addRoomBtn); roomsActions.add(updateRoomBtn);
    roomsPanel.add(roomsActions, BorderLayout.SOUTH);

    // Reviews panel
    JTable reviewsTable = new JTable();
    JPanel reviewsPanel = new JPanel(new BorderLayout());
    reviewsPanel.add(new JScrollPane(reviewsTable), BorderLayout.CENTER);
    // reviewsPanel is read-only for owners (owners can view reviews only)
    reviewsPanel.add(new JPanel(), BorderLayout.SOUTH);

    // Payments panel
    JTable paymentsTable = new JTable();
    JPanel paymentsPanel = new JPanel(new BorderLayout());
    paymentsPanel.add(new JScrollPane(paymentsTable), BorderLayout.CENTER);
    JPanel payActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton addPaymentBtn = new JButton("Add Payment");
    JButton markPaidBtn = new JButton("Mark Booking Paid");
    JButton refreshPaymentsBtn = new JButton("Refresh");
    payActions.add(refreshPaymentsBtn); payActions.add(addPaymentBtn); payActions.add(markPaidBtn);
    paymentsPanel.add(payActions, BorderLayout.SOUTH);

    tabs.add("Rooms", roomsPanel);
    // Bookings panel with actions
    JPanel bookingsPanel = new JPanel(new BorderLayout());
    bookingsPanel.add(new JScrollPane(bookingsTable), BorderLayout.CENTER);
    JPanel bookingsActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton viewBookingBtn = new JButton("View Booking");
    JButton markBookingPaidBtn = new JButton("Mark Paid");
    bookingsActions.add(viewBookingBtn); bookingsActions.add(markBookingPaidBtn);
    bookingsPanel.add(bookingsActions, BorderLayout.SOUTH);
    tabs.add("Bookings", bookingsPanel);
    tabs.add("Customers", new JScrollPane(customersTable));
    tabs.add("Reviews", reviewsPanel);
    tabs.add("Payments", paymentsPanel);
        add(tabs, BorderLayout.CENTER);

        back.addActionListener(e -> { new LoginForm(); dispose(); });
        spot.addActionListener(e -> spotBookDialog());

    loadRooms(); loadBookings(); loadCustomers(); loadReviews(reviewsTable);
    loadPayments(paymentsTable);
    // wire room actions
    addRoomBtn.addActionListener(e -> addRoomDialog());
    updateRoomBtn.addActionListener(e -> updateRoomDialog());
    addPaymentBtn.addActionListener(e -> addPaymentDialog());
    markPaidBtn.addActionListener(e -> markBookingPaid(paymentsTable));
    refreshPaymentsBtn.addActionListener(e -> loadPayments(paymentsTable));
    // owner cannot add reviews here; review creation is customer-only
    viewBookingBtn.addActionListener(e -> viewBookingDialog());
    markBookingPaidBtn.addActionListener(e -> markSelectedBookingPaid());
        setVisible(true);
    }

    private void addReviewDialog(JTable reviewsTable) {
        JPanel p = new JPanel(new GridLayout(4,2,6,6));
        JTextField userField = new JTextField();
        JTextField roomField = new JTextField();
        JTextField ratingField = new JTextField();
        JTextArea reviewArea = new JTextArea(4,20);
        p.add(new JLabel("User ID:")); p.add(userField);
        p.add(new JLabel("Room ID:")); p.add(roomField);
        p.add(new JLabel("Rating (1-5):")); p.add(ratingField);
        p.add(new JLabel("Review:")); p.add(new JScrollPane(reviewArea));
        int r = JOptionPane.showConfirmDialog(this, p, "Add Review", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            int uid = Integer.parseInt(userField.getText().trim());
            int rid = Integer.parseInt(roomField.getText().trim());
            int rating = 5; try { rating = Integer.parseInt(ratingField.getText().trim()); if (rating<1||rating>5) rating=5; } catch (Exception ignore) {}
            String review = reviewArea.getText();
            try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement("INSERT INTO reviews(user_id, room_id, review_text, rating, created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)")) {
                ps.setInt(1, uid); ps.setInt(2, rid); ps.setString(3, review); ps.setInt(4, rating); ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Review added");
                loadReviews(reviewsTable);
            }
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Add review error: " + ex.getMessage()); }
    }

    private void loadReviews(JTable reviewsTable) {
        // Try to load reviews, accounting for different possible column names for text (review_text or comment)
        String qWithReviewText = "SELECT r.id, u.name AS user_name, rm.room_number, r.rating, r.review_text AS comment_text, r.created_at FROM reviews r JOIN users u ON r.user_id=u.id JOIN rooms rm ON r.room_id=rm.id ORDER BY r.created_at DESC";
        String qWithComment = "SELECT r.id, u.name AS user_name, rm.room_number, r.rating, r.comment AS comment_text, r.created_at FROM reviews r JOIN users u ON r.user_id=u.id JOIN rooms rm ON r.room_id=rm.id ORDER BY r.created_at DESC";
        String qNoText = "SELECT r.id, u.name AS user_name, rm.room_number, r.rating, r.created_at FROM reviews r JOIN users u ON r.user_id=u.id JOIN rooms rm ON r.room_id=rm.id ORDER BY r.created_at DESC";
        try (Connection conn = Database.connect(); Statement st = conn.createStatement()) {
            // prefer review_text
            try (ResultSet rs = st.executeQuery(qWithReviewText)) {
                DefaultTableModel model = new DefaultTableModel(new Object[]{"ID","User","Room","Rating","Comment","Created At"}, 0);
                while (rs.next()) {
                    model.addRow(new Object[]{rs.getInt("id"), rs.getString("user_name"), rs.getString("room_number"), rs.getInt("rating"), rs.getString("comment_text"), rs.getTimestamp("created_at")});
                }
                reviewsTable.setModel(model);
                return;
            } catch (SQLException ignore1) {
                // try comment column
                try (ResultSet rs2 = st.executeQuery(qWithComment)) {
                    DefaultTableModel model2 = new DefaultTableModel(new Object[]{"ID","User","Room","Rating","Comment","Created At"}, 0);
                    while (rs2.next()) {
                        model2.addRow(new Object[]{rs2.getInt("id"), rs2.getString("user_name"), rs2.getString("room_number"), rs2.getInt("rating"), rs2.getString("comment_text"), rs2.getTimestamp("created_at")});
                    }
                    reviewsTable.setModel(model2);
                    return;
                } catch (SQLException ignore2) {
                    // try without any text column
                    try (ResultSet rs3 = st.executeQuery(qNoText)) {
                        DefaultTableModel model3 = new DefaultTableModel(new Object[]{"ID","User","Room","Rating","Created At"}, 0);
                        while (rs3.next()) {
                            model3.addRow(new Object[]{rs3.getInt("id"), rs3.getString("user_name"), rs3.getString("room_number"), rs3.getInt("rating"), rs3.getTimestamp("created_at")});
                        }
                        reviewsTable.setModel(model3);
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // If we reach here, set empty model
        reviewsTable.setModel(new DefaultTableModel(new Object[]{"ID","User","Room","Rating","Comment","Created At"}, 0));
    }

    private void addRoomDialog() {
        JTextField numberField = new JTextField();
        JTextField typeField = new JTextField();
        JTextField priceField = new JTextField();
        Object[] form = {"Room Number:", numberField, "Type:", typeField, "Price:", priceField};
        int ok = JOptionPane.showConfirmDialog(this, form, "Add Room", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            String number = numberField.getText().trim();
            String type = typeField.getText().trim();
            String priceS = priceField.getText().trim();
            if (number.isEmpty() || type.isEmpty() || priceS.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields required", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                double price = Double.parseDouble(priceS);
                try (Connection conn = Database.connect();
                     PreparedStatement ps = conn.prepareStatement("INSERT INTO rooms(room_number, type, price, available) VALUES(?,?,?,1)")) {
                    ps.setString(1, number);
                    ps.setString(2, type);
                    ps.setDouble(3, price);
                    ps.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Room added");
                    loadRooms();
                }
            } catch (NumberFormatException nf) {
                JOptionPane.showMessageDialog(this, "Price must be numeric", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "DB error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateRoomDialog() {
        int sel = roomsTable.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(this, "Select a room first"); return; }
        DefaultTableModel m = (DefaultTableModel) roomsTable.getModel();
        Object idObj = m.getValueAt(sel, 0);
        if (idObj == null) { JOptionPane.showMessageDialog(this, "Invalid selection"); return; }
        int id = Integer.parseInt(idObj.toString());
        String currentNumber = m.getValueAt(sel, 1).toString();
        String currentType = m.getValueAt(sel, 2).toString();
        String currentPrice = m.getValueAt(sel, 3).toString();

        JTextField numberField = new JTextField(currentNumber);
        JTextField typeField = new JTextField(currentType);
        JTextField priceField = new JTextField(currentPrice);
        JCheckBox availableBox = new JCheckBox("Available", Boolean.parseBoolean(m.getValueAt(sel,4).toString()));
        Object[] form = {"Room Number:", numberField, "Type:", typeField, "Price:", priceField, "", availableBox};
        int ok = JOptionPane.showConfirmDialog(this, form, "Update Room", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            try {
                double price = Double.parseDouble(priceField.getText().trim());
                boolean avail = availableBox.isSelected();
                try (Connection conn = Database.connect();
                     PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET room_number=?, type=?, price=?, available=? WHERE id=?")) {
                    ps.setString(1, numberField.getText().trim());
                    ps.setString(2, typeField.getText().trim());
                    ps.setDouble(3, price);
                    ps.setBoolean(4, avail);
                    ps.setInt(5, id);
                    ps.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Room updated");
                    loadRooms();
                }
            } catch (NumberFormatException nf) {
                JOptionPane.showMessageDialog(this, "Price must be numeric", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "DB error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
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
            JButton newCustomerBtn = new JButton("New Customer");
            JComboBox<String> roomCombo = new JComboBox<>(roomModel);
            JPanel ui = new JPanel(new GridLayout(5,3,6,6)); ui.add(new JLabel("Customer:")); ui.add(custCombo); ui.add(newCustomerBtn); ui.add(new JLabel("Room:")); ui.add(roomCombo); ui.add(new JLabel());
            ui.add(new JLabel("Start (YYYY-MM-DD):")); JTextField s = new JTextField(); ui.add(s); ui.add(new JLabel("End (YYYY-MM-DD):")); JTextField e = new JTextField(); ui.add(e);
            JCheckBox markPaid = new JCheckBox("Mark payment as PAID"); ui.add(markPaid); ui.add(new JLabel());

            // new customer flow: show a small form and insert into users with created_by_owner = TRUE
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
                cp.add(new JLabel("Phone:")); cp.add(phoneField);
                int cr = JOptionPane.showConfirmDialog(this, cp, "Create Customer", JOptionPane.OK_CANCEL_OPTION);
                if (cr != JOptionPane.OK_OPTION) return;
                String nm = nameField.getText().trim();
                String un = unameField.getText().trim();
                String pw = new String(pwdField.getPassword());
                String em = emailField.getText().trim();
                String ph = phoneField.getText().trim();
                if (nm.isEmpty() || un.isEmpty() || pw.isEmpty()) { JOptionPane.showMessageDialog(this, "Name, username and password required"); return; }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO users(name, username, password, email, phone, user_type, created_by_owner) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, nm); ins.setString(2, un); ins.setString(3, pw); ins.setString(4, em); ins.setString(5, ph); ins.setString(6, "CUSTOMER"); ins.setBoolean(7, true);
                    ins.executeUpdate(); try (ResultSet gk = ins.getGeneratedKeys()) { if (gk.next()) { int newId = gk.getInt(1); custModel.addElement(newId + ":" + un); custCombo.setSelectedIndex(custModel.getSize()-1); loadCustomers(); } }
                } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Create customer error: " + ex.getMessage()); }
            });

            int ok = JOptionPane.showConfirmDialog(this, ui, "Spot Book", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            String cs = (String) custCombo.getSelectedItem(); String rs = (String) roomCombo.getSelectedItem(); if (cs==null||rs==null) { JOptionPane.showMessageDialog(this, "Select entries"); return; }
            int uid = Integer.parseInt(cs.split(":" )[0]); int rid = Integer.parseInt(rs.split(":" )[0]); java.sql.Date sd = java.sql.Date.valueOf(s.getText().trim()); java.sql.Date ed = java.sql.Date.valueOf(e.getText().trim());

            try (PreparedStatement pin = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { pin.setInt(1, uid); pin.setInt(2, rid); pin.setDate(3, sd); pin.setDate(4, ed); pin.setString(5, "BOOKED"); pin.setString(6, markPaid.isSelected()?"PAID":"PENDING"); pin.executeUpdate(); }
            try (PreparedStatement pup = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) { pup.setInt(1, rid); pup.executeUpdate(); }

            loadRooms(); loadBookings(); loadCustomers(); JOptionPane.showMessageDialog(this, "Spot booking saved");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Spot booking error: "+ex.getMessage()); }
    }

    // Payments support for owner
    private void loadPayments(JTable paymentsTable) {
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID","User","BookingID","Amount","Status","Method","GPay","QR","Created At"}, 0);
        try (Connection conn = Database.connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT p.id, u.username, p.booking_id, p.amount, p.status, p.payment_method, p.googlepay_number, p.qr_code, p.created_at FROM payments p JOIN users u ON p.user_id=u.id ORDER BY p.created_at DESC")) {
            while (rs.next()) model.addRow(new Object[]{rs.getInt("id"), rs.getString("username"), rs.getInt("booking_id"), rs.getDouble("amount"), rs.getString("status"), rs.getString("payment_method"), rs.getString("googlepay_number"), rs.getString("qr_code"), rs.getTimestamp("created_at")});
            paymentsTable.setModel(model);
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading payments: " + ex.getMessage()); }
    }

    private void addPaymentDialog() {
        JPanel p = new JPanel(new GridLayout(4,2,6,6));
        JTextField uidField = new JTextField();
        JTextField bookingField = new JTextField();
        JTextField amountField = new JTextField();
        JComboBox<String> pm = new JComboBox<>(new String[]{"CASH","GOOGLEPAY"});
        p.add(new JLabel("User ID:")); p.add(uidField);
        p.add(new JLabel("Booking ID:")); p.add(bookingField);
        p.add(new JLabel("Amount:")); p.add(amountField);
        p.add(new JLabel("Method:")); p.add(pm);
        int r = JOptionPane.showConfirmDialog(this, p, "Add Payment", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) {
            int uid = Integer.parseInt(uidField.getText().trim());
            int bid = Integer.parseInt(bookingField.getText().trim());
            double amt = Double.parseDouble(amountField.getText().trim());
            String method = pm.getSelectedItem().toString();
            ps.setInt(1, uid); ps.setInt(2, bid); ps.setDouble(3, amt); ps.setString(4, "PAID"); ps.setString(5, method); ps.setString(6, method.equals("GOOGLEPAY")?null:null); ps.setString(7, null); ps.setString(8, null);
            ps.executeUpdate();
            try (PreparedStatement p2 = conn.prepareStatement("UPDATE bookings SET payment_status='PAID' WHERE id=?")) { p2.setInt(1, bid); p2.executeUpdate(); }
            JOptionPane.showMessageDialog(this, "Payment recorded");
            // refresh payments table if visible
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Add payment error: " + ex.getMessage()); }
    }

    private void markBookingPaid(JTable paymentsTable) {
        int sel = paymentsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a payment row (or use Add Payment to record) or select booking in Bookings tab."); return; }
        DefaultTableModel m = (DefaultTableModel) paymentsTable.getModel();
        Object bidObj = m.getValueAt(sel, 2);
        if (bidObj == null) { JOptionPane.showMessageDialog(this, "Invalid booking id"); return; }
        int bid = Integer.parseInt(bidObj.toString());
        try (Connection conn = Database.connect(); PreparedStatement p = conn.prepareStatement("UPDATE bookings SET payment_status='PAID' WHERE id=?")) { p.setInt(1, bid); p.executeUpdate(); JOptionPane.showMessageDialog(this, "Booking marked PAID"); } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Mark paid error: " + ex.getMessage()); }
        loadPayments(paymentsTable);
    }

    private void viewBookingDialog() {
        int sel = bookingsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a booking to view"); return; }
        DefaultTableModel m = (DefaultTableModel) bookingsTable.getModel();
        Object idObj = m.getValueAt(sel, 0);
        if (idObj == null) { JOptionPane.showMessageDialog(this, "Invalid selection"); return; }
        int bid = Integer.parseInt(idObj.toString());
        try (Connection conn = Database.connect(); PreparedStatement ps = conn.prepareStatement("SELECT b.id, u.username, r.room_number, b.start_date, b.end_date, b.status, b.payment_status FROM bookings b JOIN users u ON b.user_id=u.id JOIN rooms r ON b.room_id=r.id WHERE b.id=?")) {
            ps.setInt(1, bid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Booking ID: ").append(rs.getInt("id")).append('\n');
                    sb.append("User: ").append(rs.getString("username")).append('\n');
                    sb.append("Room: ").append(rs.getString("room_number")).append('\n');
                    sb.append("Start: ").append(rs.getDate("start_date")).append('\n');
                    sb.append("End: ").append(rs.getDate("end_date")).append('\n');
                    sb.append("Status: ").append(rs.getString("status")).append('\n');
                    sb.append("Payment: ").append(rs.getString("payment_status")).append('\n');
                    JOptionPane.showMessageDialog(this, sb.toString(), "Booking Details", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading booking: " + ex.getMessage()); }
    }

    

    private void markSelectedBookingPaid() {
        int sel = bookingsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a booking to mark paid"); return; }
        DefaultTableModel m = (DefaultTableModel) bookingsTable.getModel();
        Object idObj = m.getValueAt(sel, 0); if (idObj==null) { JOptionPane.showMessageDialog(this, "Invalid selection"); return; }
        int bid = Integer.parseInt(idObj.toString());
        try (Connection conn = Database.connect(); PreparedStatement p = conn.prepareStatement("UPDATE bookings SET payment_status='PAID' WHERE id=?")) { p.setInt(1, bid); p.executeUpdate(); JOptionPane.showMessageDialog(this, "Booking marked PAID"); } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Mark paid error: " + ex.getMessage()); }
        loadBookings();
    }
}
