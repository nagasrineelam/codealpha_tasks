import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Hotel Reservation System
 * -----------------------------------------------------
 * Console-based, OOP-driven Java application to search,
 * book, and manage hotel rooms. Supports room categories,
 * reservation creation/cancellation, simulated payments,
 * and file-based persistence of rooms + reservations.
 */
public class HotelReservationSystem {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    // ====================================================
    //  ROOM CATEGORY
    // ====================================================
    enum RoomCategory {
        STANDARD(99.00),
        DELUXE(159.00),
        SUITE(279.00);

        final double basePricePerNight;

        RoomCategory(double basePricePerNight) {
            this.basePricePerNight = basePricePerNight;
        }
    }

    // ====================================================
    //  ROOM
    // ====================================================
    static class Room {
        private final String roomNumber;
        private final RoomCategory category;
        private final double pricePerNight;

        Room(String roomNumber, RoomCategory category) {
            this.roomNumber = roomNumber;
            this.category = category;
            this.pricePerNight = category.basePricePerNight;
        }

        String getRoomNumber() { return roomNumber; }
        RoomCategory getCategory() { return category; }
        double getPricePerNight() { return pricePerNight; }
    }

    // ====================================================
    //  GUEST
    // ====================================================
    static class Guest {
        private final String name;
        private final String contact;

        Guest(String name, String contact) {
            this.name = name;
            this.contact = contact;
        }

        String getName() { return name; }
        String getContact() { return contact; }
    }

    // ====================================================
    //  PAYMENT  -  simulated payment processing
    // ====================================================
    static class Payment {
        enum Status { PAID, FAILED, REFUNDED }

        private final String method;
        private final double amount;
        private Status status;

        Payment(String method, double amount) {
            this.method = method;
            this.amount = amount;
            this.status = Status.FAILED;
        }

        // Simulated processing: succeeds unless amount is invalid.
        // A 5% random failure rate mimics a real declined-card scenario.
        boolean process(Random rng) {
            if (amount <= 0) {
                status = Status.FAILED;
                return false;
            }
            if (rng.nextInt(100) < 5) {
                status = Status.FAILED;
                return false;
            }
            status = Status.PAID;
            return true;
        }

        void refund() { status = Status.REFUNDED; }

        String getMethod() { return method; }
        double getAmount() { return amount; }
        Status getStatus() { return status; }
    }

    // ====================================================
    //  RESERVATION
    // ====================================================
    static class Reservation {
        enum Status { CONFIRMED, CANCELLED, CHECKED_OUT }

        private final String id;
        private final Guest guest;
        private final Room room;
        private final LocalDate checkIn;
        private final LocalDate checkOut;
        private final double totalCost;
        private Payment payment;
        private Status status;

        Reservation(String id, Guest guest, Room room, LocalDate checkIn, LocalDate checkOut, Payment payment) {
            this.id = id;
            this.guest = guest;
            this.room = room;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.payment = payment;
            this.totalCost = payment.getAmount();
            this.status = Status.CONFIRMED;
        }

        long getNights() { return ChronoUnit.DAYS.between(checkIn, checkOut); }

        boolean overlaps(LocalDate otherIn, LocalDate otherOut) {
            return status == Status.CONFIRMED && checkIn.isBefore(otherOut) && otherIn.isBefore(checkOut);
        }

        String getId() { return id; }
        Guest getGuest() { return guest; }
        Room getRoom() { return room; }
        LocalDate getCheckIn() { return checkIn; }
        LocalDate getCheckOut() { return checkOut; }
        double getTotalCost() { return totalCost; }
        Payment getPayment() { return payment; }
        Status getStatus() { return status; }
        void cancel() { status = Status.CANCELLED; payment.refund(); }

        void printDetails() {
            System.out.println("\n----------------- Reservation " + id + " -----------------");
            System.out.println("Guest        : " + guest.getName() + " (" + guest.getContact() + ")");
            System.out.println("Room         : " + room.getRoomNumber() + " [" + room.getCategory() + "]");
            System.out.println("Check-in     : " + checkIn);
            System.out.println("Check-out    : " + checkOut);
            System.out.println("Nights       : " + getNights());
            System.out.printf("Rate/night   : $%.2f%n", room.getPricePerNight());
            System.out.printf("Total cost   : $%.2f%n", totalCost);
            System.out.println("Payment      : " + payment.getMethod() + " - " + payment.getStatus());
            System.out.println("Status       : " + status);
            System.out.println("-----------------------------------------------------");
        }

