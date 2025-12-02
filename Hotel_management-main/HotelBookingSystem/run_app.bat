@echo off
cd /d "C:\Users\user\Hotel_management (Another)\Hotel_management-main\HotelBookingSystem"
java -cp "out;src\lib\mysql-connector-j-9.4.0.jar" Main
echo java exit code %ERRORLEVEL%
