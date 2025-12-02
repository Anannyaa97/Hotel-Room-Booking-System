@echo off
cd /d "C:\Users\user\Hotel_management (Another)\Hotel_management-main\HotelBookingSystem\src"
javac -d "..\out" -cp ".;lib\\mysql-connector-j-9.4.0.jar" Main.java ui\\*.java db\\*.java model\\*.java
echo javac exit code %ERRORLEVEL%