        String toFileLine() {
            return id + "|" + guest.getName() + "|" + guest.getContact() + "|" + room.getRoomNumber() + "|"
                    + room.getCategory() + "|" + checkIn + "|" + checkOut + "|" + totalCost + "|"
                    + payment.getMethod() + "|" + payment.getStatus() + "|" + status;
        }
    }

    // ====================================================
    //  HOTEL  -  owns rooms + reservations, core business logic
    // ====================================================
    static class Hotel {
        private final List<Room> rooms = new ArrayList<>();
        private final Map<String, Reservation> reservations = new LinkedHashMap<>();
        private final Random rng = new Random();
        private int reservationCounter = 1;

        void addRoom(Room r) { rooms.add(r); }

        List<Room> getAllRooms() { return rooms; }

        Room findRoom(String roomNumber) {
            for (Room r : rooms) {
                if (r.getRoomNumber().equalsIgnoreCase(roomNumber)) return r;
            }
            return null;
        }

        List<Room> searchAvailableRooms(RoomCategory category, LocalDate checkIn, LocalDate checkOut) {
            List<Room> available = new ArrayList<>();
            for (Room r : rooms) {
                if (category != null && r.getCategory() != category) continue;
                if (isRoomAvailable(r, checkIn, checkOut)) available.add(r);
            }
            return available;
        }

        boolean isRoomAvailable(Room room, LocalDate checkIn, LocalDate checkOut) {
            for (Reservation res : reservations.values()) {
                if (res.getRoom().getRoomNumber().equals(room.getRoomNumber())
                        && res.overlaps(checkIn, checkOut)) {
                    return false;
                }
            }
            return true;
        }

        // Returns the new Reservation, or null if room unavailable / payment failed
        Reservation bookRoom(Guest guest, Room room, LocalDate checkIn, LocalDate checkOut, String paymentMethod) {
            if (!isRoomAvailable(room, checkIn, checkOut)) return null;

            long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
            if (nights <= 0) return null;

            double total = nights * room.getPricePerNight();
            Payment payment = new Payment(paymentMethod, total);
            boolean paid = payment.process(rng);
            if (!paid) return null;

            String id = "RES" + String.format("%04d", reservationCounter++);
            Reservation res = new Reservation(id, guest, room, checkIn, checkOut, payment);
            reservations.put(id, res);
            return res;
        }

        boolean cancelReservation(String id) {
            Reservation res = reservations.get(id);
            if (res == null || res.getStatus() != Reservation.Status.CONFIRMED) return false;
            res.cancel();
            return true;
        }

        Reservation getReservation(String id) { return reservations.get(id); }

        Collection<Reservation> getAllReservations() { return reservations.values(); }

        void setReservationCounter(int next) { this.reservationCounter = next; }
        int getReservationCounter() { return reservationCounter; }

        void restoreReservation(Reservation res) { reservations.put(res.getId(), res); }
    }

    // ====================================================
    //  FILE PERSISTENCE
    // ====================================================
    static final String ROOMS_FILE = "rooms.csv";
    static final String RESERVATIONS_FILE = "reservations.csv";

