package ui;

import db.Database;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class CustomerDashboard extends JFrame {
    int userId;
    JTable roomsTable;
    JTable bookingsTable;

    public CustomerDashboard(int userId) {
        this.userId = userId;
        setTitle("Customer Dashboard");
        setSize(800, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // Top: Back button
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        top.add(back);
        add(top, BorderLayout.NORTH);

        // Center: Tabs with room list and bookings
        JTabbedPane tabs = new JTabbedPane();

        // Rooms tab
        JPanel roomsPanel = new JPanel(new BorderLayout(6,6));
        roomsTable = new JTable();
        roomsPanel.add(new JScrollPane(roomsTable), BorderLayout.CENTER);

    JPanel roomsActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton btnBook = new JButton("Book Room");
    roomsActions.add(btnBook);
        roomsPanel.add(roomsActions, BorderLayout.SOUTH);

        tabs.add("Available Rooms", roomsPanel);

        // Bookings tab
    JPanel bookingsPanel = new JPanel(new BorderLayout());
    bookingsTable = new JTable();
    bookingsPanel.add(new JScrollPane(bookingsTable), BorderLayout.CENTER);

    JPanel bookingsActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton btnPayBooking = new JButton("Make Payment");
    JButton btnReviewBooking = new JButton("Add Review");
    JButton btnCancelBooking = new JButton("Cancel Booking");
    bookingsActions.add(btnReviewBooking); bookingsActions.add(btnPayBooking);
    bookingsActions.add(btnCancelBooking);
    bookingsPanel.add(bookingsActions, BorderLayout.SOUTH);

    tabs.add("My Bookings", bookingsPanel);

        add(tabs, BorderLayout.CENTER);

        // Load data
        loadRooms();
    loadBookings();

        back.addActionListener(e -> {
            new LoginForm();
            dispose();
        });

    btnBook.addActionListener(e -> doBook());
    btnReviewBooking.addActionListener(e -> doReviewFromBooking());
    btnPayBooking.addActionListener(e -> doPayment());
    btnCancelBooking.addActionListener(e -> cancelBooking());

        setVisible(true);
    }

    void cancelBooking() {
        int sel = bookingsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a booking to cancel"); return; }
        int bookingId = (int) bookingsTable.getModel().getValueAt(sel, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to cancel booking ID " + bookingId + "?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection conn = Database.connect()) {
            // find room id for booking
            int roomId = -1;
            try (PreparedStatement p = conn.prepareStatement("SELECT room_id FROM bookings WHERE id=?")) {
                p.setInt(1, bookingId);
                ResultSet r = p.executeQuery();
                if (r.next()) roomId = r.getInt("room_id");
            }

            // mark booking cancelled and free up room
            try (PreparedStatement p = conn.prepareStatement("UPDATE bookings SET status=?, payment_status=? WHERE id=?")) {
                p.setString(1, "CANCELLED");
                p.setString(2, "CANCELLED");
                p.setInt(3, bookingId);
                p.executeUpdate();
            }

            if (roomId != -1) {
                try (PreparedStatement p2 = conn.prepareStatement("UPDATE rooms SET available=TRUE WHERE id=?")) {
                    p2.setInt(1, roomId);
                    p2.executeUpdate();
                }
            }

            JOptionPane.showMessageDialog(this, "Booking cancelled and room made available");
            loadBookings();
            loadRooms();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Cancel booking error: " + ex.getMessage());
        }
    }

    void loadRooms() {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT id, room_number, type, price FROM rooms WHERE available=TRUE")) {
            ResultSet rs = ps.executeQuery();
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Room#","Type","Price"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price")});
            }
            roomsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading rooms: " + ex.getMessage());
        }
    }

    void loadBookings() {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT b.id, r.room_number, b.start_date, b.end_date, b.status, b.payment_status FROM bookings b JOIN rooms r ON b.room_id=r.id WHERE b.user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Room#","Start","End","Status","PaymentStatus"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getInt("id"), rs.getString("room_number"), rs.getDate("start_date"), rs.getDate("end_date"), rs.getString("status"), rs.getString("payment_status")});
            }
            bookingsTable.setModel(model);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading bookings: " + ex.getMessage());
        }
    }

    void doBook() {
        int sel = roomsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a room to book"); return; }
        int roomId = (int) roomsTable.getModel().getValueAt(sel, 0);
        String start = JOptionPane.showInputDialog(this, "Enter start date (YYYY-MM-DD)");
        if (start == null) return;
        String end = JOptionPane.showInputDialog(this, "Enter end date (YYYY-MM-DD)");
        if (end == null) return;
        try (Connection conn = Database.connect()) {
            // get price of room
            double price = 0.0;
            try (PreparedStatement p2 = conn.prepareStatement("SELECT price FROM rooms WHERE id=?")) {
                p2.setInt(1, roomId);
                ResultSet r2 = p2.executeQuery();
                if (r2.next()) price = r2.getDouble("price");
            }

            // compute nights
            java.time.LocalDate s = java.time.LocalDate.parse(start);
            java.time.LocalDate e = java.time.LocalDate.parse(end);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(s, e);
            if (nights <= 0) { JOptionPane.showMessageDialog(this, "End date must be after start date"); return; }

            // insert booking and get generated id
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO bookings(user_id, room_id, start_date, end_date, status, payment_status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setInt(2, roomId);
                ps.setDate(3, Date.valueOf(start));
                ps.setDate(4, Date.valueOf(end));
                ps.setString(5, "BOOKED");
                ps.setString(6, "PENDING");
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                int bookingId = -1;
                if (keys.next()) bookingId = keys.getInt(1);

                // mark room unavailable
                try (PreparedStatement p3 = conn.prepareStatement("UPDATE rooms SET available=FALSE WHERE id=?")) {
                    p3.setInt(1, roomId);
                    p3.executeUpdate();
                }

                // compute total = price * nights
                double total = price * nights;

                JOptionPane.showMessageDialog(this, "Room booked. Opening payment dialog.");
                loadRooms();
                loadBookings();

                // auto open payment dialog with amount and booking id
                openAutoPaymentDialog(bookingId, total);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Booking error: " + ex.getMessage());
        }
    }

    void openAutoPaymentDialog(int bookingId, double total) {
    JPanel p = new JPanel(new GridLayout(3,2,6,6));
        p.add(new JLabel("Booking ID:")); p.add(new JLabel(String.valueOf(bookingId)));
        p.add(new JLabel("Total amount:")); p.add(new JLabel(String.format("%.2f", total)));
        p.add(new JLabel("Payment Method:"));
    JComboBox<String> pm = new JComboBox<>(new String[]{"CASH","GOOGLEPAY"});
        p.add(pm);
    p.add(new JLabel("GooglePay Number:"));
    JTextField gfn = new JTextField();
    p.add(gfn);
        // show dialog with payment options and preview
        while (true) {
            int r = JOptionPane.showConfirmDialog(this, p, "Make Payment", JOptionPane.OK_CANCEL_OPTION);
            if (r!=JOptionPane.OK_OPTION) break;
            String method = pm.getSelectedItem().toString();
            String gpn = gfn.getText();

            // no QR option anymore

            // insert payment
            try (Connection conn = Database.connect();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, userId);
                ps.setInt(2, bookingId);
                ps.setDouble(3, total);
                ps.setString(4, "PAID");
                ps.setString(5, method);
                ps.setString(6, method.equals("GOOGLEPAY") ? gpn : null);
                ps.setString(7, method.equals("QR") ? gpn : null);
                ps.setString(8, null);
                ps.executeUpdate();

                // update booking payment_status
                try (PreparedStatement p2 = conn.prepareStatement("UPDATE bookings SET payment_status='PAID' WHERE id=?")) { p2.setInt(1, bookingId); p2.executeUpdate(); }

                JOptionPane.showMessageDialog(this, "Payment successful");
                loadBookings();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Payment error: " + ex.getMessage());
            }

            break; // done
        }
    }

    void doReview() {
        int sel = roomsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a room to review"); return; }
        int roomId = (int) roomsTable.getModel().getValueAt(sel, 0);
        JPanel p = new JPanel(new GridLayout(2,2,6,6));
        JTextField ratingField = new JTextField();
        JTextArea reviewArea = new JTextArea(4,20);
        p.add(new JLabel("Rating (1-5):")); p.add(ratingField);
        p.add(new JLabel("Review:")); p.add(new JScrollPane(reviewArea));
        int r = JOptionPane.showConfirmDialog(this, p, "Write Review", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        String review = reviewArea.getText();
        if (review == null || review.trim().isEmpty()) return;
        int rating = 5;
        try { rating = Integer.parseInt(ratingField.getText().trim()); if (rating<1 || rating>5) rating=5; } catch (Exception ex) { rating=5; }
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO reviews(user_id, room_id, review_text, rating) VALUES(?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, roomId);
            ps.setString(3, review);
            ps.setInt(4, rating);
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Review submitted");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Review error: " + ex.getMessage());
        }
    }

    void doReviewFromBooking() {
        int sel = bookingsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a booking to review"); return; }
        int bookingId = (int) bookingsTable.getModel().getValueAt(sel, 0);
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT room_id FROM bookings WHERE id=?")) {
            ps.setInt(1, bookingId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int roomId = rs.getInt("room_id");
                // reuse doReview flow but with known roomId
                JPanel p = new JPanel(new GridLayout(2,2,6,6));
                JTextField ratingField = new JTextField();
                JTextArea reviewArea = new JTextArea(4,20);
                p.add(new JLabel("Rating (1-5):")); p.add(ratingField);
                p.add(new JLabel("Review:")); p.add(new JScrollPane(reviewArea));
                int r = JOptionPane.showConfirmDialog(this, p, "Write Review", JOptionPane.OK_CANCEL_OPTION);
                if (r != JOptionPane.OK_OPTION) return;
                String review = reviewArea.getText();
                if (review == null || review.trim().isEmpty()) return;
                int rating = 5;
                try { rating = Integer.parseInt(ratingField.getText().trim()); if (rating<1 || rating>5) rating=5; } catch (Exception ex) { rating=5; }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO reviews(user_id, room_id, review_text, rating) VALUES(?,?,?,?)")) {
                    ins.setInt(1, userId);
                    ins.setInt(2, roomId);
                    ins.setString(3, review);
                    ins.setInt(4, rating);
                    ins.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Review submitted");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Review error: " + ex.getMessage());
        }
    }

    void doPayment() {
        int sel = bookingsTable.getSelectedRow();
        if (sel == -1) { JOptionPane.showMessageDialog(this, "Select a booking to pay"); return; }
        int bookingId = (int) bookingsTable.getModel().getValueAt(sel, 0);
        String paymentStatus = null;
        try {
            Object psObj = bookingsTable.getModel().getValueAt(sel, 5); // PaymentStatus column
            if (psObj != null) paymentStatus = psObj.toString();
        } catch (Exception ignore) {
            // fallback to DB check below
        }

        try (Connection conn = Database.connect()) {
            if (paymentStatus == null) {
                try (PreparedStatement pchk = conn.prepareStatement("SELECT payment_status FROM bookings WHERE id=?")) {
                    pchk.setInt(1, bookingId);
                    ResultSet rchk = pchk.executeQuery();
                    if (rchk.next()) paymentStatus = rchk.getString("payment_status");
                }
            }

            if ("PAID".equalsIgnoreCase(paymentStatus)) {
                JOptionPane.showMessageDialog(this, "This booking is already paid.");
                return;
            }

            // fetch booking details and room price
            double price = 0.0;
            java.sql.Date startDate = null, endDate = null;
            int roomId = -1;
            try (PreparedStatement p = conn.prepareStatement("SELECT b.room_id, b.start_date, b.end_date, r.price FROM bookings b JOIN rooms r ON b.room_id=r.id WHERE b.id=?")) {
                p.setInt(1, bookingId);
                ResultSet rs = p.executeQuery();
                if (rs.next()) {
                    roomId = rs.getInt("room_id");
                    startDate = rs.getDate("check_in_date");
                    endDate = rs.getDate("check_out_date");
                    price = rs.getDouble("price");
                } else {
                    JOptionPane.showMessageDialog(this, "Booking not found");
                    return;
                }
            }

            // compute nights
            java.time.LocalDate s = startDate.toLocalDate();
            java.time.LocalDate e = endDate.toLocalDate();
            long nights = java.time.temporal.ChronoUnit.DAYS.between(s, e);
            if (nights <= 0) nights = 1;
            double total = price * nights;

            // payment dialog
            JPanel p = new JPanel(new GridLayout(3,2,6,6));
            p.add(new JLabel("Booking ID:")); p.add(new JLabel(String.valueOf(bookingId)));
            p.add(new JLabel("Total amount:")); p.add(new JLabel(String.format("%.2f", total)));
            p.add(new JLabel("Payment Method:"));
            JComboBox<String> pm = new JComboBox<>(new String[]{"CASH","GOOGLEPAY"});
            p.add(pm);

            int res = JOptionPane.showConfirmDialog(this, p, "Pay Pending Booking", JOptionPane.OK_CANCEL_OPTION);
            if (res != JOptionPane.OK_OPTION) return;

            String method = pm.getSelectedItem().toString();
            String gpn = null;
            if ("GOOGLEPAY".equals(method)) {
                gpn = JOptionPane.showInputDialog(this, "Enter GooglePay number:");
                if (gpn == null || gpn.trim().isEmpty()) { JOptionPane.showMessageDialog(this, "GooglePay number required"); return; }
            }

            // insert payment
            try (PreparedStatement pins = conn.prepareStatement("INSERT INTO payments(user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin) VALUES(?,?,?,?,?,?,?,?)")) {
                pins.setInt(1, userId);
                pins.setInt(2, bookingId);
                pins.setDouble(3, total);
                pins.setString(4, "PAID");
                pins.setString(5, method);
                pins.setString(6, gpn);
                pins.setString(7, null);
                pins.setString(8, null);
                pins.executeUpdate();
            }

            try (PreparedStatement pup = conn.prepareStatement("UPDATE bookings SET payment_status='PAID' WHERE id=?")) {
                pup.setInt(1, bookingId);
                pup.executeUpdate();
            }

            JOptionPane.showMessageDialog(this, "Payment successful");
            loadBookings();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Payment error: " + ex.getMessage());
        }
    }
}
