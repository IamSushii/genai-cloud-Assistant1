GlowUp AI Assistant ✨
An enterprise-grade Java cloud assistant featuring a multi-turn conversational AI interface powered by the Google Gemini API, backed by MySQL for persistent chat history, and served via a lightweight Java HTTP server.

🚀 Tech Stack & Architecture
Backend: Java 17, com.sun.net.httpserver, Google Gson

Database: MySQL (mysql-connector-j) with persistent session tracking

AI Model: Google Gemini (gemini-3.5-flash) with dynamic multi-turn context injection

Frontend: HTML5, JavaScript, Tailwind CSS

⚙️ Environment Variables
To run this application locally, you must configure the following environment variables:

GEMINI_API_KEY: Your Google Gemini API developer key

DB_URL: jdbc:mysql://localhost:3306/genai_assistant

DB_USER: Your MySQL database username (e.g., root)

DB_PASSWORD: Your MySQL database password

📦 How to Build & Run
1. Build the Fat JAR using Maven
Open your terminal in the project root directory and run a clean package build to bundle all external dependencies (Gson and MySQL connector):

PowerShell
mvn clean package
2. Set Environment Variables & Start the Server
PowerShell (Windows):

PowerShell
$env:GEMINI_API_KEY="your_actual_api_key"
$env:DB_URL="jdbc:mysql://localhost:3306/genai_assistant"
$env:DB_USER="root"
$env:DB_PASSWORD="your_mysql_password"

java -jar target/genai-cloud-assistant-1.0-SNAPSHOT.jar
3. Launch the Frontend
Open index.html using the Live Server extension in VS Code (running on port 5500) to interact with your application UI.