    static void saveData(Hotel hotel) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ROOMS_FILE))) {
            for (Room r : hotel.getAllRooms()) {
                pw.println(r.getRoomNumber() + "," + r.getCategory());
            }
        } catch (IOException e) {
            System.out.println("Warning: could not save rooms (" + e.getMessage() + ")");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(RESERVATIONS_FILE))) {
            for (Reservation res : hotel.getAllReservations()) {
                pw.println(res.toFileLine());
            }
        } catch (IOException e) {
            System.out.println("Warning: could not save reservations (" + e.getMessage() + ")");
        }
    }

    static void loadReservations(Hotel hotel) {
        File f = new File(RESERVATIONS_FILE);
        if (!f.exists()) return;

        int maxCounter = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split("\\|");
                String id = p[0];
                String guestName = p[1];
                String guestContact = p[2];
                String roomNumber = p[3];
                LocalDate checkIn = LocalDate.parse(p[5], DATE_FMT);
                LocalDate checkOut = LocalDate.parse(p[6], DATE_FMT);
                double totalCost = Double.parseDouble(p[7]);
                String paymentMethod = p[8];
                Payment.Status paymentStatus = Payment.Status.valueOf(p[9]);
                Reservation.Status resStatus = Reservation.Status.valueOf(p[10]);

                Room room = hotel.findRoom(roomNumber);
                if (room == null) continue;

                Guest guest = new Guest(guestName, guestContact);
                Payment payment = new Payment(paymentMethod, totalCost);
                if (paymentStatus == Payment.Status.PAID) payment.process(new Random() {
                    @Override public int nextInt(int bound) { return 99; } // force success on reload
                });
                if (paymentStatus == Payment.Status.REFUNDED) payment.refund();

                Reservation res = new Reservation(id, guest, room, checkIn, checkOut, payment);
                if (resStatus == Reservation.Status.CANCELLED) res.cancel();
                hotel.restoreReservation(res);

                int num = Integer.parseInt(id.replace("RES", ""));
                if (num > maxCounter) maxCounter = num;
            }
            hotel.setReservationCounter(maxCounter + 1);
            System.out.println("Loaded saved reservations from " + RESERVATIONS_FILE + ".");
        } catch (Exception e) {
            System.out.println("Could not fully read saved reservations (" + e.getMessage() + ")");
        }
    }

    // ====================================================
    //  APPLICATION / MENU
    // ====================================================
    private static Hotel hotel = new Hotel();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        setupRooms();
        loadReservations(hotel);

        System.out.println("=================================================");
        System.out.println("           HOTEL RESERVATION SYSTEM");
        System.out.println("=================================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1: viewAllRooms(); break;
                case 2: searchAvailableRooms(); break;
                case 3: bookRoom(); break;
                case 4: cancelReservation(); break;
                case 5: viewReservationDetails(); break;
                case 6: viewAllReservations(); break;
                case 7:
                    saveData(hotel);
                    System.out.println("\nData saved to " + ROOMS_FILE + " and " + RESERVATIONS_FILE + ".");
                    break;
                case 8:
                    saveData(hotel);
                    running = false;
                    System.out.println("\nData saved. Goodbye!");
                    break;
                default:
                    System.out.println("\nInvalid choice. Please select a number from 1 to 8.");
            }
        }
        scanner.close();
    }

    private static void setupRooms() {
        // Standard rooms
        hotel.addRoom(new Room("101", RoomCategory.STANDARD));
        hotel.addRoom(new Room("102", RoomCategory.STANDARD));
        hotel.addRoom(new Room("103", RoomCategory.STANDARD));
        hotel.addRoom(new Room("104", RoomCategory.STANDARD));
        // Deluxe rooms
        hotel.addRoom(new Room("201", RoomCategory.DELUXE));
        hotel.addRoom(new Room("202", RoomCategory.DELUXE));
        hotel.addRoom(new Room("203", RoomCategory.DELUXE));
        // Suites
        hotel.addRoom(new Room("301", RoomCategory.SUITE));
        hotel.addRoom(new Room("302", RoomCategory.SUITE));
    }

    private static void printMenu() {
        System.out.println("\n-------------------- MENU --------------------");
        System.out.println("1. View all rooms");
        System.out.println("2. Search available rooms");
        System.out.println("3. Book a room");
        System.out.println("4. Cancel a reservation");
        System.out.println("5. View reservation details");
        System.out.println("6. View all reservations");
        System.out.println("7. Save data to file");
        System.out.println("8. Save & exit");
        System.out.println("-----------------------------------------------");
    }

    // ---------- Actions ----------

    private static void viewAllRooms() {
        System.out.println("\n================= ALL ROOMS =================");
        System.out.printf("%-10s %-12s %-10s%n", "Room #", "Category", "Price/Night");
        System.out.println("-----------------------------------------------");
        for (Room r : hotel.getAllRooms()) {
            System.out.printf("%-10s %-12s $%-9.2f%n", r.getRoomNumber(), r.getCategory(), r.getPricePerNight());
        }
        System.out.println("===============================================");
    }

    private static void searchAvailableRooms() {
        RoomCategory category = promptForCategoryOptional();
        LocalDate checkIn = readDate("Check-in date (yyyy-MM-dd): ");
        LocalDate checkOut = readDate("Check-out date (yyyy-MM-dd): ");
        if (checkOut.isBefore(checkIn) || checkOut.isEqual(checkIn)) {
            System.out.println("Check-out date must be after check-in date.");
            return;
        }

        List<Room> available = hotel.searchAvailableRooms(category, checkIn, checkOut);
        System.out.println("\n========== AVAILABLE ROOMS (" + checkIn + " to " + checkOut + ") ==========");
        if (available.isEmpty()) {
            System.out.println("No rooms available for those dates" + (category != null ? " in category " + category : "") + ".");
        } else {
            System.out.printf("%-10s %-12s %-10s%n", "Room #", "Category", "Price/Night");
            for (Room r : available) {
                System.out.printf("%-10s %-12s $%-9.2f%n", r.getRoomNumber(), r.getCategory(), r.getPricePerNight());
            }
        }
        System.out.println("===========================================================");
    }

    private static void bookRoom() {
        LocalDate checkIn = readDate("Check-in date (yyyy-MM-dd): ");
        LocalDate checkOut = readDate("Check-out date (yyyy-MM-dd): ");
        if (checkOut.isBefore(checkIn) || checkOut.isEqual(checkIn)) {
            System.out.println("Check-out date must be after check-in date.");
            return;
        }

        System.out.print("Room number to book (use option 2 to search first): ");
        String roomNumber = scanner.nextLine().trim();
        Room room = hotel.findRoom(roomNumber);
        if (room == null) {
            System.out.println("No such room.");
            return;
        }
        if (!hotel.isRoomAvailable(room, checkIn, checkOut)) {
            System.out.println("Room " + roomNumber + " is not available for those dates.");
            return;
        }

        System.out.print("Guest name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Guest contact (email/phone): ");
        String contact = scanner.nextLine().trim();
        System.out.print("Payment method (Card/UPI/Cash): ");
        String method = scanner.nextLine().trim();
        if (method.isEmpty()) method = "Card";

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        double total = nights * room.getPricePerNight();
        System.out.printf("Total for %d night(s) @ $%.2f/night = $%.2f%n", nights, room.getPricePerNight(), total);
        System.out.println("Processing payment...");

        Guest guest = new Guest(name, contact);
        Reservation res = hotel.bookRoom(guest, room, checkIn, checkOut, method);

        if (res == null) {
            System.out.println("Booking failed — either the room became unavailable or the payment was declined. Please try again.");
        } else {
            System.out.println("Payment successful! Reservation confirmed.");
            res.printDetails();
        }
    }

    private static void cancelReservation() {
        System.out.print("\nEnter reservation ID to cancel (e.g. RES0001): ");
        String id = scanner.nextLine().trim().toUpperCase();
        Reservation res = hotel.getReservation(id);

        if (res == null) {
            System.out.println("No reservation found with that ID.");
            return;
        }
        if (res.getStatus() != Reservation.Status.CONFIRMED) {
            System.out.println("This reservation is already " + res.getStatus() + " and cannot be cancelled.");
            return;
        }

        boolean cancelled = hotel.cancelReservation(id);
        if (cancelled) {
            System.out.printf("Reservation %s cancelled. $%.2f will be refunded via %s.%n",
                    id, res.getTotalCost(), res.getPayment().getMethod());
        } else {
            System.out.println("Could not cancel reservation.");
        }
    }

    private static void viewReservationDetails() {
        System.out.print("\nEnter reservation ID (e.g. RES0001): ");
        String id = scanner.nextLine().trim().toUpperCase();
        Reservation res = hotel.getReservation(id);
        if (res == null) {
            System.out.println("No reservation found with that ID.");
            return;
        }
        res.printDetails();
    }

    private static void viewAllReservations() {
        Collection<Reservation> all = hotel.getAllReservations();
        System.out.println("\n================= ALL RESERVATIONS =================");
        if (all.isEmpty()) {
            System.out.println("No reservations yet.");
        } else {
            System.out.printf("%-9s %-16s %-8s %-10s %-12s %-12s %-10s %-10s%n",
                    "ID", "Guest", "Room", "Category", "Check-in", "Check-out", "Total", "Status");
            System.out.println("---------------------------------------------------------------------------------------");
            for (Reservation r : all) {
                System.out.printf("%-9s %-16s %-8s %-10s %-12s %-12s $%-9.2f %-10s%n",
                        r.getId(), r.getGuest().getName(), r.getRoom().getRoomNumber(),
                        r.getRoom().getCategory(), r.getCheckIn(), r.getCheckOut(),
                        r.getTotalCost(), r.getStatus());
            }
        }
        System.out.println("=========================================================================================");
    }

    // ---------- Input helpers ----------

    private static RoomCategory promptForCategoryOptional() {
        System.out.println("Filter by category? 1=Standard 2=Deluxe 3=Suite 0=Any");
        int choice = readInt("Choice: ");
        switch (choice) {
            case 1: return RoomCategory.STANDARD;
            case 2: return RoomCategory.DELUXE;
            case 3: return RoomCategory.SUITE;
            default: return null;
        }
    }

    private static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return LocalDate.parse(input, DATE_FMT);
            } catch (Exception e) {
                System.out.println("Please enter a valid date in yyyy-MM-dd format (e.g. 2026-07-15).");
            }
        }
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
}
