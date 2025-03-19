@echo off
echo Generation du keystore SSL...
keytool -genkey -keyalg RSA -alias serverkey -keystore keystore.jks -storepass changeit -keypass changeit -validity 365 -keysize 2048 -dname "CN=localhost, OU=RemoteControl, O=YourCompany, L=YourCity, ST=YourState, C=FR"
echo Keystore genere avec succes !
pause 